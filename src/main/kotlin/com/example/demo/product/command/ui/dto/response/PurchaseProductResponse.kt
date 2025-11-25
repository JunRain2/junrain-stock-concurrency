package com.example.demo.product.command.ui.dto.response

import com.example.demo.product.command.application.dto.result.PurchaseProductResult

data class PurchaseProductResponse(
    val productId: Long
) {
    companion object {
        fun from(result: PurchaseProductResult): PurchaseProductResponse {
            return PurchaseProductResponse(
                productId = result.productId
            )
        }
    }
}
