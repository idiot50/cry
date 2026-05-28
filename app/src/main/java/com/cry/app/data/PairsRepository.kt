package com.cry.app.data

import android.content.Context

class PairsRepository(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): List<String> {
        val raw = prefs.getString(KEY, null) ?: return DEFAULTS
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun save(pairs: List<String>) {
        prefs.edit().putString(KEY, pairs.joinToString(",")).apply()
    }

    companion object {
        private const val PREFS = "cry_prefs"
        private const val KEY = "pairs"
        private val DEFAULTS = listOf("BTCUSDT", "ETHUSDT")
    }
}
