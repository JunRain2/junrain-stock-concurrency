package com.example.demo.product.command.application

import com.example.demo.product.command.domain.ProductStockDecreasedEvent
import com.example.demo.product.command.domain.StockRepository
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Service
class ProductStockDecreasedHandler(
    private val stockRepository: StockRepository,
) {
    // 실패했을 경우, 실패한 만큼 재고를 보상
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    @Async
    fun handleFailedProductStockDecrease(event: ProductStockDecreasedEvent) {
        for (product in event.event.sortedBy { it.productId }) {
            stockRepository.increaseStock(product.productId, product.stock)
        }
    }
}