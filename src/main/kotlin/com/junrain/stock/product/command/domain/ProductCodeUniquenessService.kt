package com.junrain.stock.product.command.domain

import com.junrain.stock.product.exception.ProductDuplicateCodeException
import org.springframework.stereotype.Service

@Service
class ProductCodeUniquenessService {
    fun ensureProductCodeUniqueness(products: List<Result<Product>>): List<Result<Product>> {
        val duplication = products.mapNotNull { it.getOrNull() }
            .groupingBy { it.code }
            .eachCount()
            .filter { it.value > 1 }
            .keys.toSet()

        return products.map { result ->
            result.mapCatching { product ->
                if (product.code in duplication) {
                    throw ProductDuplicateCodeException(product.code)
                }
                product
            }
        }
    }
}