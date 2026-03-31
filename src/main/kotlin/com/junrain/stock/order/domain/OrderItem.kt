package com.junrain.stock.order.domain

import com.junrain.stock.common.domain.BaseEntity
import com.junrain.stock.common.domain.Money
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
    val price: Money,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    val order: Order,
    @Column(name = "seller_id")
    val sellerId: Long,
) : BaseEntity() {
    @Enumerated(EnumType.STRING)
    @Column(name = "order_item_state")
    var status: OrderItemState = OrderItemState.WAITING
        private set
}

enum class OrderItemState {
    WAITING,
}
