package com.junrain.stock.cart.command.application

import com.junrain.stock.cart.command.application.dto.CartAddProductCommand
import com.junrain.stock.cart.command.application.dto.CartAddProductResult
import com.junrain.stock.cart.command.domain.CartItem
import com.junrain.stock.cart.command.domain.CartItemRepository
import com.junrain.stock.cart.command.domain.StockAvailabilityService
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