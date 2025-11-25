package com.example.demo.cart.command.application

import com.example.demo.cart.command.application.dto.CartItemQuantityUpdateCommand
import com.example.demo.cart.command.application.dto.CartItemQuantityUpdateResult
import com.example.demo.cart.command.domain.CartItemRepository
import com.example.demo.cart.command.domain.StockAvailabilityService
import com.example.demo.global.contract.exception.NotFoundCartItemException
import org.springframework.stereotype.Service

@Service
class CartItemQuantityUpdateService(
    private val cartItemRepository: CartItemRepository,
    private val stockAvailabilityService: StockAvailabilityService
) {
    fun updateQuantity(command: CartItemQuantityUpdateCommand): CartItemQuantityUpdateResult {
        val cartItem = cartItemRepository.findById(command.cartItemId)
            .orElseThrow { NotFoundCartItemException() }
        cartItem.updateQuantity(command.quantity)

        stockAvailabilityService.validateProductStock(cartItem)

        cartItemRepository.save(cartItem)

        return CartItemQuantityUpdateResult(
            cartItemId = cartItem.id,
            quantity = cartItem.quantity
        )
    }
}