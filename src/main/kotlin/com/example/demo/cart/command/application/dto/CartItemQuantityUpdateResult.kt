package com.example.demo.cart.command.application.dto

data class CartItemQuantityUpdateResult(
    val cartItemId: Long,
    val quantity: Long,
)
