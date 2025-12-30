package com.junrain.stock.cart.command.domain

import com.junrain.stock.contract.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "cart_items")
class CartItem(
    @Column(name = "member_id")
    val memberId: Long,
    @Column(name = "product_id")
    val productId: Long,
    quantity: Long,
) : BaseEntity() {
    var quantity: Long = quantity
        private set

    init {
        require(quantity > 0) { "수량은 1개 이상이어야 합니다." }
    }

    private fun validateQuantity(quantity: Long) {
        require(quantity > 0) { "수량은 1개 이상이어야 합니다." }
    }

    fun updateQuantity(quantity: Long) {
        validateQuantity(quantity)
        this.quantity = quantity
    }
}