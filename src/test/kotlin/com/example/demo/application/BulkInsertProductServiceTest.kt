package com.example.demo.application

import com.example.demo.domain.product.BulkInsertProductRepository
import com.example.demo.domain.product.Product
import com.example.demo.ui.dto.request.RegisterProductRequest
import com.example.demo.ui.dto.response.RegisterProductResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.dao.TransientDataAccessException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Test용 TransientDataAccessException 구현체
class TestTransientDataAccessException(msg: String) : TransientDataAccessException(msg)

@ExtendWith(MockitoExtension::class)
class BulkInsertProductServiceTest {

    @Mock
    private lateinit var bulkInsertProductRepository: BulkInsertProductRepository

    @InjectMocks
    private lateinit var bulkInsertProductService: BulkInsertProductService

    @BeforeEach
    fun setUp() {
        // Mock 초기화는 @ExtendWith(MockitoExtension::class)에 의해 자동으로 수행됨
    }

    @Test
    fun `모든 상품이 성공적으로 등록되어야 한다`() {
        // given
        val products = createValidProducts(5)
        whenever(bulkInsertProductRepository.saveAllAndReturnFailed(any()))
            .thenReturn(emptyList())

        // when
        val result = bulkInsertProductService.registerProducts(products)

        // then
        assertEquals(5, result.successCount)
        assertEquals(0, result.failureCount)
        assertTrue(result.failedProducts.isEmpty())
        verify(bulkInsertProductRepository, times(1)).saveAllAndReturnFailed(any())
    }

    @Test
    fun `빈 리스트가 입력되면 성공 0 실패 0이어야 한다`() {
        // given
        val products = emptyList<RegisterProductRequest.RegisterProduct>()

        // when
        val result = bulkInsertProductService.registerProducts(products)

        // then
        assertEquals(0, result.successCount)
        assertEquals(0, result.failureCount)
        assertTrue(result.failedProducts.isEmpty())
        verify(bulkInsertProductRepository, never()).saveAllAndReturnFailed(any())
    }

    @Test
    fun `상품명이 빈 문자열일 경우 검증 실패해야 한다`() {
        // given
        val products = listOf(
            RegisterProductRequest.RegisterProduct(
                name = "",
                price = 1000L,
                stock = 10L,
                code = "P001"
            )
        )
        whenever(bulkInsertProductRepository.saveAllAndReturnFailed(any()))
            .thenReturn(emptyList())

        // when
        val result = bulkInsertProductService.registerProducts(products)

        // then
        assertEquals(0, result.successCount)
        assertEquals(1, result.failureCount)
        assertEquals(1, result.failedProducts.size)
        assertEquals("상품명은 필수입니다", result.failedProducts[0].message)
    }

    @Test
    fun `상품명이 20자를 초과하면 검증 실패해야 한다`() {
        // given
        val products = listOf(
            RegisterProductRequest.RegisterProduct(
                name = "이것은매우긴상품명입니다정말로매우길어요정말매우길다",
                price = 1000L,
                stock = 10L,
                code = "P001"
            )
        )
        whenever(bulkInsertProductRepository.saveAllAndReturnFailed(any()))
            .thenReturn(emptyList())

        // when
        val result = bulkInsertProductService.registerProducts(products)

        // then
        assertEquals(0, result.successCount)
        assertEquals(1, result.failureCount)
        assertEquals("상품명은 20자 이하여야 합니다", result.failedProducts[0].message)
    }

    @Test
    fun `상품명에 특수문자가 포함되면 검증 실패해야 한다`() {
        // given
        val products = listOf(
            RegisterProductRequest.RegisterProduct(
                name = "상품명@#$",
                price = 1000L,
                stock = 10L,
                code = "P001"
            )
        )
        whenever(bulkInsertProductRepository.saveAllAndReturnFailed(any()))
            .thenReturn(emptyList())

        // when
        val result = bulkInsertProductService.registerProducts(products)

        // then
        assertEquals(0, result.successCount)
        assertEquals(1, result.failureCount)
        assertEquals("상품명은 특수문자를 포함할 수 없습니다", result.failedProducts[0].message)
    }

