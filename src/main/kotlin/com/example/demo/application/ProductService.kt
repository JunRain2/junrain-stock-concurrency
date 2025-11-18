package com.example.demo.application

import com.example.demo.application.exception.NotFoundProductException
import com.example.demo.common.aop.NamedLock
import com.example.demo.domain.product.ProductRepository
import org.springframework.stereotype.Service

@Service
class ProductService(
    private val productRepository: ProductRepository,
) {
    @NamedLock(["#productId"])
    fun purchaseWithLock(productId: Long, quantity: Long) {
        val product = productRepository.findById(productId).orElseThrow { throw NotFoundProductException() }

        product.decrease(quantity)

        productRepository.save(product)
    }
}