package com.junrain.stock.product.command.infrastructure.redis

import com.junrain.stock.contract.exception.InfraException
import com.junrain.stock.config.jdbc.ErrorLogRepository
import com.junrain.stock.config.jdbc.ErrorLogType
import com.junrain.stock.product.command.domain.StockChange
import com.junrain.stock.product.exception.ProductOutOfStockException
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.redisson.api.BatchOptions
import org.redisson.api.RBatch
import org.redisson.api.RedissonClient
import org.redisson.client.RedisConnectionException
import org.redisson.client.RedisException
import org.redisson.client.RedisTimeoutException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger { }

@Component
class RedisStockRepository(
    private val redissonClient: RedissonClient,
    private val errorLogRepository: ErrorLogRepository,
    private val applicationScope: CoroutineScope,
    @param:Value("\${spring.data.redis.batch-size:500}") val maxSize: Int,
) {
    fun setStockIfAbsent(productId: Long, quantity: Long) {
        redissonClient.getAtomicLong(generateStockKey(productId)).takeUnless { it.isExists }
            ?.set(quantity)
    }

    private fun generateStockKey(id: Long) = "product_stock:$id"


    fun hasRequestKey(requestKey: String): Boolean {
        return redissonClient.getBucket<String>(requestKey).isExists
    }

    fun decreaseStock(requestKey: String, vararg stockChanges: StockChange) {
        validateStockChanges(stockChanges)
        val batch = generateBatch(requestKey)

        stockChanges.sortedBy { it.productId }.forEach {
            batch.getAtomicLong(generateStockKey(it.productId)).addAndGetAsync(-it.quantity)
        }

        try {
            batch.execute().responses.drop(1).map { it as Long }.apply {
                if (this.any { it < 0 }) throw ProductOutOfStockException()
            }
        } catch (e: RedisException) {
            when (e) {
                is RedisTimeoutException -> {
                    logger.error(e) { "에러 로그 저장 시작" }
                    applicationScope.launch {
                        saveRedisStockChangeException(requestKey, *stockChanges)
                    }
                    throw InfraException(e)
                }

                is RedisConnectionException -> {
                    logger.error(e) { "레디스 상태 확인 바람" }
                    throw InfraException(e)
                }
            }
            throw e
        }
    }

    private fun generateBatch(requestKey: String): RBatch {
        return redissonClient.createBatch(
            BatchOptions.defaults().executionMode(BatchOptions.ExecutionMode.IN_MEMORY)
                .responseTimeout(1, TimeUnit.SECONDS).retryAttempts(0) // 재시도 로직으로 인해 정합성이 지는 것을 방지
        ).apply {
            this.getBucket<String>(generateRequestKey(requestKey)).setAsync("1", 1, TimeUnit.DAYS)
        }
    }

    private fun generateRequestKey(id: String) = "request:$id"

    private fun validateStockChanges(stockChanges: Array<out StockChange>) {
        require(stockChanges.isNotEmpty()) { "stockChanges는 반드시 한 개 이상이어야 합니다." }
        require(stockChanges.size <= maxSize) { "stockChanges는 반드시 $maxSize 이하이어야 합니다." }
    }

    fun increaseStock(requestKey: String, vararg stockChanges: StockChange) {
        validateStockChanges(stockChanges)
        val batch = generateBatch(requestKey)

        stockChanges.sortedBy { it.productId }.forEach {
            batch.getAtomicLong(generateStockKey(it.productId)).addAndGetAsync(it.quantity)
        }

        try {
            batch.execute()
        } catch (e: RedisException) {
            when (e) {
                is RedisTimeoutException, is RedisConnectionException -> {
                    applicationScope.launch {
                        saveRedisStockChangeException(requestKey, *stockChanges)
                    }
                    throw InfraException(e)
                }
            }
            throw e
        }
    }

    private fun saveRedisStockChangeException(requestKey: String, vararg changes: StockChange) {
        errorLogRepository.saveErrorLog(
            requestKey = requestKey, reason = ErrorLogType.STOCK_CHANGE, content = changes.toList()
        )
    }
}