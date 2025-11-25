package com.example.demo.cart.command.application.dto

data class CartAddProductCommand(
    val productId: Long,
    val memberId: Long,
    val quantity: Long
)