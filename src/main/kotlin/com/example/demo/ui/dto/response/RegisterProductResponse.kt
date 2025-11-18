package com.example.demo.ui.dto.response

data class RegisterProductResponse(
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