package com.example.demo.product.command.infrastructure

import com.example.demo.product.command.domain.StockRepository
import com.example.demo.product.exception.ProductOutOfStockException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository

@Repository
class RedisStockRepositoryImpl(
    private val redisTemplate: StringRedisTemplate
) : StockRepository {
    companion object {
        const val PRODUCT_KEY_PREFIX = "product:"
    }

    override fun decreaseStock(
        productId: Long,
        quantity: Long
    ) {
        val result = redisTemplate.opsForValue().decrement(generateKey(productId), quantity)
        if (result == null || result < 0) {
            throw ProductOutOfStockException()
        }
    }

    private fun generateKey(id: Long): String {
        return PRODUCT_KEY_PREFIX + id
    }

    override fun increaseStock(
        productId: Long,
        quantity: Long
    ) {
        redisTemplate.opsForValue().increment(generateKey(productId), quantity)
    }

    override fun setStock(productId: Long, quantity: Long) {
        redisTemplate.opsForValue().set(generateKey(productId), quantity.toString())
    }
}