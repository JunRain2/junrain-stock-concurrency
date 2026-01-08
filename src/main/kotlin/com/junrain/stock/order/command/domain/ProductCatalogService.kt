package com.junrain.stock.order.command.domain

import com.junrain.stock.order.command.application.dto.OrderPlacementDto

interface ProductCatalogService {
    fun fulfillOrderItems(orderProducts: List<OrderPlacementDto.Command.PlaceAnOrder.PlaceAnOrderProduct>): List<OrderItem>

    fun deductStocks(orderItems: List<OrderItem>)
}
