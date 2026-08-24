package com.junrain.stock.application.product

import com.junrain.stock.application.product.port.ProductReader
import com.junrain.stock.application.product.query.ProductSorter
import com.junrain.stock.domain.common.Money
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
class GetProductPageTest {
    @Mock
    private lateinit var productReader: ProductReader

    private lateinit var getProductPage: GetProductPage

    @BeforeEach
    fun setUp() {
        getProductPage = GetProductPage(productReader)
    }

    @Test
    fun `최신순 정렬로 상품 페이지를 조회하면 올바른 응답을 반환해야 한다`() {
        // given
        val sorter =
            ProductSorter.LatestSorter(
                lastProductId = null,
                createdAt = null,
            )
        val query =
            GetProductPage.Query(
                ownerId = null,
                productName = "Product",
                productSorter = sorter,
                size = 10,
            )

        val products = createProductPageResults(10)
        whenever(
            productReader.findProductPage(
                ownerId = null,
                size = 10,
                productName = "Product",
                sortRequest = sorter,
            ),
        ).thenReturn(products)

        // when
        val result = getProductPage(query)

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
        val query =
            GetProductPage.Query(
                ownerId = null,
                productName = "Product",
                productSorter =
                    ProductSorter.LatestSorter(
                        lastProductId = null,
                        createdAt = null,
                    ),
                size = 10,
            )

        // size보다 1개 많은 데이터 반환 (다음 페이지 존재)
        val products = createProductPageResults(11)
        whenever(productReader.findProductPage(anyOrNull(), any(), any(), any()))
            .thenReturn(products)

        // when
        val result = getProductPage(query)

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
        val query =
            GetProductPage.Query(
                ownerId = ownerId,
                productName = "Product",
                productSorter =
                    ProductSorter.LatestSorter(
                        lastProductId = null,
                        createdAt = null,
                    ),
                size = 10,
            )

        val products = createProductPageResults(5, ownerId)
        whenever(productReader.findProductPage(anyOrNull(), any(), any(), any()))
            .thenReturn(products)

        // when
        val result = getProductPage(query)

        // then
        assertEquals(5, result.data.size)
        assertEquals(5, result.size)
        assertFalse(result.hasNext)
        assertTrue(result.data.all { it.owner.ownerId == ownerId })
    }

    @Test
    fun `가격 오름차순 정렬로 상품 페이지를 조회할 수 있다`() {
        // given
        val query =
            GetProductPage.Query(
                ownerId = null,
                productName = "Product",
                productSorter =
                    ProductSorter.SalePriceAsc(
                        lastProductId = null,
                        price = null,
                    ),
                size = 10,
            )

        val products = createProductPageResults(10)
        whenever(productReader.findProductPage(anyOrNull(), any(), any(), any()))
            .thenReturn(products)

        // when
        val result = getProductPage(query)

        // then
        assertEquals(10, result.data.size)
        assertTrue(result.nextCursor.isNotEmpty())
        assertEquals(products.last().productId, result.nextCursor["lastProductId"])
        assertEquals(products.last().price.amount, result.nextCursor["price"])
    }

    @Test
    fun `가격 내림차순 정렬로 상품 페이지를 조회할 수 있다`() {
        // given
        val query =
            GetProductPage.Query(
                ownerId = null,
                productName = "Product",
                productSorter =
                    ProductSorter.SalePriceDesc(
                        lastProductId = null,
                        price = null,
                    ),
                size = 10,
            )

        val products = createProductPageResults(10)
        whenever(productReader.findProductPage(anyOrNull(), any(), any(), any()))
            .thenReturn(products)

        // when
        val result = getProductPage(query)

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
        val query =
            GetProductPage.Query(
                ownerId = null,
                productName = "Product",
                productSorter =
                    ProductSorter.LatestSorter(
                        lastProductId = lastProductId,
                        createdAt = createdAt,
                    ),
                size = 10,
            )

        val products = createProductPageResults(10, startId = 11L)
        whenever(productReader.findProductPage(anyOrNull(), any(), any(), any()))
            .thenReturn(products)

        // when
        val result = getProductPage(query)

        // then
        assertEquals(10, result.data.size)
        assertTrue(result.data.all { it.productId > lastProductId })
        assertTrue(result.nextCursor.isNotEmpty())
    }

