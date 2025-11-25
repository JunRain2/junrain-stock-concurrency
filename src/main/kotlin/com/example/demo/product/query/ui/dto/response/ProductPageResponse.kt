package com.example.demo.product.query.ui.dto.response

import com.example.demo.product.query.application.dto.ProductPageResult
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
        fun from(result: ProductPageResult): ProductPageResponse {
            return ProductPageResponse(
                productId = result.productId,
                name = result.name,
                price = result.price.amount,
                owner = OwnerResponse(
                    ownerId = result.owner.ownerId,
                    name = result.owner.name
                ),
                createdAt = result.createdAt
            )
        }
    }
}
