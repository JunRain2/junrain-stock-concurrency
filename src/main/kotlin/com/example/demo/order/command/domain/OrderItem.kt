package com.example.demo.order.command.domain

import com.example.demo.global.contract.BaseEntity
import com.example.demo.global.contract.vo.Money
import jakarta.persistence.*

@Entity
@Table(name = "order_items")
class OrderItem(
    @Column(name = "product_id")
    val productId: Long,
    @Column(name = "product_quantity")
    val quantity: Long,
    @Column(name = "total_amounts")
    @Embedded
    val totalAmounts: Money
) : BaseEntity() {
    @Enumerated(EnumType.STRING)
    @Column(name = "order_item_state")
    var status: OrderItemState = OrderItemState.WAITING
        private set
}

enum class OrderItemState() {
    WAITING
}