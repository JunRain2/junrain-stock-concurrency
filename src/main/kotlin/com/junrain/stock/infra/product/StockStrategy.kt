package com.junrain.stock.infra.product

/**
 * 예약 경로가 쓸 재고 차감 전략. `stock.strategy` 속성 하나로 고른다.
 *
 * 이 값은 구현체 선택만 정하는 것이 아니라 초기 재고를 어느 저장소에 심을지도 정한다.
 * 둘이 어긋나면 예약이 전부 재고 없음으로 실패하므로, 분기 지점을 열거형으로 묶어
 * 새 전략이 늘 때 컴파일러가 빠진 곳을 잡게 한다.
 */
enum class StockStrategy {
    /** Redis Lua 원자 차감. 재고는 `product_stock:{id}` 키에 있다. */
    REDIS,

    /** stock_items 낱개 행 + SELECT ... FOR UPDATE SKIP LOCKED. */
    SKIP_LOCKED,

    /** products.stock 단일 CASE UPDATE(비관적 락). */
    SINGLE_UPDATE,
    ;

    companion object {
        /** [org.springframework.boot.autoconfigure.condition.ConditionalOnProperty]에 그대로 넣는다. */
        const val PROPERTY = "stock.strategy"

        /** 어노테이션 인자는 상수여야 하므로 [org.springframework.beans.factory.annotation.Value]용 자리표시자도 상수로 둔다. */
        const val PLACEHOLDER = "\${stock.strategy}"
    }
}
