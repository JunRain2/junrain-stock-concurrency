package com.example.demo.product.command.ui.dto

import com.example.demo.product.command.application.dto.RegisterProductResult

data class ProductRegisterResponse(
    val productId: Long
) {
    companion object {
        fun from(result: RegisterProductResult): ProductRegisterResponse {
            return ProductRegisterResponse(
                productId = result.productId
            )
        }
    }
}