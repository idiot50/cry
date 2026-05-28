package com.cry.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

data class TickerUpdate(
    val symbol: String,
    val price: Double,
    val priceChangePercent: Double,
)

class BinanceClient {
    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private val _status = MutableStateFlow("idle")
    val status: StateFlow<String> = _status.asStateFlow()

    private var msgCount = 0L

    fun stream(symbols: List<String>): Flow<TickerUpdate> = callbackFlow {
        if (symbols.isEmpty()) {
            _status.value = "no symbols"
            close()
            awaitClose { }
            return@callbackFlow
        }
        val streams = symbols.joinToString("/") { "${it.lowercase()}@ticker" }
        val url = "wss://fstream.binance.com/stream?streams=$streams"
        _status.value = "connecting to fstream.binance.com"
        msgCount = 0
        val request = Request.Builder().url(url).build()

        val socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _status.value = "connected, waiting for first message"
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                msgCount++
                val update = parse(text)
                if (update == null) {
                    _status.value = "parse error msg#$msgCount"
                    return
                }
                _status.value = "live · $msgCount msgs"
                trySend(update)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _status.value = "closed ($code): ${reason.ifBlank { "no reason" }}"
                close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val cls = t.javaClass.simpleName
                val msg = t.message?.take(80) ?: ""
                _status.value = "error · $cls: $msg"
                close(t)
            }
        })

        awaitClose {
            socket.cancel()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun isValidSymbol(symbol: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://fapi.binance.com/fapi/v1/ticker/price?symbol=$symbol")
                .build()
            http.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    private fun parse(text: String): TickerUpdate? {
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val data = root["data"]?.jsonObject ?: return null
            val s = data["s"]?.jsonPrimitive?.content ?: return null
            val p = data["c"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
            val pct = data["P"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            TickerUpdate(s, p, pct)
        } catch (_: Exception) {
            null
        }
    }
}
