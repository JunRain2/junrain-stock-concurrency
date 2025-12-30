package com.junrain.stock.product.query.application

import com.junrain.stock.contract.vo.Money
import com.junrain.stock.product.query.application.dto.ProductPageQuery
import com.junrain.stock.product.query.application.dto.ProductPageResult
import com.junrain.stock.product.query.application.dto.ProductSorter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.math.BigDecimal
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductQueryServiceTest {

    @Mock
    private lateinit var productQueryRepository: ProductQueryRepository

    private lateinit var productQueryService: ProductQueryService

    @BeforeEach
    fun setUp() {
        productQueryService = ProductQueryService(productQueryRepository)
    }

    @Test
    fun `최신순 정렬로 상품 페이지를 조회하면 올바른 응답을 반환해야 한다`() {
        // given
        val sorter = ProductSorter.LatestSorter(
            lastProductId = null,
            createdAt = null
        )
        val query = ProductPageQuery(
            ownerId = null,
            productName = "Product",
            productSorter = sorter,
            size = 10
        )

        val products = createProductPageResults(10)
        whenever(productQueryRepository.findProductPage(
            ownerId = null,
            size = 10,
            productName = "Product",
            sortRequest = sorter
        )).thenReturn(products)

        // when
        val result = productQueryService.getProductPage(query)

        // then
        assertEquals(10, result.data.size)
        assertEquals(10, result.size)
        assertFalse(result.hasNext)
        assertTrue(result.nextCursor.isNotEmpty())
        assertEquals(products.last().productId, result.nextCursor["lastProductId"])
    }

    @Test
    fun `다음 페이지가 있을 경우 hasNext가 true여야 한다`() {
        // given
        val query = ProductPageQuery(
            ownerId = null,
            productName = "Product",
            productSorter = ProductSorter.LatestSorter(
                lastProductId = null,
                createdAt = null
            ),
            size = 10
        )

        // size보다 1개 많은 데이터 반환 (다음 페이지 존재)
        val products = createProductPageResults(11)
        whenever(productQueryRepository.findProductPage(anyOrNull(), any(), any(), any()))
            .thenReturn(products)

        // when
        val result = productQueryService.getProductPage(query)

        // then
        assertEquals(10, result.data.size) // take(10)으로 잘림
        assertEquals(10, result.size)
        assertTrue(result.hasNext)
        assertTrue(result.nextCursor.isNotEmpty())
    }

    @Test
    fun `ownerId로 필터링하여 상품 페이지를 조회할 수 있다`() {
        // given
        val ownerId = 1L
        val query = ProductPageQuery(
            ownerId = ownerId,
            productName = "Product",
            productSorter = ProductSorter.LatestSorter(
                lastProductId = null,
                createdAt = null
            ),
            size = 10
        )

        val products = createProductPageResults(5, ownerId)
        whenever(productQueryRepository.findProductPage(anyOrNull(), any(), any(), any()))
            .thenReturn(products)

        // when
        val result = productQueryService.getProductPage(query)

        // then
        assertEquals(5, result.data.size)
        assertEquals(5, result.size)
        assertFalse(result.hasNext)
        assertTrue(result.data.all { it.owner.ownerId == ownerId })
    }

    @Test
    fun `가격 오름차순 정렬로 상품 페이지를 조회할 수 있다`() {
        // given
        val query = ProductPageQuery(
            ownerId = null,
            productName = "Product",
            productSorter = ProductSorter.SalePriceAsc(
                lastProductId = null,
                price = null
            ),
            size = 10
        )

        val products = createProductPageResults(10)
        whenever(productQueryRepository.findProductPage(anyOrNull(), any(), any(), any()))
            .thenReturn(products)

        // when
        val result = productQueryService.getProductPage(query)

        // then
        assertEquals(10, result.data.size)
        assertTrue(result.nextCursor.isNotEmpty())
        assertEquals(products.last().productId, result.nextCursor["lastProductId"])
        assertEquals(products.last().price.amount, result.nextCursor["price"])
    }

    @Test
    fun `가격 내림차순 정렬로 상품 페이지를 조회할 수 있다`() {
        // given
        val query = ProductPageQuery(
            ownerId = null,
            productName = "Product",
            productSorter = ProductSorter.SalePriceDesc(
                lastProductId = null,
                price = null
            ),
            size = 10
        )

        val products = createProductPageResults(10)
        whenever(productQueryRepository.findProductPage(anyOrNull(), any(), any(), any()))
            .thenReturn(products)

        // when
        val result = productQueryService.getProductPage(query)

        // then
        assertEquals(10, result.data.size)
        assertTrue(result.nextCursor.isNotEmpty())
        assertEquals(products.last().productId, result.nextCursor["lastProductId"])
        assertEquals(products.last().price.amount, result.nextCursor["price"])
    }

    @Test
    fun `커서 기반 페이지네이션으로 다음 페이지를 조회할 수 있다`() {
        // given
        val lastProductId = 10L
        val createdAt = LocalDateTime.now()
        val query = ProductPageQuery(
            ownerId = null,
            productName = "Product",
            productSorter = ProductSorter.LatestSorter(
                lastProductId = lastProductId,
                createdAt = createdAt
            ),
            size = 10
        )

        val products = createProductPageResults(10, startId = 11L)
        whenever(productQueryRepository.findProductPage(anyOrNull(), any(), any(), any()))
            .thenReturn(products)

        // when
        val result = productQueryService.getProductPage(query)

        // then
        assertEquals(10, result.data.size)
        assertTrue(result.data.all { it.productId > lastProductId })
        assertTrue(result.nextCursor.isNotEmpty())
    }

    @Test
    fun `상품명으로 전위 검색이 가능해야 한다`() {
        // given
        val query = ProductPageQuery(
            ownerId = null,
            productName = "Prod",
            productSorter = ProductSorter.LatestSorter(
                lastProductId = null,
                createdAt = null
            ),
            size = 10
        )

        val products = createProductPageResults(5)
        whenever(productQueryRepository.findProductPage(anyOrNull(), any(), any(), any()))
            .thenReturn(products)

        // when
        val result = productQueryService.getProductPage(query)

        // then
        assertEquals(5, result.data.size)
        assertTrue(result.data.all { it.name.startsWith("Product") })
        assertTrue(result.nextCursor.isNotEmpty())
    }

    @Test
    fun `size 파라미터로 조회 개수를 지정할 수 있다`() {
        // given
        val requestSize = 5
        val query = ProductPageQuery(
            ownerId = null,
            productName = "Product",
            productSorter = ProductSorter.LatestSorter(
                lastProductId = null,
                createdAt = null
            ),
            size = requestSize
        )

        val products = createProductPageResults(requestSize)
        whenever(productQueryRepository.findProductPage(anyOrNull(), any(), any(), any()))
            .thenReturn(products)

        // when
        val result = productQueryService.getProductPage(query)

        // then
        assertEquals(requestSize, result.data.size)
        assertEquals(requestSize, result.size)
        assertTrue(result.nextCursor.isNotEmpty())
    }

    @Test
    fun `조회 결과가 없으면 빈 응답을 반환해야 한다`() {
        // given
        val query = ProductPageQuery(
            ownerId = null,
            productName = "NonExistent",
            productSorter = ProductSorter.LatestSorter(
                lastProductId = null,
                createdAt = null
            ),
            size = 10
        )

        // Repository에서 빈 리스트 반환
        whenever(productQueryRepository.findProductPage(anyOrNull(), any(), any(), any()))
            .thenReturn(emptyList())

        // when
        val result = productQueryService.getProductPage(query)

        // then
        assertEquals(0, result.data.size)
        assertEquals(0, result.size)
        assertFalse(result.hasNext)
        assertTrue(result.nextCursor.isEmpty())
    }

    @Test
    fun `가격 오름차순 커서 기반 페이지네이션`() {
        // given
        val lastProductId = 5L
        val price = BigDecimal("5000")
        val query = ProductPageQuery(
            ownerId = null,
            productName = "Product",
            productSorter = ProductSorter.SalePriceAsc(
                lastProductId = lastProductId,
                price = price
            ),
            size = 10
        )

        val products = createProductPageResults(10, startId = 6L, startPrice = 6000)
        whenever(productQueryRepository.findProductPage(anyOrNull(), any(), any(), any()))
            .thenReturn(products)

        // when
        val result = productQueryService.getProductPage(query)

        // then
        assertEquals(10, result.data.size)
        assertTrue(result.nextCursor.isNotEmpty())
        assertEquals(products.last().productId, result.nextCursor["lastProductId"])
        assertEquals(products.last().price.amount, result.nextCursor["price"])
    }

    @Test
    fun `가격 내림차순 커서 기반 페이지네이션`() {
        // given
        val lastProductId = 5L
        val price = BigDecimal("15000")
        val query = ProductPageQuery(
            ownerId = null,
            productName = "Product",
            productSorter = ProductSorter.SalePriceDesc(
                lastProductId = lastProductId,
                price = price
            ),
            size = 10
        )

        val products = createProductPageResults(10, startId = 6L, startPrice = 14000)
        whenever(productQueryRepository.findProductPage(anyOrNull(), any(), any(), any()))
            .thenReturn(products)

        // when
        val result = productQueryService.getProductPage(query)

        // then
        assertEquals(10, result.data.size)
        assertTrue(result.nextCursor.isNotEmpty())
        assertEquals(products.last().productId, result.nextCursor["lastProductId"])
        assertEquals(products.last().price.amount, result.nextCursor["price"])
    }

    // 헬퍼 메서드
    private fun createProductPageResults(
        count: Int,
        ownerId: Long = 1L,
        startId: Long = 1L,
        startPrice: Int = 1000
    ): List<ProductPageResult> {
        return (0 until count).map { index ->
            ProductPageResult(
                productId = startId + index,
                name = "Product${startId + index}",
                price = Money.of((startPrice + index * 1000).toLong()),
                owner = ProductPageResult.OwnerResponse(
                    ownerId = ownerId,
                    name = "Owner$ownerId"
                ),
                createdAt = LocalDateTime.now().minusHours(count - index.toLong())
            )
        }
    }
}
