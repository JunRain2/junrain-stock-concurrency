package com.junrain.stock.order.command.domain

import com.junrain.stock.contract.entity.BaseEntity
import com.junrain.stock.contract.vo.Money
import com.junrain.stock.order.command.domain.vo.OrderCode
import com.junrain.stock.order.command.domain.vo.Orderer
import jakarta.persistence.*

@Entity
@Table(name = "orders")
class Order(
    @Embedded
    private val orderer: Orderer,
    @OneToMany(mappedBy = "user")
    private val _orders: MutableList<Order> = mutableListOf(),
    totalAmount: Money,

    @Embedded
    val code: OrderCode,
) : BaseEntity() {
    @Embedded
    @AttributeOverrides(
        AttributeOverride(
            name = "amount",
            column = Column(name = "order_total_amount")
        ),
        AttributeOverride(
            name = "currencyCode",
            column = Column(name = "order_currency_code")
        )
    )
    var totalAmount: Money = totalAmount

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status")
    var status: OrderStatus = OrderStatus.PENDING

    val orders: List<Order>
        get() = _orders.toList()
}

enum class OrderStatus() {
    PENDING,
    CONFIRMED,
    PARTIAL_CANCELLED,
    CANCELLED
}