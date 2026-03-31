package com.junrain.stock.cart.infra

import com.junrain.stock.cart.domain.CartItem
import com.junrain.stock.cart.domain.StockAvailabilityService
import com.junrain.stock.product.application.ProductStockAvailabilityService
import com.junrain.stock.product.domain.exception.ProductOutOfStockException
import org.springframework.stereotype.Service

@Service
class StockAvailabilityServiceImpl(
    private val productStockAvailabilityService: ProductStockAvailabilityService,
) : StockAvailabilityService {
    override fun validateProductStock(cartItem: CartItem) {
        if (!productStockAvailabilityService.hasEnoughStock(
                productId = cartItem.productId,
                requiredQuantity = cartItem.quantity,
            )
        ) {
            throw ProductOutOfStockException()
        }
    }
}
