package com.example.demo.product.ui.dto.response

import java.math.BigDecimal

data class ProductDetailResponse(
    val productId: Long,
    val name: String,
    val code: String,
    val price: BigDecimal,
    val stock: Long,
    val owner: OwnerResponse
) {
    data class OwnerResponse(
        val id: Long,
        val name: String
    )
}