package com.junrain.stock.cart.command.application.dto

data class CartItemQuantityUpdateResult(
    val cartItemId: Long,
    val quantity: Long,
)
