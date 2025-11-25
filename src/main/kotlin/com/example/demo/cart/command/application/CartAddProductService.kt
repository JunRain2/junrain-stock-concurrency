package com.example.demo.cart.command.application

import com.example.demo.cart.command.application.dto.CartAddProductCommand
import com.example.demo.cart.command.application.dto.CartAddProductResult
import com.example.demo.cart.command.domain.CartItem
import com.example.demo.cart.command.domain.CartItemRepository
import com.example.demo.cart.command.domain.StockAvailabilityService
import org.springframework.stereotype.Service

@Service
class CartAddProductService(
    private val cartItemRepository: CartItemRepository,
    private val stockAvailabilityService: StockAvailabilityService
) {
    fun putProductInCart(command: CartAddProductCommand): CartAddProductResult {
        val cartItem = CartItem(
            memberId = command.memberId,
            productId = command.productId,
            quantity = command.quantity
        ).let {
            stockAvailabilityService.validateProductStock(it)
            cartItemRepository.save(it)
        }

        return CartAddProductResult(cartItem.id)
    }
}