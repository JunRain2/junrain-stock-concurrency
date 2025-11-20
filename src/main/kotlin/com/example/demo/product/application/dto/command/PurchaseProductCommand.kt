package com.example.demo.product.application.dto.command

import com.example.demo.product.ui.dto.request.PurchaseProductRequest

data class PurchaseProductCommand(
    val productId: Long,
    val amount: Long
) {
    companion object {
        fun of(productId: Long, request: PurchaseProductRequest): PurchaseProductCommand {
            return PurchaseProductCommand(
                productId = productId,
                amount = request.amount
            )
        }
    }
}
