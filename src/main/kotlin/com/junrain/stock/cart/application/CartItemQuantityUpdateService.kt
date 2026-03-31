package com.junrain.stock.cart.application

import com.junrain.stock.cart.application.command.CartItemQuantityUpdateCommand
import com.junrain.stock.cart.application.command.CartItemQuantityUpdateResult
import com.junrain.stock.cart.domain.CartItemRepository
import com.junrain.stock.cart.domain.StockAvailabilityService
import com.junrain.stock.cart.domain.exception.CartItemNotFoundException
import org.springframework.stereotype.Service

@Service
class CartItemQuantityUpdateService(
    private val cartItemRepository: CartItemRepository,
    private val stockAvailabilityService: StockAvailabilityService,
) {
    fun updateQuantity(command: CartItemQuantityUpdateCommand): CartItemQuantityUpdateResult {
        val cartItem =
            cartItemRepository
                .findById(command.cartItemId)
                .orElseThrow { CartItemNotFoundException() }
        cartItem.updateQuantity(command.quantity)

        stockAvailabilityService.validateProductStock(cartItem)

        cartItemRepository.save(cartItem)

        return CartItemQuantityUpdateResult(
            cartItemId = cartItem.id,
            quantity = cartItem.quantity,
        )
    }
}
