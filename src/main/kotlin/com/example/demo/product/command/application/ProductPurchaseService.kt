package com.example.demo.product.command.application

import com.example.demo.product.command.application.dto.PurchaseProductCommand
import com.example.demo.product.command.application.dto.PurchaseProductResult
import com.example.demo.product.command.domain.FailedProductStockDecreasedEvent
import com.example.demo.product.command.domain.StockItem
import com.example.demo.product.command.domain.StockRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class ProductPurchaseService(
    private val stockRepository: StockRepository,
    private val applicationEventPublisher: ApplicationEventPublisher
) {
    // TODO 성능 테스트를 위해 도메인 로직에 대한 추상화 X
    fun decreaseStock(commands: List<PurchaseProductCommand>): List<PurchaseProductResult> {
        val stockItems = commands.map {
            StockItem(
                productId = it.productId, quantity = it.amount
            )
        }

        try {
            stockRepository.decreaseStock(*stockItems.toTypedArray())
        } catch (e: Exception) {
            val event = FailedProductStockDecreasedEvent(stockItems.map {
                FailedProductStockDecreasedEvent.ProductStock(
                    productId = it.productId, stock = it.quantity
                )
            })
            applicationEventPublisher.publishEvent(event)

            throw e
        }

        return commands.map { PurchaseProductResult(it.productId) }
    }
}