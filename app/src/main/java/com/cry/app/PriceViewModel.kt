package com.cry.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cry.app.data.PairsRepository
import com.cry.app.data.TickerData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PriceViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = PairsRepository(app)
    private val client = BinanceClient()

    private val _pairs = MutableStateFlow(repo.load())
    val pairs: StateFlow<List<String>> = _pairs.asStateFlow()

    private val _tickers = MutableStateFlow<Map<String, TickerData>>(emptyMap())
    val tickers: StateFlow<Map<String, TickerData>> = _tickers.asStateFlow()

    private val _addError = MutableStateFlow<String?>(null)
    val addError: StateFlow<String?> = _addError.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _streamError = MutableStateFlow<String?>(null)
    val streamError: StateFlow<String?> = _streamError.asStateFlow()

    private var streamJob: Job? = null

    init {
        restartStream()
    }

    private fun restartStream() {
        streamJob?.cancel()
        val symbols = _pairs.value
        _streamError.value = null
        if (symbols.isEmpty()) return
        streamJob = viewModelScope.launch {
            while (isActive) {
                try {
                    client.stream(symbols).collect { update ->
                        if (_streamError.value != null) _streamError.value = null
                        _tickers.update { current ->
                            val prev = current[update.symbol]
                            val direction = when {
                                prev == null -> 0
                                update.price > prev.price -> 1
                                update.price < prev.price -> -1
                                else -> prev.direction
                            }
                            current + (update.symbol to TickerData(
                                symbol = update.symbol,
                                price = update.price,
                                priceChangePercent = update.priceChangePercent,
                                direction = direction,
                            ))
                        }
                    }
                    _streamError.value = "disconnected"
                } catch (e: Exception) {
                    _streamError.value = e.message?.take(80) ?: "connection failed"
                }
                if (!isActive) break
                delay(2000)
            }
        }
    }

    fun addPair(input: String) {
        val normalized = input.uppercase().trim()
        if (normalized.isBlank()) return
        if (_pairs.value.contains(normalized)) {
            _addError.value = "already added"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            _addError.value = null
            val ok = client.isValidSymbol(normalized)
            _busy.value = false
            if (!ok) {
                _addError.value = "symbol not found"
                return@launch
            }
            _pairs.value = _pairs.value + normalized
            repo.save(_pairs.value)
            restartStream()
        }
    }

    fun removePair(symbol: String) {
        if (!_pairs.value.contains(symbol)) return
        _pairs.value = _pairs.value - symbol
        _tickers.update { it - symbol }
        repo.save(_pairs.value)
        restartStream()
    }

    fun clearAddError() {
        _addError.value = null
    }
}
