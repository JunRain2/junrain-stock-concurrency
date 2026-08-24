package com.junrain.stock.infra.product

/**
 * 재고 증감 단위. 감소는 음수.
 *
 * Redis 실패 시 error_log에 JSON으로 저장되어 배치가 재시도한다.
 */
data class StockDelta(
    val productId: Long,
    val quantity: Long,
)
