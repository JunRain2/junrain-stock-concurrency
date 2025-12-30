package com.junrain.stock.cart.command.infrastructure

import com.junrain.stock.cart.command.domain.CartItem
import com.junrain.stock.cart.command.domain.StockAvailabilityService
import com.junrain.stock.product.exception.ProductOutOfStockException
import com.junrain.stock.product.command.application.ProductStockAvailabilityService
import org.springframework.stereotype.Service

@Service
class StockAvailabilityServiceImpl(
    private val productStockAvailabilityService: ProductStockAvailabilityService
) : StockAvailabilityService {
    override fun validateProductStock(cartItem: CartItem) {
        if (!productStockAvailabilityService.hasEnoughStock(
                productId = cartItem.productId,
                requiredQuantity = cartItem.quantity
            )
        ) throw ProductOutOfStockException()
    }
}