package com.junrain.stock.order.command.domain

import com.junrain.stock.contract.vo.Money
import com.junrain.stock.order.command.application.dto.OrderPlacementDto

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