    @Test
    fun `가격이 음수이면 검증 실패해야 한다`() {
        // given
        val products = listOf(
            RegisterProductRequest.RegisterProduct(
                name = "상품1",
                price = -1000L,
                stock = 10L,
                code = "P001"
            )
        )
        whenever(bulkInsertProductRepository.saveAllAndReturnFailed(any()))
            .thenReturn(emptyList())

        // when
        val result = bulkInsertProductService.registerProducts(products)

        // then
        assertEquals(0, result.successCount)
        assertEquals(1, result.failureCount)
        assertEquals("Money value cannot be negative", result.failedProducts[0].message)
    }

    @Test
    fun `재고가 음수이면 검증 실패해야 한다`() {
        // given
        val products = listOf(
            RegisterProductRequest.RegisterProduct(
                name = "상품1",
                price = 1000L,
                stock = -10L,
                code = "P001"
            )
        )
        whenever(bulkInsertProductRepository.saveAllAndReturnFailed(any()))
            .thenReturn(emptyList())

        // when
        val result = bulkInsertProductService.registerProducts(products)

        // then
        assertEquals(0, result.successCount)
        assertEquals(1, result.failureCount)
        assertEquals("재고는 0개 이상이어야 합니다", result.failedProducts[0].message)
    }

    @Test
    fun `Repository에서 반환한 실패 상품들이 응답에 포함되어야 한다`() {
        // given
        val products = createValidProducts(3)
        val dbFailedProducts = listOf(
            RegisterProductResponse.FailedRegisterProduct(
                name = "상품1",
                price = 1000L,
                stock = 10L,
                message = "상품 코드가 중복되었습니다"
            )
        )
        whenever(bulkInsertProductRepository.saveAllAndReturnFailed(any()))
            .thenReturn(dbFailedProducts)

        // when
        val result = bulkInsertProductService.registerProducts(products)

        // then
        assertEquals(2, result.successCount)
        assertEquals(1, result.failureCount)
        assertEquals(1, result.failedProducts.size)
        assertEquals("상품 코드가 중복되었습니다", result.failedProducts[0].message)
    }

    @Test
    fun `비즈니스 검증 실패와 DB 저장 실패가 함께 발생해야 한다`() {
        // given
        val products = listOf(
            RegisterProductRequest.RegisterProduct(
                name = "",
                price = 1000L,
                stock = 10L,
                code = "P001"
            ),
            RegisterProductRequest.RegisterProduct(
                name = "상품2",
                price = 2000L,
                stock = 20L,
                code = "P002"
            ),
            RegisterProductRequest.RegisterProduct(
                name = "상품3",
                price = 3000L,
                stock = 30L,
                code = "P003"
            )
        )

        val dbFailedProducts = listOf(
            RegisterProductResponse.FailedRegisterProduct(
                name = "상품2",
                price = 2000L,
                stock = 20L,
                message = "상품 코드가 중복되었습니다"
            )
        )
        whenever(bulkInsertProductRepository.saveAllAndReturnFailed(any()))
            .thenReturn(dbFailedProducts)

        // when
        val result = bulkInsertProductService.registerProducts(products)

        // then
        assertEquals(1, result.successCount) // 상품3만 성공
        assertEquals(2, result.failureCount) // 상품1(검증실패), 상품2(DB실패)
        assertEquals(2, result.failedProducts.size)
        assertTrue(result.failedProducts.any { it.message == "상품명은 필수입니다" })
        assertTrue(result.failedProducts.any { it.message == "상품 코드가 중복되었습니다" })
    }

    @Test
    fun `10개를 초과하는 상품은 여러 청크로 나뉘어 처리되어야 한다`() {
        // given
        val products = createValidProducts(25) // 3개의 청크로 나뉨 (10 + 10 + 5)
        whenever(bulkInsertProductRepository.saveAllAndReturnFailed(any()))
            .thenReturn(emptyList())

        // when
        val result = bulkInsertProductService.registerProducts(products)

        // then
        assertEquals(25, result.successCount)
        assertEquals(0, result.failureCount)
        verify(bulkInsertProductRepository, times(3)).saveAllAndReturnFailed(any())
    }

