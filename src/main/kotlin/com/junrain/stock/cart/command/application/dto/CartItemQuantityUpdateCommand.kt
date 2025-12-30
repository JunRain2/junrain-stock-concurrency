package com.junrain.stock.cart.command.application.dto

data class CartItemQuantityUpdateCommand(
    val cartItemId: Long,
    val quantity: Long
){
    init{
        require(quantity > 0) {}
    }
}
