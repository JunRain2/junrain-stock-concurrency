package com.example.demo.product.command.application

import com.example.demo.product.command.application.dto.PurchaseProductCommand
import com.example.demo.product.command.application.dto.PurchaseProductResult
import com.example.demo.product.command.domain.ProductRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class ProductPurchaseService(
    private val productRepository: ProductRepository,
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
        val products = productRepository.findAllByIdsWithLock(commands.map { it.productId })

        for (product in products) {
            val quantity = commands.find { it.productId == product.id }!!.amount

            product.decrease(quantity)
        }

        return products.map { PurchaseProductResult(it.id) }
    }
}