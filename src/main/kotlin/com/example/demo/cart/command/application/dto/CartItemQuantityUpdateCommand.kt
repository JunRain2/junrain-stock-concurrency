package com.example.demo.cart.command.application.dto

data class CartItemQuantityUpdateCommand(
    val cartItemId: Long,
    val quantity: Long
){
    init{
        require(quantity > 0) {}
    }
}
