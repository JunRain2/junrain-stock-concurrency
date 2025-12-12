package com.example.demo.product.command.application

import com.example.demo.global.contract.InfraHandledException
import com.example.demo.product.command.application.dto.PurchaseProductCommand
import com.example.demo.product.command.application.dto.PurchaseProductResult
import com.example.demo.product.command.domain.ProductStockService
import com.example.demo.product.command.domain.StockChange
import com.example.demo.product.exception.ProductOutOfStockException
import org.springframework.stereotype.Service

@Service
class ProductOrderService(
    private val productStockService: ProductStockService
) {
    fun reserveProductStock(commands: List<PurchaseProductCommand>): List<PurchaseProductResult> {
        val stockItems = commands.map {
            StockChange(productId = it.productId, quantity = it.amount)
        }

        try {
            productStockService.reserve(*stockItems.toTypedArray())
        } catch (e: Exception) {
            when (e) {
                is InfraHandledException -> {}
                // 롤백 로직을 수행
                is ProductOutOfStockException -> {}
            }
        }

        return commands.map { PurchaseProductResult(it.productId) }
    }
}