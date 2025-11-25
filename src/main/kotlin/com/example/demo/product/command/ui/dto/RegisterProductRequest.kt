package com.example.demo.product.command.ui.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class RegisterProductRequest(
    @field:NotBlank(message = "상품명은 필수입니다")
    val name: String,

    @field:Min(1, message = "가격은 반드시 양수여야 합니다.")
    val price: Long,

    @field:Min(1, message = "수량은 반드시 양수여야 합니다.")
    val stock: Long,

    @field:NotBlank(message = "상품코드는 필수입니다.")
    val code: String,
)