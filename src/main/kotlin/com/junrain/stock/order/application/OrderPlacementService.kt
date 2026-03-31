package com.junrain.stock.order.application

import com.junrain.stock.common.domain.LockRepository
import com.junrain.stock.common.domain.Money
import com.junrain.stock.order.application.command.OrderPlacementDto
import com.junrain.stock.order.domain.*
import com.junrain.stock.order.domain.exception.OrderInvalidException
import com.junrain.stock.order.domain.exception.OrderNotFoudException
import com.junrain.stock.order.domain.vo.OrderCode
import jakarta.transaction.Transactional
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class OrderPlacementService(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val productCatalogService: ProductCatalogService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val lockRepository: LockRepository,
) {
    // 일단 happy path로 구현해보자 예외는 난중에 생각해보고
    @Transactional
    fun placeAnOrder(command: OrderPlacementDto.Command.PlaceAnOrder): OrderPlacementDto.Result.PlaceAnOrder {
        val orderProducts = productCatalogService.reserveProductsForOrder(command.products)

        val order =
            Order(
                orderer = command.orderer,
                code = OrderCode(),
                totalAmount = Money.of(orderProducts.sumOf { (it.price * it.quantity).amount }),
            ).let { orderRepository.save(it) }

        val orderItems =
            orderProducts
                .map {
                    OrderItem(
                        productId = it.productId,
                        quantity = it.quantity,
                        price = it.price,
                        order = order,
                        sellerId = it.sellerId,
                    )
                }.let { orderItemRepository.saveAll(it) }

        return OrderPlacementDto.Result.PlaceAnOrder(
            orderCode = order.code,
            totalAmount = order.totalAmount,
        )
    }

    fun payOrder(command: OrderPlacementDto.Command.PayOrder): OrderPlacementDto.Result.CompletePayment {
        val savedOrder =
            lockRepository.executeWithLock(formatOrderLockKey(command.orderCode)) {
                val order =
                    orderRepository.findByCode(command.orderCode) ?: throw OrderNotFoudException()
                if (!order.isPurchasable()) throw OrderInvalidException()

                val orderItems = orderItemRepository.findAllByOrder(order)

                productCatalogService.deductStocks(orderItems)

                order.markAsPaid()
                orderRepository.save(order)
            }

        applicationEventPublisher.publishEvent(OrderPaidEvent(savedOrder.code))

        return OrderPlacementDto.Result.CompletePayment(
            orderCode = savedOrder.code,
            orderStatus = savedOrder.status,
        )
    }

    fun formatOrderLockKey(orderCode: OrderCode) = "order_code:${orderCode.code}"
}
