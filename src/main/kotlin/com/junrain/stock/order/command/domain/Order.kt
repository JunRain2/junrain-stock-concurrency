package com.junrain.stock.order.command.domain

import com.junrain.stock.contract.entity.BaseEntity
import com.junrain.stock.contract.vo.Money
import com.junrain.stock.order.command.domain.vo.Orderer
import jakarta.persistence.*

@Entity
@Table(name = "orders")
class Order(
    @Embedded
    private val orderer: Orderer,
    @OneToMany
    val orderItems: List<OrderItem>,
    totalAmount: Money,
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
}

enum class OrderStatus() {
    PENDING,
    CONFIRMED,
    PARTIAL_CANCELLED,
    CANCELLED
}