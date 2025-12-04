package com.example.demo.product.command.application

import com.example.demo.product.command.application.dto.PurchaseProductCommand
import com.example.demo.product.command.application.dto.PurchaseProductResult
import com.example.demo.product.command.domain.ProductStockDecreasedEvent
import com.example.demo.product.command.domain.StockRepository
import jakarta.transaction.Transactional
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class ProductPurchaseService(
    private val stockRepository: StockRepository,
    private val applicationEventPublisher: ApplicationEventPublisher
) {
    companion object {
        const val PRODUCT_PREFIX = "product:"
    }

//    // TODO 사용자가 만들어지면 변경될 수 있는 코드
//    fun decreaseStock(command: PurchaseProductCommand): PurchaseProductResult {
//        lockRepository.executeWithLock("product:${command.productId}") {
//            val product = productRepository.findById(command.productId)
//                .orElseThrow { throw ProductNotFoundException() }
//
//            product.decrease(command.amount)
//
//            productRepository.save(product)
//        }
//
//        return PurchaseProductResult(command.productId)
//    }

    // TODO 성능 테스트를 위해 도메인 로직에 대한 추상화 X
    @Transactional
    fun decreaseStock(commands: List<PurchaseProductCommand>): List<PurchaseProductResult> {
        val result = mutableListOf<PurchaseProductCommand>()

        try {
            for (command in commands.sortedBy { it.productId }) {
                try {
                    stockRepository.decreaseStock(command.productId, command.amount)
                    result.add(command)
                } catch (e: Exception) {
                    // Redis에서 decrement는 이미 실행되었으므로
                    // 보상을 위해 result에 추가하고 예외를 다시 던짐
                    result.add(command)
                    throw e
                }
            }
        } finally {
            // 성공했을 경우 -> DB에 반영 (현재는 배치로 처리 예정)
            // 실패했을 경우 -> Redis에 보상로직
            val productStockEvents = result.map {
                ProductStockDecreasedEvent.ProductStock(
                    productId = it.productId, stock = it.amount
                )
            }
            applicationEventPublisher.publishEvent(ProductStockDecreasedEvent(productStockEvents))
        }

        return result.map { PurchaseProductResult(it.productId) }
    }
}