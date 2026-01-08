package com.junrain.stock.order.command.application.dto

import com.junrain.stock.contract.vo.Money
import com.junrain.stock.order.command.domain.OrderStatus
import com.junrain.stock.order.command.domain.vo.OrderCode
import com.junrain.stock.order.command.domain.vo.Orderer

class OrderPlacementDto {
    class Command {
        data class PlaceAnOrder(
            val orderer: Orderer, val products: List<PlaceAnOrderProduct>
        ) {
            data class PlaceAnOrderProduct(
                val productId: Long, val quantity: Long
            ) {
                init {
                    require(quantity > 0) { "재고는 양수여야 합니다." }
                }
            }
        }

        data class CompletePayment(
            val orderCode: OrderCode
        )
    }

    class Result {
        data class PlaceAnOrder(
            val orderCode: OrderCode,
            val totalAmount: Money,
        )

        data class CompletePayment(
            val orderCode: OrderCode, val orderStatus: OrderStatus
        )
    }
}