    @Test
    fun `정확히 10개인 경우 1개의 청크로 처리되어야 한다`() {
        // given
        val products = createValidProducts(10)
        whenever(bulkInsertProductRepository.saveAllAndReturnFailed(any()))
            .thenReturn(emptyList())

        // when
        val result = bulkInsertProductService.registerProducts(products)

        // then
        assertEquals(10, result.successCount)
        assertEquals(0, result.failureCount)
        verify(bulkInsertProductRepository, times(1)).saveAllAndReturnFailed(any())
    }

    @Test
    fun `청크 단위로 검증과 저장이 이루어져야 한다`() {
        // given
        val products = listOf(
            // 첫 번째 청크: 검증 실패 1개 + 성공 9개
            RegisterProductRequest.RegisterProduct("", 1000L, 10L, "P001"),
            *createValidProducts(9).toTypedArray(),
            // 두 번째 청크: 모두 성공
            *createValidProducts(10).toTypedArray()
        )

        whenever(bulkInsertProductRepository.saveAllAndReturnFailed(any()))
            .thenReturn(emptyList())

        // when
        val result = bulkInsertProductService.registerProducts(products)

        // then
        assertEquals(19, result.successCount)
        assertEquals(1, result.failureCount)
        verify(bulkInsertProductRepository, times(2)).saveAllAndReturnFailed(any())
    }

    @Test
    fun `TransientDataAccessException 발생 시 재시도를 수행하고 성공해야 한다`() {
        // given
        val products = createValidProducts(5)
        whenever(bulkInsertProductRepository.saveAllAndReturnFailed(any()))
            .thenThrow(TestTransientDataAccessException("일시적 DB 오류"))
            .thenReturn(emptyList()) // 첫 재시도에서 성공

        // when
        val result = bulkInsertProductService.registerProducts(products)

        // then
        assertEquals(5, result.successCount)
        assertEquals(0, result.failureCount)
        verify(bulkInsertProductRepository, times(2)).saveAllAndReturnFailed(any())
    }

    @Test
    fun `TransientDataAccessException 발생 시 2번 재시도 후 성공해야 한다`() {
        // given
        val products = createValidProducts(5)
        whenever(bulkInsertProductRepository.saveAllAndReturnFailed(any()))
            .thenThrow(TestTransientDataAccessException("일시적 DB 오류"))
            .thenThrow(TestTransientDataAccessException("일시적 DB 오류"))
            .thenReturn(emptyList()) // 두 번째 재시도에서 성공

        // when
        val result = bulkInsertProductService.registerProducts(products)

        // then
        assertEquals(5, result.successCount)
        assertEquals(0, result.failureCount)
        verify(bulkInsertProductRepository, times(3)).saveAllAndReturnFailed(any()) // 최초 + 재시도 2회
    }

    @Test
    fun `모든 재시도 실패 후 최종 실패 처리되어야 한다`() {
        // given
        val products = createValidProducts(5)
        whenever(bulkInsertProductRepository.saveAllAndReturnFailed(any()))
            .thenThrow(TestTransientDataAccessException("일시적 DB 오류"))

        // when
        val result = bulkInsertProductService.registerProducts(products)

        // then
        assertEquals(0, result.successCount)
        assertEquals(5, result.failureCount)
        assertEquals(5, result.failedProducts.size)
        result.failedProducts.forEach {
            assertEquals("서버 장애로인해 데이터를 저장하지 못했습니다.", it.message)
        }
        verify(bulkInsertProductRepository, times(3)).saveAllAndReturnFailed(any()) // 최초 + 재시도 2회
    }

    @Test
    fun `일부 청크만 재시도해야 한다`() {
        // given
        val products = createValidProducts(20) // 2개의 청크
        whenever(bulkInsertProductRepository.saveAllAndReturnFailed(argThat { this.size == 10 }))
            .thenReturn(emptyList()) // 첫 번째 청크는 성공
            .thenThrow(TestTransientDataAccessException("일시적 DB 오류")) // 두 번째 청크는 실패
            .thenReturn(emptyList()) // 재시도 시 성공

        // when
        val result = bulkInsertProductService.registerProducts(products)

        // then
        assertEquals(20, result.successCount)
        assertEquals(0, result.failureCount)
        verify(bulkInsertProductRepository, times(3)).saveAllAndReturnFailed(any()) // 첫번째 청크 + 두번째 청크 + 재시도
    }

