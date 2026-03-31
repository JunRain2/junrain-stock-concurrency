package com.junrain.stock.cart.domain

interface StockAvailabilityService {
    fun validateProductStock(cartItem: CartItem)
}
