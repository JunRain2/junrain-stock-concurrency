package com.junrain.stock.product.command.ui.dto

import com.junrain.stock.product.command.application.dto.ProductOrderDto as AppProductOrderDto
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

class ProductOrderDto {
    class Request {
        data class ReserveStock(
            @field:NotNull
            @field:Min(1)
            val productId: Long,

            @field:NotNull
            @field:Min(1)
            val quantity: Long
        )

        data class CancelReservation(
            @field:NotNull
            @field:Min(1)
            val productId: Long,

            @field:NotNull
            @field:Min(1)
            val quantity: Long
        )

        data class OrderProducts(
            @field:NotNull
            @field:Min(1)
            val productId: Long,

            @field:NotNull
            @field:Min(1)
            val quantity: Long
        )

        data class CancelOrder(
            @field:NotNull
            @field:Min(1)
            val productId: Long,

            @field:NotNull
            @field:Min(1)
            val quantity: Long
        )
    }

    class Response {
        data class ReserveStock(
            val productId: Long,
            val reservedQuantity: Long
        ) {
            companion object {
                fun from(result: AppProductOrderDto.Result.ReserveStock): ReserveStock {
                    return ReserveStock(
                        productId = result.productId,
                        reservedQuantity = result.reservedQuantity
                    )
                }
            }
        }

        data class CancelReservation(
            val productId: Long,
            val canceledQuantity: Long
        ) {
            companion object {
                fun from(result: AppProductOrderDto.Result.CancelReservation): CancelReservation {
                    return CancelReservation(
                        productId = result.productId,
                        canceledQuantity = result.canceledQuantity
                    )
                }
            }
        }

        data class OrderProducts(
            val productId: Long,
            val orderedQuantity: Long
        ) {
            companion object {
                fun from(result: AppProductOrderDto.Result.OrderProducts): OrderProducts {
                    return OrderProducts(
                        productId = result.productId,
                        orderedQuantity = result.orderedQuantity
                    )
                }
            }
        }

        data class CancelOrder(
            val productId: Long,
            val canceledQuantity: Long
        ) {
            companion object {
                fun from(result: AppProductOrderDto.Result.CancelOrder): CancelOrder {
                    return CancelOrder(
                        productId = result.productId,
                        canceledQuantity = result.canceledQuantity
                    )
                }
            }
        }
    }
}
