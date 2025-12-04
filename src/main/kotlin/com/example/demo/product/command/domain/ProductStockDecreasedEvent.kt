package com.example.demo.product.command.domain


data class ProductStockDecreasedEvent(
    val event: List<ProductStock>
) {
    data class ProductStock(
        val productId: Long,
        val stock: Long
    )
}