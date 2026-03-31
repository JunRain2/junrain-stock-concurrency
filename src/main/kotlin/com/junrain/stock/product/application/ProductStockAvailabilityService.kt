package com.junrain.stock.product.application

import com.junrain.stock.product.domain.ProductRepository
import org.springframework.stereotype.Service

@Service
class ProductStockAvailabilityService(
    private val productRepository: ProductRepository,
) {
    fun hasEnoughStock(
        productId: Long,
        requiredQuantity: Long,
    ): Boolean {
        val product = productRepository.findById(productId)

        return product.hasEnoughStock(quantity = requiredQuantity)
    }
}
