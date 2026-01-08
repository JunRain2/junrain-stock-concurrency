package com.junrain.stock.product.command.application

import com.junrain.stock.product.command.application.dto.ProductOrderDto
import com.junrain.stock.product.command.application.dto.ProductPurchaseDto
import com.junrain.stock.product.command.domain.ProductRepository
import com.junrain.stock.product.command.domain.ProductStockService
import com.junrain.stock.product.command.domain.StockChange
import com.junrain.stock.product.exception.ProductNotFoundException
import com.junrain.stock.product.exception.ProductOutOfStockException
import org.springframework.stereotype.Service

@Service
class ProductOrderService(
    private val productRepository: ProductRepository,
    private val productStockService: ProductStockService
) {
    fun reserveProducts(commands: List<ProductPurchaseDto.Command.Purchase>): List<ProductOrderDto.Result.ReserveStock> {
        val products = productRepository.findAllByIds(commands.map { it.productId }).also {
                if (it.size != commands.size) throw ProductNotFoundException()
            }.associateBy { it.id }

        val stockItems = commands.map {
            StockChange(productId = it.productId, quantity = it.quantity)
        }

        try {
            productStockService.reserve(*stockItems.toTypedArray())
        } catch (e: ProductOutOfStockException) {
            productStockService.cancelReservation(*stockItems.toTypedArray())
            throw e
        }

        return commands.map {
            ProductOrderDto.Result.ReserveStock(
                productId = it.productId,
                reservedQuantity = it.quantity,
                totalAmount = products.getValue(it.productId).price * it.quantity
            )
        }
    }

    fun cancelReservationProducts(commands: List<ProductPurchaseDto.Command.Purchase>): List<ProductPurchaseDto.Result.Purchase> {
        val stockItems = commands.map {
            StockChange(productId = it.productId, quantity = it.quantity)
        }

        productStockService.reserve(*stockItems.toTypedArray())

        return commands.map { ProductPurchaseDto.Result.Purchase(it.productId) }
    }

    fun deductStocks(commands: List<ProductPurchaseDto.Command.Purchase>): List<ProductPurchaseDto.Result.Purchase> {
        val stockItems = commands.map {
            StockChange(productId = it.productId, quantity = it.quantity)
        }

        productStockService.decrease(*stockItems.toTypedArray())

        return commands.map { ProductPurchaseDto.Result.Purchase(it.productId) }
    }

    fun cancelOrderProducts(commands: List<ProductPurchaseDto.Command.Purchase>): List<ProductPurchaseDto.Result.Purchase> {
        val stockItems = commands.map {
            StockChange(productId = it.productId, quantity = it.quantity)
        }

        productStockService.increase(*stockItems.toTypedArray())

        return commands.map { ProductPurchaseDto.Result.Purchase(it.productId) }
    }
}