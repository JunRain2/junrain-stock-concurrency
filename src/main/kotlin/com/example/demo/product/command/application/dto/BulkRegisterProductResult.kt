package com.example.demo.product.command.application.dto

data class BulkRegisterProductResult(
    val successCount: Int,
    val failureCount: Int,
    val failedProducts: List<FailedRegisterProduct>
) {
    data class FailedRegisterProduct(
        val name: String,
        val price: Long,
        val stock: Long,
        val message: String
    )
}
