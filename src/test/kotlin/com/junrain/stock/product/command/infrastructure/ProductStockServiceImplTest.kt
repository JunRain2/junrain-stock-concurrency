package com.junrain.stock.product.command.infrastructure

import com.junrain.stock.contract.exception.InfraException
import com.junrain.stock.config.jdbc.ErrorLogRepository
import com.junrain.stock.product.command.domain.StockChange
import com.junrain.stock.product.command.infrastructure.mysql.JpaProductRepository
import com.junrain.stock.product.command.infrastructure.redis.RedisStockRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.redisson.client.RedisConnectionException
import org.redisson.client.RedisTimeoutException

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MockitoExtension::class)
class ProductStockServiceImplTest {

    @Mock
    private lateinit var redisStockRepository: RedisStockRepository

    @Mock
    private lateinit var jpaProductRepository: JpaProductRepository

    @Mock
    private lateinit var errorLogRepository: ErrorLogRepository

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testScope: TestScope
    private lateinit var productStockService: ProductStockServiceImpl

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        testScope = TestScope(testDispatcher)

        productStockService = ProductStockServiceImpl(
            redisStockRepository = redisStockRepository,
            jpaProductRepository = jpaProductRepository,
            applicationScope = testScope
        )
    }

    // ==================== reserve() 테스트 ====================

    @Test
    fun `reserve는 재고를 감소시키고 정상적으로 완료되어야 한다`() {
        // given
        val changes = arrayOf(
            StockChange(productId = 1L, quantity = 10L),
            StockChange(productId = 2L, quantity = 5L)
        )

        // when
        productStockService.reserve(*changes)

        // then
        verify(redisStockRepository, times(1)).decreaseStock(check { assertNotNull(it) }, any(), any())
        verifyNoInteractions(errorLogRepository)
    }

    @Test
    fun `reserve 시 RedisConnectionException이 발생하면 InfraHandledException을 던져야 한다`() {
        // given
        val changes = arrayOf(StockChange(productId = 1L, quantity = 10L))
        val exception = InfraException(RedisConnectionException("Connection failed"))

        whenever(redisStockRepository.decreaseStock(any(), any())).thenThrow(exception)

        // when & then
        val thrown = assertThrows<InfraException> {
            productStockService.reserve(*changes)
        }

        assertEquals(exception.cause, thrown.cause)
        verifyNoInteractions(errorLogRepository)
    }

    @Test
    fun `reserve 시 RedisTimeoutException이 발생하면 InfraHandledException을 던져야 한다`() =
        runTest {
            // given
            val changes = arrayOf(StockChange(productId = 1L, quantity = 10L))
            val exception = InfraException(RedisTimeoutException())

            whenever(redisStockRepository.decreaseStock(any(), any())).thenThrow(exception)

            // when & then
            val thrown = assertThrows<InfraException> {
                productStockService.reserve(*changes)
            }

            assertEquals(exception.cause, thrown.cause)
        }

    @Test
    fun `reserve 시 알 수 없는 예외가 발생하면 그대로 전파되어야 한다`() {
        // given
        val changes = arrayOf(StockChange(productId = 1L, quantity = 10L))
        val exception = RuntimeException("Unknown error")

        whenever(redisStockRepository.decreaseStock(any(), any())).thenThrow(exception)

        // when & then
        val thrown = assertThrows<RuntimeException> {
            productStockService.reserve(*changes)
        }

        assertEquals(exception, thrown)
        verifyNoInteractions(errorLogRepository)
    }

    // ==================== cancelReservation() 테스트 ====================

    @Test
    fun `cancelReservation은 재고를 증가시키고 정상적으로 완료되어야 한다`() {
        // given
        val changes = arrayOf(
            StockChange(productId = 1L, quantity = 10L),
            StockChange(productId = 2L, quantity = 5L)
        )

        // when
        productStockService.cancelReservation(*changes)

        // then
        verify(redisStockRepository, times(1)).increaseStock(check { assertNotNull(it) }, any(), any())
        verifyNoInteractions(errorLogRepository)
    }

    @Test
    fun `cancelReservation 시 RedisConnectionException이 발생하면 InfraHandledException을 던져야 한다`() =
        runTest {
            // given
            val changes = arrayOf(StockChange(productId = 1L, quantity = 10L))
            val exception = InfraException(RedisConnectionException("Connection failed"))

            whenever(redisStockRepository.increaseStock(any(), any())).thenThrow(exception)

            // when & then
            val thrown = assertThrows<InfraException> {
                productStockService.cancelReservation(*changes)
            }

            assertEquals(exception.cause, thrown.cause)
        }

    @Test
    fun `cancelReservation 시 RedisTimeoutException이 발생하면 InfraHandledException을 던져야 한다`() {
        // given
        val changes = arrayOf(StockChange(productId = 1L, quantity = 10L))
        val exception = InfraException(RedisTimeoutException("Timeout"))

        whenever(redisStockRepository.increaseStock(any(), any())).thenThrow(exception)

        // when & then
        val thrown = assertThrows<InfraException> {
            productStockService.cancelReservation(*changes)
        }

        assertEquals(exception.cause, thrown.cause)
    }

    @Test
    fun `cancelReservation 시 알 수 없는 예외가 발생하면 그대로 전파되어야 한다`() {
        // given
        val changes = arrayOf(StockChange(productId = 1L, quantity = 10L))
        val exception = RuntimeException("Unknown error")

        whenever(redisStockRepository.increaseStock(any(), any())).thenThrow(exception)

        // when & then
        val thrown = assertThrows<RuntimeException> {
            productStockService.cancelReservation(*changes)
        }

        assertEquals(exception, thrown)
        verifyNoInteractions(errorLogRepository)
    }

    // ==================== increase() 테스트 ====================

    @Test
    fun `increase는 DB를 먼저 증가시키고 비동기로 Redis를 증가시켜야 한다`() = runTest {
        // given
        val changes = arrayOf(
            StockChange(productId = 1L, quantity = 10L),
            StockChange(productId = 2L, quantity = 5L)
        )

        // when
        productStockService.increase(*changes)

        // 비동기 작업 완료 대기
        testScope.advanceUntilIdle()

        // then
        verify(jpaProductRepository, times(1)).updateProductStock(1L, 10L)
        verify(jpaProductRepository, times(1)).updateProductStock(2L, 5L)
        verify(redisStockRepository, times(1)).increaseStock(check { assertNotNull(it) }, any(), any())
    }

    @Test
    fun `increase 시 DB 업데이트가 실패하면 예외가 발생해야 한다`() {
        // given
        val changes = arrayOf(StockChange(productId = 1L, quantity = 10L))
        val exception = RuntimeException("DB update failed")

        whenever(jpaProductRepository.updateProductStock(any(), any())).thenThrow(exception)

        // when & then
        val thrown = assertThrows<RuntimeException> {
            productStockService.increase(*changes)
        }

        assertEquals(exception, thrown)
        verifyNoInteractions(redisStockRepository)
    }

    @Test
    fun `increase 시 비동기 Redis 증가가 실패하면 DB는 증가되어야 한다`() = runTest {
        // given
        val changes = arrayOf(StockChange(productId = 1L, quantity = 10L))
        val exception = RedisConnectionException("Connection failed")

        whenever(redisStockRepository.increaseStock(any(), any())).thenThrow(exception)

        // when
        productStockService.increase(*changes)

        // 비동기 작업 완료 대기 (예외가 발생하더라도)
        testScope.advanceUntilIdle()

        // then
        // DB는 성공적으로 증가되어야 함 (비동기 Redis 실패와 무관)
        verify(jpaProductRepository, times(1)).updateProductStock(1L, 10L)
        // 비동기 내부에서 에러 로그 저장 시도 (실패 시 로그만 남음)
        // Note: 비동기 코루틴 내부의 예외는 삼켜지므로 외부로 전파되지 않음
    }

    // ==================== decrease() 테스트 ====================

    @Test
    fun `decrease는 DB 재고를 감소시켜야 한다`() {
        // given
        val changes = arrayOf(
            StockChange(productId = 1L, quantity = 10L),
            StockChange(productId = 2L, quantity = 5L)
        )

        // when
        productStockService.decrease(*changes)

        // then
        verify(jpaProductRepository, times(1)).updateProductStock(1L, -10L)
        verify(jpaProductRepository, times(1)).updateProductStock(2L, -5L)
        verifyNoInteractions(redisStockRepository)
        verifyNoInteractions(errorLogRepository)
    }

    @Test
    fun `decrease 시 DB 업데이트가 실패하면 예외가 발생해야 한다`() {
        // given
        val changes = arrayOf(StockChange(productId = 1L, quantity = 10L))
        val exception = RuntimeException("DB update failed")

        whenever(jpaProductRepository.updateProductStock(any(), any())).thenThrow(exception)

        // when & then
        val thrown = assertThrows<RuntimeException> {
            productStockService.decrease(*changes)
        }

        assertEquals(exception, thrown)
        verifyNoInteractions(redisStockRepository)
        verifyNoInteractions(errorLogRepository)
    }
}
