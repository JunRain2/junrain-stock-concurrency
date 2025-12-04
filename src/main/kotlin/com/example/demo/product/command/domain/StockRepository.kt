package com.example.demo.product.command.domain

interface StockRepository {
    fun decreaseStock(productId: Long, quantity: Long)

    fun increaseStock(productId: Long, quantity: Long)

    fun setStock(productId: Long, quantity: Long)
}