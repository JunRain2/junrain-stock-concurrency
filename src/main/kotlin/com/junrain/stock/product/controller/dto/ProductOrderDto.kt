package com.junrain.stock.product.controller.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import com.junrain.stock.product.application.command.ProductOrderDto as AppProductOrderDto

class ProductOrderDto {
    class Request {
        data class ReserveStock(
            @field:NotNull
            @field:Min(1)
            val productId: Long,
            @field:NotNull
            @field:Min(1)
            val quantity: Long,
        )

        data class CancelReservation(
            @field:NotNull
            @field:Min(1)
            val productId: Long,
            @field:NotNull
            @field:Min(1)
            val quantity: Long,
        )

        data class OrderProducts(
            @field:NotNull
            @field:Min(1)
            val productId: Long,
            @field:NotNull
            @field:Min(1)
            val quantity: Long,
        )

        data class CancelOrder(
            @field:NotNull
            @field:Min(1)
            val productId: Long,
            @field:NotNull
            @field:Min(1)
            val quantity: Long,
        )
    }

    class Response {
        data class ReserveStock(
            val productId: Long,
            val reservedQuantity: Long,
        ) {
            companion object {
                fun from(result: AppProductOrderDto.Result.ReserveProducts): ReserveStock =
                    ReserveStock(
                        productId = result.productId,
                        reservedQuantity = result.reservedQuantity,
                    )
            }
        }

        data class CancelReservation(
            val productId: Long,
            val canceledQuantity: Long,
        ) {
            companion object {
                fun from(result: AppProductOrderDto.Result.CancelReservation): CancelReservation =
                    CancelReservation(
                        productId = result.productId,
                        canceledQuantity = result.canceledQuantity,
                    )
            }
        }

        data class OrderProducts(
            val productId: Long,
            val orderedQuantity: Long,
        ) {
            companion object {
                fun from(result: AppProductOrderDto.Result.OrderProducts): OrderProducts =
                    OrderProducts(
                        productId = result.productId,
                        orderedQuantity = result.orderedQuantity,
                    )
            }
        }

        data class CancelOrder(
            val productId: Long,
            val canceledQuantity: Long,
        ) {
            companion object {
                fun from(result: AppProductOrderDto.Result.CancelOrder): CancelOrder =
                    CancelOrder(
                        productId = result.productId,
                        canceledQuantity = result.canceledQuantity,
                    )
            }
        }
    }
}
