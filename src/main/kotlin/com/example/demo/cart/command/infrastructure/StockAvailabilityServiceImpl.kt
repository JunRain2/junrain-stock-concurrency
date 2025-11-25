package com.example.demo.cart.command.infrastructure

import com.example.demo.cart.command.domain.CartItem
import com.example.demo.cart.command.domain.StockAvailabilityService
import com.example.demo.global.contract.exception.ProductOutOfStockException
import com.example.demo.product.command.application.ProductStockAvailabilityService
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