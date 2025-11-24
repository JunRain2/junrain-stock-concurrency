package com.example.demo.product.command.application.dto.request

import com.example.demo.product.command.ui.dto.request.PurchaseProductRequest

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
