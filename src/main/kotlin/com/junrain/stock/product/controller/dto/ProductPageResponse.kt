package com.junrain.stock.product.controller.dto

import com.junrain.stock.product.application.query.ProductPageResult
import java.math.BigDecimal
import java.time.LocalDateTime

data class ProductPageResponse(
    val productId: Long,
    val name: String,
    val price: BigDecimal,
    val owner: OwnerResponse,
    val createdAt: LocalDateTime,
) {
    data class OwnerResponse(
        val ownerId: Long,
        val name: String,
    )

    companion object {
        fun from(result: ProductPageResult): ProductPageResponse =
            ProductPageResponse(
                productId = result.productId,
                name = result.name,
                price = result.price.amount,
                owner =
                    OwnerResponse(
                        ownerId = result.owner.ownerId,
                        name = result.owner.name,
                    ),
                createdAt = result.createdAt,
            )
    }
}
