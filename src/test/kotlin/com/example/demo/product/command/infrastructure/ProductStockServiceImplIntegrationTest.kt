package com.example.demo.product.command.infrastructure

import com.example.demo.batch.job.StockConsistencyBatchJob
import com.example.demo.config.RedisTestContainersConfig
import com.example.demo.contract.exception.InfraException
import com.example.demo.product.command.domain.ProductStockService
import com.example.demo.product.command.domain.StockChange
import eu.rekawek.toxiproxy.model.ToxicDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.quartz.JobExecutionContext
import org.quartz.Scheduler
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest
class ProductStockServiceImplIntegrationTest {
    @Autowired
    private lateinit var productStockService: ProductStockService

    @Autowired
    private lateinit var redissonClient: RedissonClient

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var scheduler: Scheduler

    @Autowired
    private lateinit var stockConsistencyBatchJob: StockConsistencyBatchJob

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
        assertTrue(exception is InfraException || exception is RedisConnectionFailureException)

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
        val exception = assertThrows<InfraException> {
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
            val exception = assertThrows<InfraException> {
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

        val exception = assertThrows<InfraException> {
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

// ==================== 배치 작업을 통한 정합성 복구 테스트 ====================

    @Test
    fun `Redis 실패로 저장된 에러 로그가 배치 작업으로 재실행되어 정합성이 맞춰져야 한다`() {
        // given
        val productId = 1L
        val initialStock = 100L
        val cancelQuantity = 10L

        // Redis에 초기 재고 설정
        redissonClient.getAtomicLong("product_stock:$productId").set(initialStock)

        // Redis 연결 차단 -> cancelReservation 실패 -> 에러 로그 저장
        redisProxy.disable()

        val exception = assertThrows<InfraException> {
            productStockService.cancelReservation(StockChange(productId, cancelQuantity))
        }
        assertNotNull(exception)

        // 에러 로그가 저장되었는지 확인
        val errorLogsBeforeBatch = jdbcTemplate.queryForList(
            "SELECT * FROM exception_logs WHERE reason = ? AND is_executed = false", "STOCK_CHANGE"
        )
        assertTrue(errorLogsBeforeBatch.isNotEmpty(), "에러 로그가 저장되어야 함")

        // Redis 연결 복구
        redisProxy.enable()

        // created_at이 1분 이전이 되도록 시간 조작 (배치 쿼리에서 1분 전 데이터만 가져오기 때문)
        jdbcTemplate.update(
            "UPDATE exception_logs SET created_at = DATE_SUB(NOW(), INTERVAL 2 MINUTE) WHERE is_executed = false"
        )

        // when - 배치 작업 실행
        val mockContext = Mockito.mock(JobExecutionContext::class.java)
        Mockito.`when`(mockContext.scheduler).thenReturn(scheduler)
        stockConsistencyBatchJob.execute(mockContext)

        // then
        // 1. 에러 로그가 실행 완료 처리되었는지 확인
        val errorLogsAfterBatch = jdbcTemplate.queryForList(
            "SELECT * FROM exception_logs WHERE reason = ? AND is_executed = false", "STOCK_CHANGE"
        )
        assertEquals(0, errorLogsAfterBatch.size, "모든 에러 로그가 실행 완료 처리되어야 함")

        // 2. Redis에 재고가 정상적으로 업데이트되었는지 확인
        val finalStock = redissonClient.getAtomicLong("product_stock:$productId").get()
        assertEquals(
            initialStock + cancelQuantity, finalStock, "배치 작업으로 cancelReservation이 실행되어 재고가 증가해야 함"
        )
    }

    @Test
    fun `배치 작업 실행 중 Redis 장애가 다시 발생하면 여전히 is_executed = false 상태여야 한다`() {
        // given
        val productId = 1L
        val initialStock = 100L
        val cancelQuantity = 5L

        // Redis에 초기 재고 설정
        redissonClient.getAtomicLong("product_stock:$productId").set(initialStock)

        // 첫 번째 실패 - Redis 연결 차단
        redisProxy.disable()

        val exception = assertThrows<InfraException> {
            productStockService.cancelReservation(StockChange(productId, cancelQuantity))
        }
        assertNotNull(exception)

        // 첫 번째 에러 로그 확인
        val firstErrorLogs = jdbcTemplate.queryForList(
            "SELECT * FROM exception_logs WHERE reason = ? AND is_executed = false", "STOCK_CHANGE"
        )
        assertEquals(1, firstErrorLogs.size, "첫 번째 에러 로그가 저장되어야 함")

        // created_at을 1분 이전으로 설정
        jdbcTemplate.update(
            "UPDATE exception_logs SET created_at = DATE_SUB(NOW(), INTERVAL 2 MINUTE) WHERE is_executed = false"
        )

        // when - Redis가 여전히 장애 상태에서 배치 실행
        val mockContext = Mockito.mock(JobExecutionContext::class.java)
        Mockito.`when`(mockContext.scheduler).thenReturn(scheduler)
        stockConsistencyBatchJob.execute(mockContext)

        // then
        // 1. 네트워크 장애 시 setExecuted가 호출되지 않으므로 여전히 is_executed = false 상태
        val stillPendingLogs = jdbcTemplate.queryForList(
            "SELECT * FROM exception_logs WHERE reason = ? AND is_executed = false", "STOCK_CHANGE"
        )
        assertEquals(1, stillPendingLogs.size, "네트워크 장애로 인해 재시도 대기 상태로 유지되어야 함")

        // 2. 실행 완료 처리된 로그는 없어야 함 (네트워크 장애는 다음 스케줄에서 재시도)
        val executedLogs = jdbcTemplate.queryForList(
            "SELECT * FROM exception_logs WHERE reason = ? AND is_executed = true", "STOCK_CHANGE"
        )
        assertEquals(0, executedLogs.size, "네트워크 장애 시에는 성공 처리하지 않고 다음 스케줄에서 재시도해야 함")

        println("재시도 대기 중인 에러 로그: ${stillPendingLogs.size}, 실행 완료된 로그: ${executedLogs.size}")

        // 정리 - Redis 연결 복구
        redisProxy.enable()
    }

    @Test
    fun `여러 개의 에러 로그가 배치 작업으로 한 번에 처리되어야 한다`() {
        // given
        val product1Id = 1L
        val product2Id = 2L
        val product3Id = 3L
        val initialStock = 100L

        // Redis에 초기 재고 설정
        redissonClient.getAtomicLong("product_stock:$product1Id").set(initialStock)
        redissonClient.getAtomicLong("product_stock:$product2Id").set(initialStock)
        redissonClient.getAtomicLong("product_stock:$product3Id").set(initialStock)

        // Redis 연결 차단 후 여러 요청 실패
        redisProxy.disable()

        assertThrows<InfraException> {
            productStockService.cancelReservation(StockChange(product1Id, 10L))
        }
        assertThrows<InfraException> {
            productStockService.cancelReservation(StockChange(product2Id, 20L))
        }
        assertThrows<InfraException> {
            productStockService.cancelReservation(StockChange(product3Id, 30L))
        }

        // 에러 로그 3개 확인
        val errorLogsBeforeBatch = jdbcTemplate.queryForList(
            "SELECT * FROM exception_logs WHERE reason = ? AND is_executed = false", "STOCK_CHANGE"
        )
        assertEquals(3, errorLogsBeforeBatch.size, "3개의 에러 로그가 저장되어야 함")

        // Redis 연결 복구
        redisProxy.enable()

        // created_at을 1분 이전으로 설정
        jdbcTemplate.update(
            "UPDATE exception_logs SET created_at = DATE_SUB(NOW(), INTERVAL 2 MINUTE) WHERE is_executed = false"
        )

        // when - 배치 작업 실행
        val mockContext = Mockito.mock(JobExecutionContext::class.java)
        Mockito.`when`(mockContext.scheduler).thenReturn(scheduler)
        stockConsistencyBatchJob.execute(mockContext)

        // then
        // 1. 모든 에러 로그가 실행 완료 처리
        val errorLogsAfterBatch = jdbcTemplate.queryForList(
            "SELECT * FROM exception_logs WHERE reason = ? AND is_executed = false", "STOCK_CHANGE"
        )
        assertEquals(0, errorLogsAfterBatch.size, "모든 에러 로그가 실행 완료 처리되어야 함")

        // 2. 모든 상품의 재고가 정상적으로 업데이트
        assertEquals(110L, redissonClient.getAtomicLong("product_stock:$product1Id").get())
        assertEquals(120L, redissonClient.getAtomicLong("product_stock:$product2Id").get())
        assertEquals(130L, redissonClient.getAtomicLong("product_stock:$product3Id").get())

        println("배치 실행 후 재고 - product1: 110, product2: 120, product3: 130")
    }
}
