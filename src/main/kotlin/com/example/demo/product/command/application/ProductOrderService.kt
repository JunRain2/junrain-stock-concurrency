package com.example.demo.product.command.application

import com.example.demo.product.command.application.dto.ProductPurchaseDto
import com.example.demo.product.command.domain.ProductStockService
import com.example.demo.product.command.domain.StockChange
import com.example.demo.product.exception.ProductOutOfStockException
import org.springframework.stereotype.Service

@Service
class ProductOrderService(
    private val productStockService: ProductStockService
) {
    fun reserveProducts(commands: List<ProductPurchaseDto.Command.Purchase>): List<ProductPurchaseDto.Result.Purchase> {
        val stockItems = commands.map {
            StockChange(productId = it.productId, quantity = it.amount)
        }

        try {
            productStockService.reserve(*stockItems.toTypedArray())
        } catch (e: ProductOutOfStockException) {
            productStockService.cancelReservation(*stockItems.toTypedArray())
            throw e
        }

        return commands.map { ProductPurchaseDto.Result.Purchase(it.productId) }
    }

    fun cancelReservationProducts(commands: List<ProductPurchaseDto.Command.Purchase>): List<ProductPurchaseDto.Result.Purchase> {
        val stockItems = commands.map {
            StockChange(productId = it.productId, quantity = it.amount)
        }

        productStockService.reserve(*stockItems.toTypedArray())

        return commands.map { ProductPurchaseDto.Result.Purchase(it.productId) }
    }

    fun orderProducts(commands: List<ProductPurchaseDto.Command.Purchase>): List<ProductPurchaseDto.Result.Purchase> {
        val stockItems = commands.map {
            StockChange(productId = it.productId, quantity = it.amount)
        }

        productStockService.decrease(*stockItems.toTypedArray())

        return commands.map { ProductPurchaseDto.Result.Purchase(it.productId) }
    }

    fun cancelOrderProducts(commands: List<ProductPurchaseDto.Command.Purchase>): List<ProductPurchaseDto.Result.Purchase> {
        val stockItems = commands.map {
            StockChange(productId = it.productId, quantity = it.amount)
        }

        productStockService.increase(*stockItems.toTypedArray())

        return commands.map { ProductPurchaseDto.Result.Purchase(it.productId) }
    }
}