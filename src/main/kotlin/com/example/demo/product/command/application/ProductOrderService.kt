package com.example.demo.product.command.application

import com.example.demo.product.command.application.dto.ProductOrderDto
import com.example.demo.product.command.application.dto.ProductPurchaseDto
import com.example.demo.product.command.domain.ProductStockService
import com.example.demo.product.command.domain.StockChange
import com.example.demo.product.exception.ProductOutOfStockException
import org.springframework.stereotype.Service

@Service
class ProductOrderService(
    private val productStockService: ProductStockService
) {
    fun reserveProductStock(commands: List<ProductPurchaseDto.Command.Purchase>): List<ProductPurchaseDto.Result.Purchase> {
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

    fun cancelReservationProductStock(commands: List<ProductPurchaseDto.Command.Purchase>): List<ProductPurchaseDto.Result.Purchase> {
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

    fun reserveStock(command: ProductOrderDto.Command.ReserveStockCommand): ProductOrderDto.Result.ReserveStock {
        val stockItem = StockChange(productId = command.productId, quantity = command.quantity)

        try {
            productStockService.reserve(stockItem)
        } catch (e: ProductOutOfStockException) {
            productStockService.cancelReservation(stockItem)
            throw e
        }

        return ProductOrderDto.Result.ReserveStock(command.productId, command.quantity)
    }

    fun cancelReservation(command: ProductOrderDto.Command.CancelReservation): ProductOrderDto.Result.CancelReservation {
        val stockItem = StockChange(productId = command.productId, quantity = command.quantity)

        productStockService.cancelReservation(stockItem)

        return ProductOrderDto.Result.CancelReservation(command.productId, command.quantity)
    }

    fun order(command: ProductOrderDto.Command.OrderProducts): ProductOrderDto.Result.OrderProducts {
        val stockItem = StockChange(productId = command.productId, quantity = command.quantity)

        productStockService.decrease(stockItem)

        return ProductOrderDto.Result.OrderProducts(command.productId, command.quantity)
    }

    fun cancelOrder(command: ProductOrderDto.Command.CancelOrder): ProductOrderDto.Result.CancelOrder {
        val stockItem = StockChange(productId = command.productId, quantity = command.quantity)

        productStockService.increase(stockItem)

        return ProductOrderDto.Result.CancelOrder(command.productId, command.quantity)
    }
}