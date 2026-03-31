package com.junrain.stock.cart.application

import com.junrain.stock.cart.application.command.CartAddProductCommand
import com.junrain.stock.cart.application.command.CartAddProductResult
import com.junrain.stock.cart.domain.CartItem
import com.junrain.stock.cart.domain.CartItemRepository
import com.junrain.stock.cart.domain.StockAvailabilityService
import org.springframework.stereotype.Service

@Service
class CartAddProductService(
    private val cartItemRepository: CartItemRepository,
    private val stockAvailabilityService: StockAvailabilityService,
) {
    fun putProductInCart(command: CartAddProductCommand): CartAddProductResult {
        val cartItem =
            CartItem(
                memberId = command.memberId,
                productId = command.productId,
                quantity = command.quantity,
            ).let {
                stockAvailabilityService.validateProductStock(it)
                cartItemRepository.save(it)
            }

        return CartAddProductResult(cartItem.id)
    }
}
