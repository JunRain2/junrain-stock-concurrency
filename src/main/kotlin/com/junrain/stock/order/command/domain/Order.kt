package com.junrain.stock.order.command.domain

import com.junrain.stock.contract.entity.BaseEntity
import com.junrain.stock.contract.vo.Money
import com.junrain.stock.order.command.domain.vo.OrderCode
import com.junrain.stock.order.command.domain.vo.Orderer
import jakarta.persistence.*

@Entity
@Table(name = "orders")
class Order(
    @Embedded private val orderer: Orderer,
    orderItems: List<OrderItem>,
    totalAmount: Money,

    @Embedded val code: OrderCode,
) : BaseEntity() {
    @Embedded
    @AttributeOverrides(
        AttributeOverride(
            name = "amount", column = Column(name = "order_total_amount")
        ), AttributeOverride(
            name = "currencyCode", column = Column(name = "order_currency_code")
        )
    )
    var totalAmount: Money = totalAmount

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status")
    var status: OrderStatus = OrderStatus.PENDING

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    private val _orderItems: MutableList<OrderItem> = orderItems.toMutableList()
    val orderItems: List<OrderItem> get() = _orderItems.toList()
}

enum class OrderStatus() {
    PENDING, CONFIRMED, PARTIAL_CANCELLED, CANCELLED
}