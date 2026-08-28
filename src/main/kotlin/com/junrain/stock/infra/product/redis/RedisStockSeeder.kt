package com.junrain.stock.infra.product.redis

import org.redisson.api.BatchOptions
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * 상품 등록 시 Redis에 초기 재고를 심는다.
 *
 * Redis 전략에서 `product_stock:{id}` 키가 곧 진실 원천이라, 키가 없는 상품은 존재하되 팔 수 없는 상품이 된다.
 *
 * 심기는 `SETNX`다. 이미 있는 키는 건드리지 않으므로 같은 상품을 다시 심어도 재고가 부풀지 않는다.
 * 실패했을 때 그냥 다시 부르면 되고, 그래서 실패를 따로 적어 두는 장치가 필요 없다.
 */
@Component
class RedisStockSeeder(
    private val redissonClient: RedissonClient,
    @param:Value("\${spring.data.redis.batch-size:500}") val maxSize: Int,
) {
    fun seed(
        productId: Long,
        quantity: Long,
    ) {
        redissonClient
            .getBucket<String>(stockKey(productId), StringCodec.INSTANCE)
            .setIfAbsent(quantity.toString())
    }

    /** 재고 키는 평문 정수 문자열이어야 한다. Lua 스크립트가 `GET`/`DECRBY`로 읽는다. */
    fun seedAll(quantityByProductId: Map<Long, Long>) {
        require(quantityByProductId.isNotEmpty()) { "심을 상품이 없습니다." }
        require(quantityByProductId.size <= maxSize) { "한 번에 심을 수 있는 상품은 $maxSize 개까지입니다." }

        val batch =
            redissonClient.createBatch(
                BatchOptions.defaults().executionMode(BatchOptions.ExecutionMode.IN_MEMORY),
            )

        quantityByProductId.forEach { (productId, quantity) ->
            batch.getBucket<String>(stockKey(productId), StringCodec.INSTANCE).setIfAbsentAsync(quantity.toString())
        }

        batch.execute()
    }

    private fun stockKey(productId: Long) = "product_stock:$productId"
}
