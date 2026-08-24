package com.junrain.stock.ui.product.dto

import com.junrain.stock.application.product.GetProductPage
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
        fun from(result: GetProductPage.Result): ProductPageResponse =
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
