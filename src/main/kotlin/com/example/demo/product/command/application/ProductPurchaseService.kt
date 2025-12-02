package com.example.demo.product.command.application

import com.example.demo.global.lock.LockRepository
import com.example.demo.product.command.application.dto.PurchaseProductCommand
import com.example.demo.product.command.application.dto.PurchaseProductResult
import com.example.demo.product.command.domain.ProductRepository
import org.springframework.stereotype.Service

@Service
class ProductPurchaseService(
    private val productRepository: ProductRepository, private val lockRepository: LockRepository
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
    fun decreaseStock(commands: List<PurchaseProductCommand>): List<PurchaseProductResult> {
        val keys = commands.map { PRODUCT_PREFIX + it.productId }.toTypedArray()
        return lockRepository.executeWithLock(*keys) {
            val products = productRepository.findAllById(commands.map { it.productId }).toList()

            for (product in products) {
                val quantity = commands.find { it.productId == product.id }!!.amount

                product.decrease(quantity)
            }

            productRepository.saveAll(products).map { PurchaseProductResult(it.id) }
        }
    }
}