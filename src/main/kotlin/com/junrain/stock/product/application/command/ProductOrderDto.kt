package com.junrain.stock.product.application.command

import com.junrain.stock.common.domain.Money

class ProductOrderDto {
    class Command {
        data class CancelOrder(
            val productId: Long,
            val quantity: Long,
        )

        data class CancelReservation(
            val productId: Long,
            val quantity: Long,
        )

        data class OrderProducts(
            val productId: Long,
            val quantity: Long,
        )

        data class ReserveProducts(
            val productId: Long,
            val quantity: Long,
        )
    }

    class Result {
        data class CancelOrder(
            val productId: Long,
            val canceledQuantity: Long,
        )

        data class CancelReservation(
            val productId: Long,
            val canceledQuantity: Long,
        )

        data class OrderProducts(
            val productId: Long,
            val orderedQuantity: Long,
        )

        data class ReserveProducts(
            val productId: Long,
            val sellerId: Long,
            val reservedQuantity: Long,
            val price: Money,
        )
    }
}
