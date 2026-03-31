package com.junrain.stock.product.controller.dto

import com.junrain.stock.product.application.query.ProductDetailResult
import java.math.BigDecimal

data class ProductDetailResponse(
    val productId: Long,
    val name: String,
    val code: String,
    val price: BigDecimal,
    val stock: Long,
    val owner: OwnerResponse,
) {
    data class OwnerResponse(
        val id: Long,
        val name: String,
    )

    companion object {
        fun from(result: ProductDetailResult): ProductDetailResponse =
            ProductDetailResponse(
                productId = result.productId,
                name = result.name,
                code = result.code,
                price = result.price,
                stock = result.stock,
                owner =
                    OwnerResponse(
                        id = result.owner.id,
                        name = result.owner.name,
                    ),
            )
    }
}
