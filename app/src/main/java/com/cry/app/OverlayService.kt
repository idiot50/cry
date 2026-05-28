package com.cry.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.cry.app.data.PairsRepository
import com.cry.app.data.TickerData
import com.cry.app.ui.formatPrice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

class OverlayService : Service() {

    private val client = BinanceClient()
    private lateinit var repo: PairsRepository
    private lateinit var windowManager: WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var streamJob: Job? = null
    private var overlayView: View? = null
    private val tickers = mutableMapOf<String, TickerData>()
    private var currentSymbols: List<String> = emptyList()
    private var streamError: String? = null

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PREFS_KEY) {
            restartStream()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        repo = PairsRepository(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopOverlay()
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, buildNotification())
        showOverlay()
        restartStream()
        _isRunning.value = true
        return START_STICKY
    }

    private fun showOverlay() {
        if (overlayView != null) return
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_view, null)
        overlayView = view

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 32
            y = 240
        }

        attachDragHandler(view, params)
        runCatching { windowManager.addView(view, params) }
        updateOverlayRows()
    }

    private fun attachDragHandler(view: View, params: WindowManager.LayoutParams) {
        var initX = 0
        var initY = 0
        var touchX = 0f
        var touchY = 0f
        var downTime = 0L
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x
                    initY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    downTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initX + (event.rawX - touchX).toInt()
                    params.y = initY + (event.rawY - touchY).toInt()
                    runCatching { windowManager.updateViewLayout(view, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = abs(event.rawX - touchX) > 12 || abs(event.rawY - touchY) > 12
                    val held = System.currentTimeMillis() - downTime > 500
                    when {
                        !moved && held -> stopOverlay()
                        !moved -> openMainActivity()
                    }
                    v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun openMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        runCatching { startActivity(intent) }
    }

    private fun stopOverlay() {
        streamJob?.cancel()
        streamJob = null
        runCatching { overlayView?.let { windowManager.removeView(it) } }
        overlayView = null
        _isRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun restartStream() {
        val symbols = repo.load().take(3)
        if (symbols == currentSymbols && streamJob?.isActive == true) return
        currentSymbols = symbols
        streamJob?.cancel()
        tickers.keys.toList().forEach { if (it !in symbols) tickers.remove(it) }
        streamError = null
        updateOverlayRows()
        if (symbols.isEmpty()) return

        streamJob = scope.launch {
            while (isActive) {
                try {
                    client.stream(symbols).collect { update ->
                        if (streamError != null) streamError = null
                        val prev = tickers[update.symbol]
                        val direction = when {
                            prev == null -> 0
                            update.price > prev.price -> 1
                            update.price < prev.price -> -1
                            else -> prev.direction
                        }
                        tickers[update.symbol] = TickerData(
                            symbol = update.symbol,
                            price = update.price,
                            priceChangePercent = update.priceChangePercent,
                            direction = direction,
                        )
                        updateOverlayRows()
                    }
                    streamError = "disconnected"
                    updateOverlayRows()
                } catch (e: Exception) {
                    streamError = e.message?.take(40) ?: "connection failed"
                    updateOverlayRows()
                }
                if (!isActive) break
                delay(2000)
            }
        }
    }

    private fun updateOverlayRows() {
        val view = overlayView ?: return
        val rowIds = intArrayOf(R.id.row_1, R.id.row_2, R.id.row_3)
        val priceIds = intArrayOf(R.id.price_1, R.id.price_2, R.id.price_3)
        val changeIds = intArrayOf(R.id.change_1, R.id.change_2, R.id.change_3)

        for (i in 0..2) {
            val row = view.findViewById<View>(rowIds[i])
            val sym = currentSymbols.getOrNull(i)
            if (sym == null) {
                row.visibility = View.GONE
                continue
            }
            row.visibility = View.VISIBLE

            val ticker = tickers[sym]
            val priceView = view.findViewById<TextView>(priceIds[i])
            val changeView = view.findViewById<TextView>(changeIds[i])

            when {
                ticker != null -> {
                    priceView.text = formatPrice(ticker.price)
                    priceView.setTextColor(COLOR_NEUTRAL)
                    val pct = ticker.priceChangePercent
                    changeView.text =
                        if (pct >= 0) "+%.2f%%".format(pct) else "%.2f%%".format(pct)
                    changeView.setTextColor(
                        when {
                            pct > 0 -> COLOR_UP
                            pct < 0 -> COLOR_DOWN
                            else -> COLOR_MUTE
                        },
                    )
                }
                streamError != null -> {
                    priceView.text = "no data"
                    priceView.setTextColor(COLOR_DOWN)
                    changeView.text = ""
                }
                else -> {
                    priceView.text = "..."
                    priceView.setTextColor(COLOR_MUTE)
                    changeView.text = ""
                }
            }
        }
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Overlay",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
            mgr.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val piFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val openPi = PendingIntent.getActivity(this, 0, openIntent, piFlags)

        val stopIntent = Intent(this, OverlayService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(this, 1, stopIntent, piFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("cry overlay")
            .setContentText("tap overlay to open · long-press to hide")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Hide", stopPi)
            .build()
    }

    override fun onDestroy() {
        applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefsListener)
        streamJob?.cancel()
        scope.cancel()
        runCatching { overlayView?.let { windowManager.removeView(it) } }
        overlayView = null
        _isRunning.value = false
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "overlay_channel"
        private const val NOTIF_ID = 42
        private const val PREFS_NAME = "cry_prefs"
        private const val PREFS_KEY = "pairs"
        const val ACTION_STOP = "com.cry.app.STOP_OVERLAY"

        private const val COLOR_UP = 0xFF6BE3A8.toInt()
        private const val COLOR_DOWN = 0xFFE36B6B.toInt()
        private const val COLOR_MUTE = 0xFF6E6E6E.toInt()
        private const val COLOR_NEUTRAL = 0xFFEDEDED.toInt()

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, OverlayService::class.java).apply { action = ACTION_STOP },
            )
        }
    }
}
