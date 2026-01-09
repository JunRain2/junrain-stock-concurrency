package com.junrain.stock.order.command.domain

import com.junrain.stock.contract.entity.BaseEntity
import com.junrain.stock.contract.vo.Money
import com.junrain.stock.order.command.domain.vo.OrderCode
import com.junrain.stock.order.command.domain.vo.Orderer
import jakarta.persistence.*

@Entity
@Table(
    name = "orders",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_order_code",
            columnNames = ["order_code"]  // OrderCode의 실제 컬럼명
        )
    ]
)
class Order(
    @Embedded private val orderer: Orderer,
    orderItems: List<OrderItem>,

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
    var totalAmount: Money = Money.of(orderItems.sumOf { it.totalAmounts.amount })

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status")
    var status: OrderStatus = OrderStatus.PENDING
        private set

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    private val _orderItems: MutableList<OrderItem> = orderItems.toMutableList()
    val orderItems: List<OrderItem> get() = _orderItems.toList()

    fun markAsPaid() {
        status = OrderStatus.PAID
    }
}

enum class OrderStatus() {
    PENDING, PAID, CONFIRMED, PARTIAL_CANCELLED, CANCELLED
}