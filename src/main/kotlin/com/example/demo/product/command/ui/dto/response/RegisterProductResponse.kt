package com.example.demo.product.command.ui.dto.response

import com.example.demo.product.command.application.dto.result.RegisterProductResult

data class RegisterProductResponse(
    val productid: Long
) {
    companion object {
        fun from(result: RegisterProductResult): RegisterProductResponse {
            return RegisterProductResponse(
                productid = result.productId
            )
        }
    }
}