package com.junrain.stock.product.query.application.dto

import java.math.BigDecimal
import java.time.LocalDateTime

data class ProductPageQuery(
    val ownerId: Long?,
    val productName: String,
    val productSorter: ProductSorter,
    val size: Int = 10,
) {
    init {
        require(productName.isNotBlank()) { "상품명은 반드시 입력해야 합니다." }
        require(productName.length < 20) { "상품명은 20자 미만이어야 합니다." }
    }
}

sealed class ProductSorter() {
    abstract val lastProductId: Long?

    abstract fun getNextCursor(product: ProductPageResult): Map<String, Any>

    data class LatestSorter(
        override val lastProductId: Long?, val createdAt: LocalDateTime?
    ) : ProductSorter() {
        override fun getNextCursor(product: ProductPageResult): Map<String, Any> {
            return mapOf(
                "lastProductId" to product.productId, "createdAt" to product.createdAt
            )
        }
    }

    data class SalePriceAsc(
        override val lastProductId: Long?, val price: BigDecimal?
    ) : ProductSorter() {
        override fun getNextCursor(product: ProductPageResult): Map<String, Any> {
            return mapOf(
                "lastProductId" to product.productId, "price" to product.price.amount
            )
        }
    }

    data class SalePriceDesc(
        override val lastProductId: Long?, val price: BigDecimal?
    ) : ProductSorter() {
        override fun getNextCursor(product: ProductPageResult): Map<String, Any> {
            return mapOf(
                "lastProductId" to product.productId, "price" to product.price.amount
            )
        }
    }
}