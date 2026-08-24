package com.junrain.stock.application.product.query

import com.junrain.stock.application.product.GetProductPage
import java.math.BigDecimal
import java.time.LocalDateTime

sealed class ProductSorter {
    abstract val lastProductId: Long?

    abstract fun getNextCursor(product: GetProductPage.Result): Map<String, Any>

    data class LatestSorter(
        override val lastProductId: Long?,
        val createdAt: LocalDateTime?,
    ) : ProductSorter() {
        override fun getNextCursor(product: GetProductPage.Result): Map<String, Any> =
            mapOf(
                "lastProductId" to product.productId,
                "createdAt" to product.createdAt,
            )
    }

    data class SalePriceAsc(
        override val lastProductId: Long?,
        val price: BigDecimal?,
    ) : ProductSorter() {
        override fun getNextCursor(product: GetProductPage.Result): Map<String, Any> =
            mapOf(
                "lastProductId" to product.productId,
                "price" to product.price.amount,
            )
    }

    data class SalePriceDesc(
        override val lastProductId: Long?,
        val price: BigDecimal?,
    ) : ProductSorter() {
        override fun getNextCursor(product: GetProductPage.Result): Map<String, Any> =
            mapOf(
                "lastProductId" to product.productId,
                "price" to product.price.amount,
            )
    }
}
