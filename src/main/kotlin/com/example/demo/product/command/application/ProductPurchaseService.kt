package com.example.demo.product.command.application

import com.example.demo.product.exception.ProductNotFoundException
import com.example.demo.global.lock.LockRepository
import com.example.demo.product.command.application.dto.PurchaseProductCommand
import com.example.demo.product.command.application.dto.PurchaseProductResult
import com.example.demo.product.command.domain.ProductRepository
import org.springframework.stereotype.Service

@Service
class ProductPurchaseService(
    private val productRepository: ProductRepository,
    private val lockRepository: LockRepository,
) {
    // TODO 사용자가 만들어지면 변경될 수 있는 코드
    fun decreaseStock(command: PurchaseProductCommand): PurchaseProductResult {
        lockRepository.executeWithLock("product:${command.productId}") {
            val product = productRepository.findById(command.productId)
                .orElseThrow { throw ProductNotFoundException() }

            product.decrease(command.amount)

            productRepository.save(product)
        }

        return PurchaseProductResult(command.productId)
    }
}