    @Test
    fun `상품명으로 전위 검색이 가능해야 한다`() {
        // given
        val query =
            GetProductPage.Query(
                ownerId = null,
                productName = "Prod",
                productSorter =
                    ProductSorter.LatestSorter(
                        lastProductId = null,
                        createdAt = null,
                    ),
                size = 10,
            )

        val products = createProductPageResults(5)
        whenever(productReader.findProductPage(anyOrNull(), any(), any(), any()))
            .thenReturn(products)

        // when
        val result = getProductPage(query)

        // then
        assertEquals(5, result.data.size)
        assertTrue(result.data.all { it.name.startsWith("Product") })
        assertTrue(result.nextCursor.isNotEmpty())
    }

    @Test
    fun `size 파라미터로 조회 개수를 지정할 수 있다`() {
        // given
        val requestSize = 5
        val query =
            GetProductPage.Query(
                ownerId = null,
                productName = "Product",
                productSorter =
                    ProductSorter.LatestSorter(
                        lastProductId = null,
                        createdAt = null,
                    ),
                size = requestSize,
            )

        val products = createProductPageResults(requestSize)
        whenever(productReader.findProductPage(anyOrNull(), any(), any(), any()))
            .thenReturn(products)

        // when
        val result = getProductPage(query)

        // then
        assertEquals(requestSize, result.data.size)
        assertEquals(requestSize, result.size)
        assertTrue(result.nextCursor.isNotEmpty())
    }

    @Test
    fun `조회 결과가 없으면 빈 응답을 반환해야 한다`() {
        // given
        val query =
            GetProductPage.Query(
                ownerId = null,
                productName = "NonExistent",
                productSorter =
                    ProductSorter.LatestSorter(
                        lastProductId = null,
                        createdAt = null,
                    ),
                size = 10,
            )

        // Repository에서 빈 리스트 반환
        whenever(productReader.findProductPage(anyOrNull(), any(), any(), any()))
            .thenReturn(emptyList())

        // when
        val result = getProductPage(query)

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
        val query =
            GetProductPage.Query(
                ownerId = null,
                productName = "Product",
                productSorter =
                    ProductSorter.SalePriceAsc(
                        lastProductId = lastProductId,
                        price = price,
                    ),
                size = 10,
            )

        val products = createProductPageResults(10, startId = 6L, startPrice = 6000)
        whenever(productReader.findProductPage(anyOrNull(), any(), any(), any()))
            .thenReturn(products)

        // when
        val result = getProductPage(query)

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
        val query =
            GetProductPage.Query(
                ownerId = null,
                productName = "Product",
                productSorter =
                    ProductSorter.SalePriceDesc(
                        lastProductId = lastProductId,
                        price = price,
                    ),
                size = 10,
            )

        val products = createProductPageResults(10, startId = 6L, startPrice = 14000)
        whenever(productReader.findProductPage(anyOrNull(), any(), any(), any()))
            .thenReturn(products)

        // when
        val result = getProductPage(query)

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
        startPrice: Int = 1000,
    ): List<GetProductPage.Result> =
        (0 until count).map { index ->
            GetProductPage.Result(
                productId = startId + index,
                name = "Product${startId + index}",
                price = Money.of((startPrice + index * 1000).toLong()),
                owner =
                    GetProductPage.Result.Owner(
                        ownerId = ownerId,
                        name = "Owner$ownerId",
                    ),
                createdAt = LocalDateTime.now().minusHours(count - index.toLong()),
            )
        }
}
