package com.example.demo.product.application

import com.example.demo.common.IntegrationTestBase
import com.example.demo.product.command.domain.StockRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import kotlin.test.assertEquals

class RedisStockInitializationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var stockRepository: StockRepository

    @Autowired(required = false)
    private var redisTemplate: StringRedisTemplate? = null

    @Test
    fun `Redis 재고 초기화 테스트`() {
        // given
        val productId = 999L
        val initialStock = 100L

        println("=== Redis Stock Initialization Test ===")
        println("StockRepository class: ${stockRepository.javaClass.simpleName}")
        println("RedisTemplate available: ${redisTemplate != null}")

        // when: setStock으로 초기화
        stockRepository.setStock(productId, initialStock)

        // then: Redis에서 직접 확인
        val redisValue = redisTemplate?.opsForValue()?.get("product:$productId")
        println("Redis value after setStock: $redisValue")
        assertEquals("100", redisValue, "Redis에 100이 저장되어야 합니다")

        // when: decreaseStock 실행
        stockRepository.decreaseStock(productId, 10)

        // then: 90이 되어야 함
        val afterDecrease = redisTemplate?.opsForValue()?.get("product:$productId")
        println("Redis value after decrease: $afterDecrease")
        assertEquals("90", afterDecrease, "Redis에 90이 남아있어야 합니다")

        // when: increaseStock 실행
        stockRepository.increaseStock(productId, 5)

        // then: 95가 되어야 함
        val afterIncrease = redisTemplate?.opsForValue()?.get("product:$productId")
        println("Redis value after increase: $afterIncrease")
        assertEquals("95", afterIncrease, "Redis에 95가 되어야 합니다")

        println("=== Test Passed ===")
    }
}
