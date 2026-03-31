package com.junrain.stock.order.domain

import com.junrain.stock.common.domain.Money
import com.junrain.stock.order.application.command.OrderPlacementDto

interface ProductCatalogService {
    fun reserveProductsForOrder(orderProducts: List<OrderPlacementDto.Command.PlaceAnOrder.PlaceAnOrderProduct>): List<OrderProduct>

    fun deductStocks(orderItems: List<OrderItem>)
}

data class OrderProduct(
    val productId: Long,
    val sellerId: Long,
    val quantity: Long,
    val price: Money,
)
