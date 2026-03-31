package com.junrain.stock.cart.application.command

data class CartAddProductCommand(
    val productId: Long,
    val memberId: Long,
    val quantity: Long,
)
