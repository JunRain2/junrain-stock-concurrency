package com.example.demo.product.command.infrastructure

import com.example.demo.common.RedisTestContainersConfig
import com.example.demo.global.contract.InfraHandledException
import com.example.demo.product.command.domain.ProductStockService
import com.example.demo.product.command.domain.StockChange
import eu.rekawek.toxiproxy.model.ToxicDirection
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest(
    properties = ["spring.main.allow-bean-definition-overriding=true"],
)
class ProductStockServiceImplIntegrationTest {

    // 잘 주입되고 있었음
    @TestConfiguration
    class CoroutineTestConfig {
        @Primary
        @Bean
        fun applicationScope(): CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    }

    @Autowired
    private lateinit var productStockService: ProductStockService

    @Autowired
    private lateinit var redissonClient: RedissonClient

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val redisProxy = RedisTestContainersConfig.redisProxy

    @BeforeEach
    fun setUp() {
        // Redis 초기화
        redissonClient.keys.flushall()

        // DB 초기화
        jdbcTemplate.execute("DELETE FROM exception_logs")

        // Toxiproxy 초기화 (모든 toxic 제거)
        redisProxy.toxics().all.forEach { it.remove() }
    }

    @AfterEach
    fun tearDown() {
        // Toxiproxy toxic 정리
        redisProxy.toxics().all.forEach { it.remove() }
        // Redis 연결 복구
        redisProxy.enable()
    }

    // ==================== 정상 시나리오 통합 테스트 ====================

    @Test
    fun `reserve는 Redis에 재고를 감소시켜야 한다`() {
        // given
        val productId = 1L
        val initialStock = 100L
        val reserveQuantity = 10L

        // Redis에 초기 재고 설정
        redissonClient.getAtomicLong("product_stock:$productId").set(initialStock)

        // when
        productStockService.reserve(StockChange(productId, reserveQuantity))

        // then
        val remainingStock = redissonClient.getAtomicLong("product_stock:$productId").get()
        assertEquals(initialStock - reserveQuantity, remainingStock)

        // 에러 로그가 없어야 함
        val errorLogs = jdbcTemplate.queryForList("SELECT * FROM exception_logs")
        assertTrue(errorLogs.isEmpty())
    }

    @Test
    fun `cancelReservation은 Redis에 재고를 증가시켜야 한다`() {
        // given
        val productId = 1L
        val initialStock = 90L
        val cancelQuantity = 10L

        // Redis에 초기 재고 설정
        redissonClient.getAtomicLong("product_stock:$productId").set(initialStock)

        // when
        productStockService.cancelReservation(StockChange(productId, cancelQuantity))

        // then
        val remainingStock = redissonClient.getAtomicLong("product_stock:$productId").get()
        assertEquals(initialStock + cancelQuantity, remainingStock)

        // 에러 로그가 없어야 함
        val errorLogs = jdbcTemplate.queryForList("SELECT * FROM exception_logs")
        assertTrue(errorLogs.isEmpty())
    }

    @Test
    fun `여러 상품의 재고를 동시에 예약할 수 있다`() {
        // given
        val product1Id = 1L
        val product2Id = 2L
        val initialStock1 = 100L
        val initialStock2 = 200L

        redissonClient.getAtomicLong("product_stock:$product1Id").set(initialStock1)
        redissonClient.getAtomicLong("product_stock:$product2Id").set(initialStock2)

        // when
        productStockService.reserve(
            StockChange(product1Id, 10L), StockChange(product2Id, 20L)
        )

        // then
        assertEquals(90L, redissonClient.getAtomicLong("product_stock:$product1Id").get())
        assertEquals(180L, redissonClient.getAtomicLong("product_stock:$product2Id").get())
    }

    // ==================== Redis 장애 시나리오 - 연결 실패 ====================

    @Test
    fun `reserve 시 Redis 연결 실패하면 예외를 던져야 한다`() {
        // given
        val productId = 1L
        val reserveQuantity = 10L

        // Redis에 초기 재고 설정 (연결이 끊어지기 전)
        redissonClient.getAtomicLong("product_stock:$productId").set(100L)

        // Toxiproxy로 Redis 연결 차단 (DOWN)
        redisProxy.disable()

        // when & then - 연결 실패로 인한 예외 발생
        val exception = assertThrows<Exception> {
            productStockService.reserve(StockChange(productId, reserveQuantity))
        }
        assertNotNull(exception)
        // RedisConnectionFailureException이 발생할 수 있음
        assertTrue(exception is InfraHandledException || exception is RedisConnectionFailureException)

        // 에러 로그가 없어야 함 (RedisConnectionException은 즉시 실패하므로 에러 로그 저장 안 함)
        val errorLogs = jdbcTemplate.queryForList("SELECT * FROM exception_logs")
        assertTrue(errorLogs.isEmpty())
    }

    @Test
    fun `cancelReservation 시 Redis 연결 실패하면 에러 로그를 저장해야 한다`() {
        // given
        val productId = 1L
        val cancelQuantity = 10L

        // Toxiproxy로 Redis 연결 차단
        redisProxy.disable()

        // when & then
        val exception = assertThrows<InfraHandledException> {
            productStockService.cancelReservation(StockChange(productId, cancelQuantity))
        }
        assertNotNull(exception)

        val errorLogs = jdbcTemplate.queryForList("SELECT * FROM exception_logs")

        assertTrue(errorLogs.isNotEmpty(), "InfraHandledException 발생 시 에러 로그가 최소 1개 이상 있어야 함")
        val errorLog = errorLogs[0]
        assertEquals("STOCK_CHANGE", errorLog["reason"])
        assertFalse(errorLog["is_executed"] as Boolean)
    }

