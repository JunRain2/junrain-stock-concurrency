package com.junrain.stock.config

import com.junrain.stock.infra.product.StockStrategy
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * 확정 재고를 읽는 테스트 전용 프로브.
 *
 * 통합 테스트가 실제 저장소를 확인하는 건 정상이다. 다만 저장소를 아는 지점을 여기 한 곳으로 모아,
 * 차감 전략이 바뀌어도 테스트 본문은 그대로 두게 한다. 읽을 저장소는 구현체를 고르는 것과 같은
 * `stock.strategy`가 정하므로 전략을 바꿀 때 여기를 손으로 맞출 일이 없다.
 */
@Component
class StockProbe(
    private val jdbcTemplate: JdbcTemplate,
    private val redissonClient: RedissonClient,
    @param:Value(StockStrategy.PLACEHOLDER) private val stockStrategy: StockStrategy,
) {
    fun stockOf(productId: Long): Long =
        when (stockStrategy) {
            StockStrategy.REDIS -> {
                redissonClient.getAtomicLong("product_stock:$productId").get()
            }

            StockStrategy.SKIP_LOCKED -> {
                jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM stock_items WHERE product_id = ? AND status = 'AVAILABLE'",
                    Long::class.java,
                    productId,
                ) ?: 0
            }

            StockStrategy.SINGLE_UPDATE -> {
                jdbcTemplate.queryForObject(
                    "SELECT stock FROM products WHERE id = ?",
                    Long::class.java,
                    productId,
                ) ?: 0
            }
        }
}
