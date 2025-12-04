package com.example.demo.product.command.domain


data class FailedProductStockDecreasedEvent(
    val event: List<ProductStock>
) {
    data class ProductStock(
        val productId: Long,
        val stock: Long
    )
}