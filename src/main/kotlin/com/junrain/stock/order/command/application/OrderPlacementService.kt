package com.junrain.stock.order.command.application

import com.junrain.stock.order.command.application.dto.OrderPlacementDto
import com.junrain.stock.order.command.domain.Order
import com.junrain.stock.order.command.domain.OrderRepository
import com.junrain.stock.order.command.domain.ProductCatalogService
import com.junrain.stock.order.command.domain.vo.OrderCode
import com.junrain.stock.order.exception.OrderNotFoudException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service

@Service
class OrderPlacementService(
    private val orderRepository: OrderRepository,
    private val productCatalogService: ProductCatalogService,
    private val applicationScope: CoroutineScope,
) {
    fun placeAnOrder(command: OrderPlacementDto.Command.PlaceAnOrder): OrderPlacementDto.Result.PlaceAnOrder {
        val orderItems = productCatalogService.fulfillOrderItems(command.products)
        val order = Order(
            orderer = command.orderer,
            orderItems = orderItems,
            code = OrderCode()
        ).let { orderRepository.save(it) }

        return OrderPlacementDto.Result.PlaceAnOrder(
            orderCode = order.code,
            totalAmount = order.totalAmount
        )
    }

    fun completePayment(command: OrderPlacementDto.Command.CompletePayment): OrderPlacementDto.Result.CompletePayment {
        val order = orderRepository.findByCode(command.orderCode)?.also {
            it.completePayment()
            orderRepository.save(it)
        } ?: throw OrderNotFoudException()

        applicationScope.launch {
            productCatalogService.deductStocks(order.orderItems)
        }

        return OrderPlacementDto.Result.CompletePayment(
            orderCode = order.code,
            orderStatus = order.status
        )
    }
}