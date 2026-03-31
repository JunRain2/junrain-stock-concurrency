package com.junrain.stock.order.domain

import com.junrain.stock.common.domain.BaseEntity
import com.junrain.stock.common.domain.Money
import com.junrain.stock.order.domain.vo.OrderCode
import com.junrain.stock.order.domain.vo.Orderer
import jakarta.persistence.*

@Entity
@Table(
    name = "orders",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_order_code",
            columnNames = ["order_code"],
        ),
    ],
)
class Order(
    @Embedded
    private val orderer: Orderer,
    @Embedded
    val code: OrderCode,
    totalAmount: Money,
) : BaseEntity() {
    @Embedded
    @AttributeOverrides(
        AttributeOverride(
            name = "amount",
            column = Column(name = "order_total_amount"),
        ),
        AttributeOverride(
            name = "currencyCode",
            column = Column(name = "order_currency_code"),
        ),
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "order_status")
    var status: OrderStatus = OrderStatus.PENDING
        private set

    @Embedded
    var totalAmount: Money = totalAmount
        private set

    fun isPurchasable(): Boolean = status == OrderStatus.PENDING

    fun markAsPaid() {
        status = OrderStatus.PAID
    }
}

enum class OrderStatus {
    PENDING,
    PAID,
    CONFIRMED,
    PARTIAL_CANCELLED,
    CANCELLED,
}
