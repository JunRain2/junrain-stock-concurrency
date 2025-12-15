package com.example.demo.product.command.application.dto

class ProductPurchaseDto {
    class Command {
        data class Purchase(
            val productId: Long,
            val amount: Long
        )
    }

    class Result {
        data class Purchase(
            val productId: Long
        )
    }
}
