package com.cry.app.ui

import java.text.DecimalFormat
import kotlin.math.abs

private val KNOWN_QUOTES = listOf("USDT", "USDC", "BUSD", "BTC", "ETH", "FDUSD", "TUSD")

fun formatSymbol(s: String): String {
    val quote = KNOWN_QUOTES.firstOrNull { s.endsWith(it) && s.length > it.length }
    return if (quote != null) {
        val base = s.removeSuffix(quote)
        "$base · $quote"
    } else {
        s
    }
}

private val fmtBig = DecimalFormat("#,##0.00")
private val fmtSmall = DecimalFormat("#,##0.0000")
private val fmtTiny = DecimalFormat("#,##0.00000000")

fun formatPrice(v: Double): String {
    val a = abs(v)
    return when {
        a >= 1.0 -> fmtBig.format(v)
        a >= 0.001 -> fmtSmall.format(v)
        else -> fmtTiny.format(v)
    }
}
