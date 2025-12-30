package com.junrain.stock.cart.command.domain

interface StockAvailabilityService {
    fun validateProductStock(cartItem: CartItem)
}