package com.junrain.stock.cart.command.application

import com.junrain.stock.cart.command.application.dto.CartItemQuantityUpdateCommand
import com.junrain.stock.cart.command.application.dto.CartItemQuantityUpdateResult
import com.junrain.stock.cart.command.domain.CartItemRepository
import com.junrain.stock.cart.command.domain.StockAvailabilityService
import com.junrain.stock.cart.exception.CartItemNotFoundException
import org.springframework.stereotype.Service

@Service
class CartItemQuantityUpdateService(
    private val cartItemRepository: CartItemRepository,
    private val stockAvailabilityService: StockAvailabilityService
) {
    fun updateQuantity(command: CartItemQuantityUpdateCommand): CartItemQuantityUpdateResult {
        val cartItem = cartItemRepository.findById(command.cartItemId)
            .orElseThrow { CartItemNotFoundException() }
        cartItem.updateQuantity(command.quantity)

        stockAvailabilityService.validateProductStock(cartItem)

        cartItemRepository.save(cartItem)

        return CartItemQuantityUpdateResult(
            cartItemId = cartItem.id,
            quantity = cartItem.quantity
        )
    }
}