package com.example.demo.product.command.ui.dto

import com.example.demo.product.command.application.dto.PurchaseProductResult

data class BulkPurchaseProductResponse(
    val purchasedProducts: List<PurchasedProduct>,
    val totalCount: Int
) {
    data class PurchasedProduct(
        val productId: Long
    )

    companion object {
        fun from(results: List<PurchaseProductResult>): BulkPurchaseProductResponse {
            return BulkPurchaseProductResponse(
                purchasedProducts = results.map { PurchasedProduct(it.productId) },
                totalCount = results.size
            )
        }
    }
}
