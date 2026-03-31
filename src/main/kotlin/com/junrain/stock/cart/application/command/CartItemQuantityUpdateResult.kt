package com.junrain.stock.cart.application.command

data class CartItemQuantityUpdateResult(
    val cartItemId: Long,
    val quantity: Long,
)
