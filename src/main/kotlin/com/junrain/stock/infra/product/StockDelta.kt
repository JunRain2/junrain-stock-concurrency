package com.junrain.stock.infra.product

/**
 * 재고 증감 단위. 감소는 음수.
 */
data class StockDelta(
    val productId: Long,
    val quantity: Long,
)
