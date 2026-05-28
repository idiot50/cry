package com.cry.app.data

data class TickerData(
    val symbol: String,
    val price: Double,
    val priceChangePercent: Double,
    val lastUpdate: Long = System.currentTimeMillis(),
    val direction: Int = 0,
)
