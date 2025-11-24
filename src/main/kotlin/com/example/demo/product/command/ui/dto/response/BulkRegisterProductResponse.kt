package com.example.demo.product.command.ui.dto.response

data class BulkRegisterProductResponse(
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