package com.example.demo.product.command.infrastructure

import com.example.demo.product.command.domain.StockItem
import com.example.demo.product.command.domain.StockRepository
import com.example.demo.product.exception.ProductOutOfStockException
import org.redisson.api.BatchOptions
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Repository
import java.util.concurrent.TimeUnit

@Repository
class RedisStockRepositoryImpl(
    private val redisson: RedissonClient
) : StockRepository {
    companion object {
        const val PRODUCT_KEY_PREFIX = "product:"
    }

    override fun decreaseStock(vararg productItems: StockItem) {
        val batch = redisson.createBatch(
            BatchOptions.defaults()
                // 순차적으로 실행 원자성 보장 X
                .executionMode(BatchOptions.ExecutionMode.IN_MEMORY)
                .responseTimeout(3, TimeUnit.SECONDS)
        )

        // 데드락 방지를 위해 정렬
        productItems.sortedBy { it.productId }.forEach {
            batch.getAtomicLong(generateKey(it.productId))
                .addAndGetAsync(-it.quantity)
        }

        val result = batch.execute().responses.map { it as Long }
        if (result.any { it < 0 }) throw ProductOutOfStockException()
    }

    private fun generateKey(id: Long): String {
        return PRODUCT_KEY_PREFIX + id
    }

    override fun increaseStock(vararg productItems: StockItem) {
        val batch = redisson.createBatch(
            BatchOptions.defaults()
                .executionMode(BatchOptions.ExecutionMode.IN_MEMORY)
                .responseTimeout(3, TimeUnit.SECONDS)
        )

        productItems.sortedBy { it.productId }.forEach {
            batch.getAtomicLong(generateKey(it.productId))
                .addAndGetAsync(it.quantity)
        }

        batch.execute()
    }
}