package com.junrain.stock.product.command.application.dto

class ProductPurchaseDto {
    class Command {
        data class Purchase(
            val productId: Long,
            val quantity: Long
        )
    }

    class Result {
        data class Purchase(
            val productId: Long
        )
    }
}
