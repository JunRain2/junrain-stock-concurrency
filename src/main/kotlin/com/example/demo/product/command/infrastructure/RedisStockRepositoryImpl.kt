package com.example.demo.product.command.infrastructure

import com.example.demo.product.command.domain.StockItem
import com.example.demo.product.command.domain.StockRepository
import org.redisson.api.BatchOptions
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Repository
import java.util.concurrent.TimeUnit

@Repository
class RedisStockRepositoryImpl(
    private val redisson: RedissonClient
) : StockRepository {
    override fun updateStocks(vararg stockItems: StockItem): List<Long> {
        val batch = redisson.createBatch(
            BatchOptions.defaults()
                // 순차적으로 실행 원자성 보장 X
                .executionMode(BatchOptions.ExecutionMode.IN_MEMORY)
                .responseTimeout(3, TimeUnit.SECONDS)
        )

        // 데드락 방지를 위해 정렬
        stockItems.sortedBy { it.productId }.forEach {
            batch.getAtomicLong(generateKey(it.productId)).addAndGetAsync(it.quantity)
        }

        return batch.execute().responses.map { it as Long }
    }

    private fun generateKey(id: Long) = "product:$id"
}