package com.junrain.stock.application.product.port

import java.time.LocalDateTime

/**
 * 예약 가능한 재고 포트. 남은 수량이 곧 아직 점유할 수 있는 양이고, 저장소는 구현체가 정한다.
 */
interface StockWriter {
    /**
     * 여러 상품의 재고를 한 번에 점유한다. 하나라도 재고가 모자라면 전부 감소되지 않는다.
     *
     * 차감과 함께 [expireAt]에 만료되는 점유 기록을 남긴다. 이 기록이 남았다는 것이 곧 차감이
     * 적용됐다는 증거이고, 만료되면 회수된다. 그래서 호출자는 결과를 못 받아도 사후에
     * "적용됐나"를 확인할 필요가 없다 - 확인하든 말든 만료 시점의 처리가 같다.
     *
     * [trxId]와 [expireAt] 모두 호출자가 만들어 넘긴다. 같은 [trxId]로 다시 부르면 조용히 통과한다(멱등).
     * 만료 시각을 구현체가 자기 시계로 다시 찍으면 같은 예약의 만료 시각이 저장소마다 갈린다.
     *
     * @throws IllegalArgumentException 같은 상품이 여러 번 들어온 경우
     * @throws com.junrain.stock.domain.product.exception.StockUnavailableException
     *   재고가 모자라거나 없는 상품이 있는 경우 (재시도 불가)
     * @throws com.junrain.stock.domain.product.exception.StockUnstableException
     *   응답을 못 받아 적용 여부를 모르는 경우 (재시도 가능). 적용됐다면 만료까지 언더셀로 남는다
     */
    fun reserve(
        trxId: String,
        changes: List<StockChange>,
        expireAt: LocalDateTime,
    )

    data class StockChange(
        val productId: Long,
        val quantity: Long,
    ) {
        init {
            require(quantity > 0) { "재고 변경 수량은 1 이상이어야 합니다." }
        }
    }
}
