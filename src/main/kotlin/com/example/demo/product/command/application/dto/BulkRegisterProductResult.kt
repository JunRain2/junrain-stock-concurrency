package com.example.demo.product.command.application.dto

data class BulkRegisterProductResult(
    val successCount: Int,
    val failureCount: Int,
    val failedProducts: List<FailedRegisterProduct>
) {
    data class FailedRegisterProduct(
        val code: String,
        val cause: String?
    )
}
