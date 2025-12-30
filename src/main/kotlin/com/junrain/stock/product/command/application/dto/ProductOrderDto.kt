package com.junrain.stock.product.command.application.dto

class ProductOrderDto {
    class Command {
        data class CancelOrder(
            val productId: Long, val quantity: Long
        )

        data class CancelReservation(
            val productId: Long, val quantity: Long
        )

        data class OrderProducts(
            val productId: Long, val quantity: Long
        )

        data class ReserveStockCommand(
            val productId: Long, val quantity: Long
        )
    }

    class Result {
        data class CancelOrder(
            val productId: Long, val canceledQuantity: Long
        )


        data class CancelReservation(
            val productId: Long, val canceledQuantity: Long
        )


        data class OrderProducts(
            val productId: Long, val orderedQuantity: Long
        )


        data class ReserveStock(
            val productId: Long,
            val reservedQuantity: Long
        )
    }
}