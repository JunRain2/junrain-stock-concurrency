package com.example.demo.product.application

import com.example.demo.common.lock.LockRepository
import com.example.demo.product.application.exception.NotFoundProductException
import com.example.demo.product.domain.product.ProductRepository
import com.example.demo.product.ui.dto.request.PurchaseProductRequest
import com.example.demo.product.ui.dto.response.PurchaseProductResponse
import org.springframework.stereotype.Service

@Service
class PurchaseProductService(
    private val productRepository: ProductRepository,
    private val lockRepository: LockRepository,
) {
    fun decreaseStock(productId: Long, request: PurchaseProductRequest): PurchaseProductResponse {
        lockRepository.executeWithLock("product:${productId}") {
            val product = productRepository.findById(productId)
                .orElseThrow { throw NotFoundProductException() }

            lockRepository.executeWithLock(product.id.toString()) { product.decrease(request.amount) }

            productRepository.save(product)
        }

        return PurchaseProductResponse(productId)
    }
}