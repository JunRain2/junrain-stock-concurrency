package com.example.demo.product.infrastructure.redis

import com.example.demo.common.RedisTestContainersConfig.Companion.redisProxy
import eu.rekawek.toxiproxy.model.ToxicDirection
import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import org.redisson.api.BatchOptions
import org.redisson.api.RedissonClient
import org.redisson.client.RedisConnectionException
import org.redisson.client.RedisException
import org.redisson.client.RedisResponseTimeoutException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

private val logger = KotlinLogging.logger { }

@SpringBootTest
class RedissonTest @Autowired constructor(
    private val redissonClient: RedissonClient,
) {
    @BeforeEach
    fun beforeEach() {
        redissonClient.keys.flushdb()
    }

    @Test
    fun `Redis 파이프라인 도중에 예외가 발생하는 경우`() {
        val key = "product:1"
        redissonClient.getBucket<String>(key).set("dump")

        val batch = redissonClient.createBatch(
            BatchOptions.defaults().executionMode(BatchOptions.ExecutionMode.IN_MEMORY)
                .responseTimeout(3, TimeUnit.SECONDS)
        )

        batch.getAtomicLong(key).addAndGetAsync(1)

        val result = assertThrows<RedisException> { batch.execute() }
        logger.info { "error message : ${result.message}" }
    }

    @Test
    fun `다수의 잘못된 형식의 데이터를 저장하는 경우`() {
        val keys = mutableListOf<String>()

        for (i in 1..5) {
            val key = "product:$i"

            keys.add(key)
            redissonClient.getBucket<String>(key).set("dump$i")
        }

        val batch = redissonClient.createBatch(
            BatchOptions.defaults().executionMode(BatchOptions.ExecutionMode.IN_MEMORY)
                .responseTimeout(3, TimeUnit.SECONDS)
        )

        for (key in keys) {
            batch.getAtomicLong(key).addAndGetAsync(1L)
        }

        val result = assertThrows<RedisException> { batch.execute() }
        // error message는 최초 하나의 데이터에 대한 예외만 터트림
        logger.info { "error message : ${result.message}" }
    }

    @Test
    fun `Redis 파이프라인 도중에 예외가 발생하는 경우 일부 값은 잘 저장된다`() {
        val key1 = "product:1"
        val key2 = "product:2"
        val key2Value = 1L

        redissonClient.getBucket<String>(key1).set("dump")

        val batch = redissonClient.createBatch(
            BatchOptions.defaults().executionMode(BatchOptions.ExecutionMode.IN_MEMORY)
                .responseTimeout(3, TimeUnit.SECONDS)
        )

        // 순서 상관 없음
        batch.getAtomicLong(key1).addAndGetAsync(key2Value)
        batch.getAtomicLong(key2).addAndGetAsync(1)

        // 무슨 에러인지 터트려봐야지
        val error = assertThrows<RedisException> { batch.execute() }
        logger.info { "error type : ${error.javaClass.simpleName}" }
        val result = redissonClient.getAtomicLong(key2).get()

        logger.info { "$key2 : $result" }
        assertEquals(key2Value, result)
    }

    @Test
    fun `batch() 중 네트워크 Timeout 장애가 발생했을 경우`() {
        val key = "product:1"

        val batch = redissonClient.createBatch(
            BatchOptions.defaults().executionMode(BatchOptions.ExecutionMode.IN_MEMORY)
                .responseTimeout(1, TimeUnit.SECONDS)
        )
        batch.getAtomicLong(key).addAndGetAsync(1L)

        // DOWNSTREAM -> 응답 자체가 안옴
        val toxi = redisProxy.toxics().timeout("timeout", ToxicDirection.DOWNSTREAM, 0)

        val error = assertThrows<RedisResponseTimeoutException> { batch.execute() }
        logger.info { "error message: ${error.message}" }

        toxi.remove()

        val result = redissonClient.getAtomicLong(key).get()
        // 1회 + 재시도 기본값 4회 = 5회
        assertEquals(5L, result)
    }

    @Test
    fun `batch() 중 네트워크 connection 장애가 발생했을 경우`() {
        val key = "product:1"

        val batch = redissonClient.createBatch(
            BatchOptions.defaults().executionMode(BatchOptions.ExecutionMode.IN_MEMORY)
                .responseTimeout(1, TimeUnit.SECONDS)
        )
        batch.getAtomicLong(key).addAndGetAsync(1L)

        // 서버 자체가 닫힌 경우 -> 프록시를 닫음
        redisProxy.disable()

        val error = assertThrows<RedisConnectionException> { batch.execute() }
        logger.info { "error message: ${error.message}" }

        redisProxy.enable()

        val result = redissonClient.getAtomicLong(key).get()
        assertEquals(0L, result)
    }

    @Test
    fun `RedissonAtomicLong은 key가 없는 경우 0을 반환`() {
        val randomKey = run {
            val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9') // 영문 대소문자 및 숫자
            List(10) { allowedChars.random() }.joinToString("")
        }

        assertEquals(0, redissonClient.getAtomicLong(randomKey).get())
    }

    @Test
    fun `RedisTimeoutException이 터졌지만 Redis에 데이터가 반영이 안된 경우`() {
        val key = "product:1"
        val value = 10L

        val toxi = redisProxy.toxics().timeout("cut", ToxicDirection.UPSTREAM, 0)

        val error = assertThrows<RedisResponseTimeoutException> {
            redissonClient.getAtomicLong(key).set(value)
        }
        logger.info { "error message: ${error.message}" }

        toxi.remove()

        // 데이터가 반영이 안돼있어야 함
        assertEquals(0, redissonClient.getAtomicLong(key).get())
    }

    @Test
    fun `setIfAbsent()는 Redis의 NX 옵션을 사용한다`() {
        val key = "product:1"
        val value = 10L
        val value2 = 20L

        redissonClient.getBucket<Long>(key).set(value)
        // 새로운 값을 세팅
        val result = redissonClient.getBucket<Long>(key).setIfAbsent(value2)
        assertFalse(result)

        val redisValue = redissonClient.getBucket<Long>(key).get()
        assertNotEquals(redisValue, value2)
        assertEquals(redisValue, value)
    }

    @Test
    fun `Buket Long 으로 저장하면 AtomicLong을 활용할 수 없다`() {
        // 둘은 저장하는 방식이 다르기 때문에 활용할 수 없음
        // Buket은 자바의 객체를 직렬화하지만 AtomicLong은 직렬화 오버헤드 없이 숫자를 문자열로 저장
        val key = "product:1"
        redissonClient.getBucket<Long>(key).set(10)

        assertThrows<RedisException> {
            redissonClient.getAtomicLong(key).get()
        }
    }
}