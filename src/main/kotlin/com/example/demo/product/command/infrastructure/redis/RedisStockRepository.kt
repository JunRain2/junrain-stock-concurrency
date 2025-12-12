package com.example.demo.product.command.infrastructure.redis

import com.example.demo.product.command.domain.StockChange
import com.example.demo.product.exception.ProductOutOfStockException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.redisson.api.BatchOptions
import org.redisson.api.RBatch
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger { }

@Component
class RedisStockRepository(
    private val redissonClient: RedissonClient,
) {
    fun setStockIfAbsent(productId: Long, quantity: Long) {
        redissonClient.getAtomicLong(generateStockKey(productId))
            .takeUnless { it.isExists }
            ?.set(quantity)
    }

    private fun generateStockKey(id: Long) = "product_stock:$id"

    fun updateStocks(requestKey: String, vararg stockChanges: StockChange) {
        require(stockChanges.isNotEmpty()) { "stockChanges는 반드시 한 개 이상이어야 합니다." }
        val batch = generateBatch(requestKey)

        // 데드락 방지를 위해 정렬
        stockChanges.sortedBy { it.productId }.forEach {
            batch.getAtomicLong(generateStockKey(it.productId)).addAndGetAsync(it.quantity)
        }

        val result = batch.execute().responses.drop(1).map { it as Long }

        if (result.any { it < 0 }) throw ProductOutOfStockException()
    }

    private fun generateBatch(requestKey: String): RBatch {
        val batch = redissonClient.createBatch(
            BatchOptions.defaults()
                .executionMode(BatchOptions.ExecutionMode.IN_MEMORY)
                .responseTimeout(1, TimeUnit.SECONDS)
                .retryAttempts(0) // 재시도 로직으로 인해 정합성이 지는 것을 방지
        )

        batch.getBucket<String>(generateRequestKey(requestKey)).setAsync("1", 1, TimeUnit.DAYS)

        return batch
    }

    private fun generateRequestKey(id: String) = "request:$id"

    fun hasRequestKey(requestKey: String): Boolean {
        return redissonClient.getBucket<String>(requestKey).isExists
    }
}