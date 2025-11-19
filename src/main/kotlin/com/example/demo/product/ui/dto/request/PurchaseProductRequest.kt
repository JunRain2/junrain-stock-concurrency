package com.example.demo.product.ui.dto.request

import jakarta.validation.constraints.Min

data class PurchaseProductRequest(
    @field:Min(1, message = "상품은 한 개 이상 구매해야 합니다.")
    val amount: Long
)
