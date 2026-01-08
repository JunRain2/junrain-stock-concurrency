package com.junrain.stock.order.command.application

import com.junrain.stock.contract.vo.Money
import com.junrain.stock.order.command.application.dto.OrderPlacementDto
import com.junrain.stock.order.command.domain.Order
import com.junrain.stock.order.command.domain.OrderRepository
import com.junrain.stock.order.command.domain.ProductCatalogService
import com.junrain.stock.order.command.domain.vo.OrderCode
import org.springframework.stereotype.Service

@Service
class OrderPlacementService(
    private val orderRepository: OrderRepository,
    private val productCatalogService: ProductCatalogService
) {
    fun placeAnOrder(command: OrderPlacementDto.Command.PlaceAnOrder): OrderPlacementDto.Result.PlaceAnOrder {
        val orderItems = productCatalogService.fulfillOrderItems(command.products)
        val order = Order(
            orderer = command.orderer,
            orderItems = orderItems,
            totalAmount = Money.of(orderItems.sumOf { it.totalAmounts.amount }),
            code = OrderCode()
        ).let { orderRepository.save(it) }

        return OrderPlacementDto.Result.PlaceAnOrder(
            orderCode = order.code,
            totalAmount = order.totalAmount
        )
    }
}