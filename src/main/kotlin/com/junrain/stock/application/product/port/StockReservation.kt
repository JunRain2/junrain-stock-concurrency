package com.junrain.stock.application.product.port

interface StockReservation {
    fun reserve(vararg changes: StockChange)

    fun cancelReservation(vararg changes: StockChange)

    fun decrease(vararg changes: StockChange)

    fun increase(vararg changes: StockChange)
}

data class StockChange(
    val productId: Long,
    val quantity: Long,
)
