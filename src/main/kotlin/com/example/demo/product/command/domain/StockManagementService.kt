package com.example.demo.product.command.domain

import org.springframework.stereotype.Service

@Service
class StockManagementService(
    private val stockRepository: StockRepository
) {
    fun decreaseStock(vararg stockItems: StockItem) {
        val result = stockRepository.updateStocks(*stockItems)
    }

    fun increaseStock(vararg stockItems: StockItem) {

    }
}

data class StockItem(
    val productId: Long,
    val quantity: Long,
)