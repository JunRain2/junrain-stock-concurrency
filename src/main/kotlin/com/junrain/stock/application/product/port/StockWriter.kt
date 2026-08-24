package com.junrain.stock.application.product.port

/**
 * 확정 재고 포트. 저장소(MySQL/Redis)는 구현체가 정한다.
 */
interface StockWriter {
    /**
     * 여러 상품의 재고를 한 번에 감소시킨다. 하나라도 재고가 모자라면 전부 감소되지 않는다.
     *
     * @throws IllegalArgumentException 같은 상품이 여러 번 들어온 경우
     * @throws com.junrain.stock.domain.product.exception.StockUnavailableException 재고가 모자라거나 없는 상품이 있는 경우 (재시도 불가)
     * @throws com.junrain.stock.domain.product.exception.StockUnstableException 락 충돌 등 인프라 문제로 실패한 경우 (재시도 가능)
     */
    fun decrease(changes: List<StockChange>)

    /**
     * 여러 상품의 재고를 한 번에 증가시킨다.
     *
     * @throws IllegalArgumentException 같은 상품이 여러 번 들어온 경우
     * @throws com.junrain.stock.domain.product.exception.ProductNotFoundException 없는 상품이 있는 경우
     * @throws com.junrain.stock.domain.product.exception.StockUnstableException 락 충돌 등 인프라 문제로 실패한 경우 (재시도 가능)
     */
    fun increase(changes: List<StockChange>)

    data class StockChange(
        val productId: Long,
        val quantity: Long,
    ) {
        init {
            require(quantity > 0) { "재고 변경 수량은 1 이상이어야 합니다." }
        }
    }
}