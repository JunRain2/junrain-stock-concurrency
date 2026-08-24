package com.junrain.stock.support

import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component

/**
 * 확정 재고를 읽는 테스트 전용 프로브.
 *
 * 통합 테스트가 실제 저장소를 확인하는 건 정상이다. 다만 저장소를 아는 지점을 여기 한 곳으로 모아,
 * 차감 전략이 바뀌어도 테스트 본문은 그대로 두게 한다.
 */
@Component
class StockProbe(
    private val redissonClient: RedissonClient,
) {
    fun stockOf(productId: Long): Long =
        redissonClient
            .getAtomicLong("product_stock:$productId")
            .takeIf { it.isExists }
            ?.get()
            ?: error("재고가 등록되지 않았습니다 : $productId")
}
