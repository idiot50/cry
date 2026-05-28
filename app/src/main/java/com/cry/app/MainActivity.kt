package com.cry.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cry.app.ui.PriceScreen

class MainActivity : ComponentActivity() {
    private val vm: PriceViewModel by viewModels()

    private val overlayPermLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (Settings.canDrawOverlays(this)) {
            OverlayService.start(this)
        }
    }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* result ignored — service still runs without notif perm */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color.Black,
                    surface = Color.Black,
                ),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black,
                ) {
                    val pairs by vm.pairs.collectAsStateWithLifecycle()
                    val tickers by vm.tickers.collectAsStateWithLifecycle()
                    val busy by vm.busy.collectAsStateWithLifecycle()
                    val addError by vm.addError.collectAsStateWithLifecycle()
                    val overlayRunning by OverlayService.isRunning.collectAsStateWithLifecycle()

                    PriceScreen(
                        pairs = pairs,
                        tickers = tickers,
                        busy = busy,
                        addError = addError,
                        overlayRunning = overlayRunning,
                        onAdd = vm::addPair,
                        onRemove = vm::removePair,
                        onClearError = vm::clearAddError,
                        onToggleOverlay = ::toggleOverlay,
                    )
                }
            }
        }
    }

    private fun toggleOverlay() {
        if (OverlayService.isRunning.value) {
            OverlayService.stop(this)
            return
        }
        if (Settings.canDrawOverlays(this)) {
            OverlayService.start(this)
        } else {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            )
            overlayPermLauncher.launch(intent)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
