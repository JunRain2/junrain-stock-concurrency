package com.junrain.stock.infra.product

import com.junrain.stock.application.product.port.StockWriter
import com.junrain.stock.domain.common.Money
import com.junrain.stock.domain.member.Member
import com.junrain.stock.domain.member.MemberRepository
import com.junrain.stock.domain.member.MemberType
import com.junrain.stock.domain.product.Product
import com.junrain.stock.domain.product.exception.ProductNotFoundException
import com.junrain.stock.domain.product.exception.StockUnavailableException
import com.junrain.stock.domain.product.exception.StockUnstableException
import com.junrain.stock.domain.product.vo.ProductCode
import com.junrain.stock.infra.product.mysql.JdbcStockItemRepository
import com.junrain.stock.infra.product.mysql.JpaProductRepository
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * SKIP LOCKED 차감 전략의 경합 검증. 유스케이스도 Reservation도 거치지 않는다 -
 * 여기서 볼 것은 [SkipLockedStockWriterImpl]이 stock_items를 얼마나 정확히 나눠 잡는지뿐이다.
 */
@SpringBootTest(properties = ["stock.strategy=skip-locked"])
class SkipLockedStockWriterImplTest {
    @Autowired
    private lateinit var stockWriter: SkipLockedStockWriterImpl

    @Autowired
    private lateinit var jdbcStockItemRepository: JdbcStockItemRepository

    @Autowired
    private lateinit var jpaProductRepository: JpaProductRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private var sellerId: Long = 0
    private val codeSequence = AtomicInteger()

    @BeforeEach
    fun setUp() {
        sellerId = memberRepository.save(Member(memberType = MemberType.SELLER, name = "Test Seller")).id
    }

    @AfterEach
    fun tearDown() {
        jdbcTemplate.update("DELETE FROM stock_items")
        jpaProductRepository.deleteAll()
        memberRepository.deleteAll()
    }

    @Test
    fun `재고보다 많은 동시 요청이 들어오면 정확히 재고 개수만큼만 성공한다`() {
        // given
        val productId = saveProduct()
        jdbcStockItemRepository.insertAvailable(productId, STOCK.toLong())

        // when - 재고(STOCK)보다 많은 요청(REQUESTERS)이 동시에 1개씩 요청한다
        val results = runConcurrently(List(REQUESTERS) { decrease(productId) })

        // then
        val failures = results.mapNotNull { it.exceptionOrNull() }
        results.count { it.isSuccess } shouldBe STOCK
        failures.count { it is StockUnavailableException } shouldBe (REQUESTERS - STOCK)
        failures.count { it is StockUnstableException } shouldBe 0
        availableCountOf(productId) shouldBe 0L
    }

    @Test
    fun `increase는 신규 가용 행을 추가한다`() {
        // given
        val productId = saveProduct()
        jdbcStockItemRepository.insertAvailable(productId, 3L)

        // when
        stockWriter.increase(listOf(StockWriter.StockChange(productId, 5)))

        // then
        availableCountOf(productId) shouldBe 8L
    }

    @Test
    fun `increase 대상 상품이 없으면 ProductNotFoundException`() {
        val results = runCatching { stockWriter.increase(listOf(StockWriter.StockChange(NON_EXISTENT_PRODUCT_ID, 1))) }

        (results.exceptionOrNull() is ProductNotFoundException) shouldBe true
    }

    private fun decrease(productId: Long): () -> Unit = {
        stockWriter.decrease(listOf(StockWriter.StockChange(productId, 1L)))
    }

    /**
     * 모든 스레드를 같은 시점에 풀어 실제로 경합시킨다. 순차 실행이면 경합 자체가 재현되지 않는다.
     */
    private fun runConcurrently(tasks: List<() -> Unit>): List<Result<Unit>> {
        val ready = CountDownLatch(tasks.size)
        val start = CountDownLatch(1)
        val results = ConcurrentLinkedQueue<Result<Unit>>()

        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            tasks.forEach { task ->
                executor.submit {
                    ready.countDown()
                    start.await()
                    results += runCatching { task() }
                }
            }
            check(ready.await(READY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "스레드가 제때 준비되지 않았습니다." }
            start.countDown()
        }

        return results.toList()
    }

    private fun saveProduct(): Long =
        jpaProductRepository
            .save(
                Product(
                    ownerId = sellerId,
                    name = "Test Product",
                    code = ProductCode("SKIPLOCK%03d".format(codeSequence.incrementAndGet())),
                    price = Money.of(10000),
                    stock = 0,
                ),
            ).id

    private fun availableCountOf(productId: Long): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM stock_items WHERE product_id = ? AND status = 'AVAILABLE'",
            Long::class.java,
            productId,
        ) ?: 0

    companion object {
        private const val STOCK = 30
        private const val REQUESTERS = 50
        private const val NON_EXISTENT_PRODUCT_ID = -1L
        private const val READY_TIMEOUT_SECONDS = 10L
    }
}
