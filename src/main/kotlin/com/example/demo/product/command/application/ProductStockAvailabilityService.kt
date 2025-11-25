package com.example.demo.product.command.application

import com.example.demo.global.contract.exception.NotFoundProductException
import com.example.demo.product.command.domain.ProductRepository
import org.springframework.stereotype.Service

@Service
class ProductStockAvailabilityService(
    private val productRepository: ProductRepository
) {
    fun hasEnoughStock(productId: Long, requiredQuantity: Long): Boolean {
        val product =
            productRepository.findById(productId).orElseThrow { NotFoundProductException() }

        return product.hasEnoughStock(quantity = requiredQuantity)
    }
}