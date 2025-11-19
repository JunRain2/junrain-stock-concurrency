package com.example.demo.ui.dto.response

import java.math.BigDecimal

data class GetProductResponse(
    val productId: Long,
    val name: String,
    val code: String,
    val price: BigDecimal,
    val stock: Long
)