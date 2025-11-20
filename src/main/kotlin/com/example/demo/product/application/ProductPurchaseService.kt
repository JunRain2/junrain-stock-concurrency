package com.example.demo.product.application

import com.example.demo.global.lock.LockRepository
import com.example.demo.product.application.dto.command.PurchaseProductCommand
import com.example.demo.product.domain.product.ProductRepository
import com.example.demo.product.exception.NotFoundProductException
import com.example.demo.product.ui.dto.response.PurchaseProductResponse
import org.springframework.stereotype.Service

@Service
class ProductPurchaseService(
    private val productRepository: ProductRepository,
    private val lockRepository: LockRepository,
) {
    // TODO 사용자가 만들어지면
    fun decreaseStock(command: PurchaseProductCommand): PurchaseProductResponse {
        lockRepository.executeWithLock("product:${command.productId}") {
            val product = productRepository.findById(command.productId)
                .orElseThrow { throw NotFoundProductException() }

            product.decrease(command.amount)

            productRepository.save(product)
        }

        return PurchaseProductResponse(command.productId)
    }
}