package com.junrain.stock.ui.product.dto

import com.junrain.stock.application.product.GetProductDetail
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
        fun from(result: GetProductDetail.Result): ProductDetailResponse =
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
