package com.junrain.stock.cart.application.command

data class CartItemQuantityUpdateCommand(
    val cartItemId: Long,
    val quantity: Long,
) {
    init {
        require(quantity > 0) {}
    }
}