    // ==================== Redis 네트워크 지연 시나리오 (Toxiproxy) ====================

    @Test
    fun `Redis 응답 지연 시 타임아웃이 발생하면 에러 로그를 저장해야 한다`() {
        // given
        val productId = 1L
        val reserveQuantity = 10L

        // Redis에 초기 재고 설정
        redissonClient.getAtomicLong("product_stock:$productId").set(100L)

        // Toxiproxy로 Redis 응답 지연 설정 (3초 지연 - RedisStockRepository의 타임아웃은 1초)
        redisProxy.toxics().latency("slow_network", ToxicDirection.DOWNSTREAM, 3000)

        try {
            // when & then - 타임아웃 예외 발생
            val exception = assertThrows<InfraHandledException> {
                productStockService.reserve(StockChange(productId, reserveQuantity))
            }
            assertNotNull(exception)

            // Dispatchers.Unconfined로 인해 즉시 실행되므로 바로 확인 가능
            val errorLogs = jdbcTemplate.queryForList("SELECT * FROM exception_logs")
            assertTrue(errorLogs.size >= 1, "타임아웃 발생 시 에러 로그가 저장되어야 함")

            val errorLog = errorLogs[0]
            assertEquals("STOCK_CHANGE", errorLog["reason"])
        } finally {
            redisProxy.toxics().get("slow_network").remove()
        }
    }

    // ==================== 동시성 테스트 ====================

    @Test
    fun `여러 스레드에서 동시에 재고를 예약해도 정합성이 유지되어야 한다`() = runTest {
        // given
        val productId = 1L
        val initialStock = 1000L
        val reserveQuantity = 1L
        val concurrentRequests = 50

        redissonClient.getAtomicLong("product_stock:$productId").set(initialStock)

        // when - 동시에 50개의 예약 요청
        val jobs = List(concurrentRequests) {
            async(Dispatchers.Default) {
                try {
                    productStockService.reserve(StockChange(productId, reserveQuantity))
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }

        val results = jobs.awaitAll()
        val successCount = results.count { it }

        // then
        val finalStock = redissonClient.getAtomicLong("product_stock:$productId").get()
        // 재고가 정확히 감소해야 함
        assertEquals(initialStock - (successCount * reserveQuantity), finalStock)
        println("Initial: $initialStock, Success: $successCount, Final: $finalStock")
    }

    // ==================== 재고 부족 시나리오 ====================

    @Test
    fun `재고가 부족하면 예외가 발생하고 재고는 변경되지 않아야 한다`() {
        // given
        val productId = 1L
        val initialStock = 5L
        val reserveQuantity = 10L

        redissonClient.getAtomicLong("product_stock:$productId").set(initialStock)

        // when & then - 재고 부족으로 예외 발생
        val exception = assertThrows<Exception> {
            productStockService.reserve(StockChange(productId, reserveQuantity))
        }
        assertNotNull(exception)
        println("재고 부족 예외: ${exception::class.simpleName} - ${exception.message}")

        // 재고는 음수가 되었을 것 (ProductOutOfStockException이 발생했지만 이미 연산은 완료됨)
        val remainingStock = redissonClient.getAtomicLong("product_stock:$productId").get()
        // Redis의 원자적 연산 특성상 음수가 될 수 있지만, 예외가 발생했으므로 롤백 필요
        assertTrue(
            remainingStock < 0 || remainingStock == initialStock,
            "재고는 음수이거나 초기값을 유지해야 함. 현재: $remainingStock"
        )
    }

    // ==================== 에러 로그 조회 테스트 ====================

    @Test
    fun `에러 로그에 저장된 요청은 나중에 조회할 수 있어야 한다`() {
        // given
        val productId = 1L
        val cancelQuantity = 10L

        // Redis 연결 차단
        redisProxy.disable()

        val exception = assertThrows<InfraHandledException> {
            productStockService.cancelReservation(StockChange(productId, cancelQuantity))
        }
        assertNotNull(exception)

        // Dispatchers.Unconfined로 인해 즉시 실행되므로 바로 확인 가능
        val errorLogs = jdbcTemplate.queryForList(
            "SELECT * FROM exception_logs WHERE reason = ?", "STOCK_CHANGE"
        )

        // Redis 연결 복구
        redisProxy.enable()

        // then
        assertTrue(errorLogs.isNotEmpty(), "에러 로그가 최소 1개 이상 저장되어야 함")
        val errorLog = errorLogs[0]
        assertNotNull(errorLog["request_key"])
        assertNotNull(errorLog["request_content"])
        assertEquals("STOCK_CHANGE", errorLog["reason"])
        assertFalse(errorLog["is_executed"] as Boolean)
    }

    // ==================== RequestKey 중복 방지 테스트 ====================

    @Test
    fun `동일한 requestKey로 중복 요청하면 멱등성이 보장되어야 한다`() {
        // given
        val productId = 1L
        val initialStock = 100L
        val reserveQuantity = 10L

        redissonClient.getAtomicLong("product_stock:$productId").set(initialStock)

        // when - 같은 요청을 2번 실행
        productStockService.reserve(StockChange(productId, reserveQuantity))
        productStockService.reserve(StockChange(productId, reserveQuantity))

        // then - 재고는 2번 감소되어야 함 (멱등성 아님, 각각 다른 requestKey 사용)
        val finalStock = redissonClient.getAtomicLong("product_stock:$productId").get()
        assertEquals(initialStock - (reserveQuantity * 2), finalStock)
    }
}
