package com.example.demo.cart.command.domain

interface StockAvailabilityService {
    fun validateProductStock(cartItem: CartItem)
}