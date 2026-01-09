package com.junrain.stock.order.command.application

import com.junrain.stock.contract.lock.LockRepository
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
    private val lockRepository: LockRepository
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

    // 분산락으로 구현해야 함
    fun payOrder(command: OrderPlacementDto.Command.PayOrder): OrderPlacementDto.Result.CompletePayment {
        val savedOrder = lockRepository.executeWithLock(formatOrderLockKey(command.orderCode)) {
            val order =
                orderRepository.findByCode(command.orderCode) ?: throw OrderNotFoudException()

            applicationScope.launch {
                productCatalogService.deductStocks(order.orderItems)
            }

            order.markAsPaid()
            orderRepository.save(order)
        }

        return OrderPlacementDto.Result.CompletePayment(
            orderCode = savedOrder.code,
            orderStatus = savedOrder.status
        )
    }

    fun formatOrderLockKey(orderCode: OrderCode) = "order_code:${orderCode.code}"
}