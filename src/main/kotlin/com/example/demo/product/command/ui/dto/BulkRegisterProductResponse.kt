package com.example.demo.product.command.ui.dto

import com.example.demo.product.command.application.dto.BulkRegisterProductResult

data class BulkRegisterProductResponse(
    val successCount: Int,
    val failureCount: Int,
    val failedProducts: List<FailedRegisterProduct>
) {
    data class FailedRegisterProduct(
        val code: String,
        val message: String
    )

    companion object {
        fun from(result: BulkRegisterProductResult): BulkRegisterProductResponse {
            return BulkRegisterProductResponse(
                successCount = result.successCount,
                failureCount = result.failureCount,
                failedProducts = result.failedProducts.map {
                    FailedRegisterProduct(
                        code = it.code,
                        message = it.cause ?: "UNKNOWN"
                    )
                }
            )
        }
    }
}