    @Test
    fun `재시도 중 일부는 성공하고 일부는 계속 실패할 수 있다`() {
        // given
        val products = createValidProducts(30) // 3개의 청크
        whenever(bulkInsertProductRepository.saveAllAndReturnFailed(any()))
            .thenReturn(emptyList()) // 첫 번째 청크 성공
            .thenThrow(TestTransientDataAccessException("일시적 DB 오류")) // 두 번째 청크 실패
            .thenThrow(TestTransientDataAccessException("일시적 DB 오류")) // 세 번째 청크 실패
            .thenReturn(emptyList()) // 첫 번째 재시도: 두 번째 청크 성공
            .thenThrow(TestTransientDataAccessException("일시적 DB 오류")) // 첫 번째 재시도: 세 번째 청크 여전히 실패
            .thenThrow(TestTransientDataAccessException("일시적 DB 오류")) // 두 번째 재시도: 세 번째 청크 여전히 실패

        // when
        val result = bulkInsertProductService.registerProducts(products)

        // then
        assertEquals(20, result.successCount) // 첫 번째 + 두 번째 청크 성공
        assertEquals(10, result.failureCount) // 세 번째 청크 최종 실패
        assertEquals(10, result.failedProducts.size)
        result.failedProducts.forEach {
            assertEquals("서버 장애로인해 데이터를 저장하지 못했습니다.", it.message)
        }
    }

    @Test
    fun `검증 실패 DB 실패 TransientDataAccessException 모두 발생`() {
        // given
        val products = listOf(
            // 검증 실패
            RegisterProductRequest.RegisterProduct("", 1000L, 10L, "P001"),
            // 정상 상품들
            *createValidProducts(25).toTypedArray()
        )

        val dbFailedProducts = listOf(
            RegisterProductResponse.FailedRegisterProduct(
                name = "상품1",
                price = 1000L,
                stock = 10L,
                message = "중복된 코드입니다"
            )
        )

        whenever(bulkInsertProductRepository.saveAllAndReturnFailed(any()))
            .thenReturn(emptyList()) // 첫 번째 청크 성공
            .thenReturn(dbFailedProducts) // 두 번째 청크 일부 실패
            .thenThrow(TestTransientDataAccessException("일시적 DB 오류")) // 세 번째 청크 TransientException
            .thenReturn(emptyList()) // 재시도 성공

        // when
        val result = bulkInsertProductService.registerProducts(products)

        // then
        assertEquals(24, result.successCount) // 총 26개 중 검증실패 1개, DB실패 1개
        assertEquals(2, result.failureCount)
        assertEquals(2, result.failedProducts.size)
        assertTrue(result.failedProducts.any { it.message == "상품명은 필수입니다" })
        assertTrue(result.failedProducts.any { it.message == "중복된 코드입니다" })
    }

    @Test
    fun `재시도 중 DB 실패가 발생하면 실패 목록에 추가되어야 한다`() {
        // given
        val products = createValidProducts(10)
        val dbFailedProducts = listOf(
            RegisterProductResponse.FailedRegisterProduct(
                name = "상품1",
                price = 1000L,
                stock = 10L,
                message = "중복된 코드입니다"
            )
        )

        whenever(bulkInsertProductRepository.saveAllAndReturnFailed(any()))
            .thenThrow(TestTransientDataAccessException("일시적 DB 오류"))
            .thenReturn(dbFailedProducts) // 재시도 시 일부 DB 실패

        // when
        val result = bulkInsertProductService.registerProducts(products)

        // then
        assertEquals(9, result.successCount)
        assertEquals(1, result.failureCount)
        assertEquals("중복된 코드입니다", result.failedProducts[0].message)
    }

    // 헬퍼 메서드
    private fun createValidProducts(count: Int): List<RegisterProductRequest.RegisterProduct> {
        return (1..count).map {
            RegisterProductRequest.RegisterProduct(
                name = "상품$it",
                price = (it * 1000).toLong(),
                stock = (it * 10).toLong(),
                code = "P${String.format("%03d", it)}"
            )
        }
    }
}
