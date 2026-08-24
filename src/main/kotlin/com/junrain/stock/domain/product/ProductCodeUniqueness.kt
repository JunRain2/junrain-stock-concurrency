package com.junrain.stock.domain.product

import com.junrain.stock.domain.product.exception.ProductDuplicateCodeException

object ProductCodeUniqueness {
    fun ensure(products: List<Result<Product>>): List<Result<Product>> {
        val duplication =
            products
                .mapNotNull { it.getOrNull() }
                .groupingBy { it.code }
                .eachCount()
                .filter { it.value > 1 }
                .keys
                .toSet()

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
