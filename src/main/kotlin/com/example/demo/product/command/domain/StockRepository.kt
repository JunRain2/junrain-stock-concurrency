package com.example.demo.product.command.domain

interface StockRepository {
    fun decreaseStock(vararg productItems: StockItem)

    fun increaseStock(vararg productItems: StockItem)
}

data class StockItem(
    val productId: Long,
    val quantity: Long,
)