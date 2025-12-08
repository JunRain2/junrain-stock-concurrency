package com.example.demo.product.command.application

import com.example.demo.product.command.domain.FailedProductStockDecreasedEvent
import com.example.demo.product.command.domain.StockItem
import com.example.demo.product.command.domain.StockRepository
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class ProductStockDecreasedHandler(
    private val stockRepository: StockRepository,
) {
    // 실패했을 경우, 실패한 만큼 재고를 보상
    @EventListener(classes = [FailedProductStockDecreasedEvent::class])
    @Async
    fun handleFailedProductStockDecrease(event: FailedProductStockDecreasedEvent) {
        stockRepository.updateStocks(*event.event.map {
            StockItem(
                productId = it.productId, quantity = it.stock
            )
        }.toTypedArray())
    }
}