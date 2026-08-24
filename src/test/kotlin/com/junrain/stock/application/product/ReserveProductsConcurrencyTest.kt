package com.junrain.stock.application.product

import com.junrain.stock.application.product.ReserveProducts.Command
import com.junrain.stock.application.product.ReserveProducts.Command.Change
import com.junrain.stock.config.StockProbe
import com.junrain.stock.domain.common.Money
import com.junrain.stock.domain.member.Member
import com.junrain.stock.domain.member.MemberRepository
import com.junrain.stock.domain.member.MemberType
import com.junrain.stock.domain.product.Product
import com.junrain.stock.domain.product.ProductRepository
import com.junrain.stock.domain.product.exception.StockUnavailableException
import com.junrain.stock.domain.product.vo.ProductCode
import com.junrain.stock.domain.reservation.ReservationRepository
import com.junrain.stock.infra.product.mysql.JpaProductRepository
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
class ReserveProductsConcurrencyTest {
    @Autowired
    private lateinit var reserveProducts: ReserveProducts

    @Autowired
    private lateinit var reservationRepository: ReservationRepository

    @Autowired
    private lateinit var jpaProductRepository: JpaProductRepository

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var stockProbe: StockProbe

    @Autowired
    private lateinit var memberRepository: MemberRepository

    private var sellerId: Long = 0
    private val codeSequence = AtomicInteger()

    @BeforeEach
    fun setUp() {
        sellerId = memberRepository.save(Member(memberType = MemberType.SELLER, name = "Test Seller")).id
    }

    @AfterEach
    fun tearDown() {
        reservationRepository.deleteAll()
        jpaProductRepository.deleteAll()
        memberRepository.deleteAll()
    }

    @Test
    fun `재고보다 많은 인원이 동시에 예약하면 재고 수만큼만 성공한다`() {
        // given
        val productId = saveProduct(stock = LIMITED_STOCK)

        // when
        val results = runConcurrently(List(CONTENDER_COUNT) { reserve(productId) })

        // then - lost update가 나면 성공 수가 재고보다 많아진다
        results.count { it.isSuccess } shouldBe LIMITED_STOCK.toInt()
        stockOf(productId) shouldBe 0
        withClue("실패한 요청은 Reservation을 남기지 않아야 합니다") {
            reservationRepository.count() shouldBe LIMITED_STOCK
        }

        val failures = results.failures()
        failures.size shouldBe CONTENDER_COUNT - LIMITED_STOCK.toInt()
        withClue("실패는 전부 재고 부족이어야 합니다 : ${failures.map { it::class.simpleName }}") {
            failures.count { it is StockUnavailableException } shouldBe failures.size
        }
    }

    @Test
    fun `여러 상품 중 하나가 동시에 소진되면 나머지 상품의 재고도 롤백된다`() {
        // given - 재고가 하나뿐인 상품을 끼워 대부분의 요청이 실패하게 만든다
        val abundantId = saveProduct(stock = ABUNDANT_STOCK)
        val scarceId = saveProduct(stock = 1)

        // when
        val results = runConcurrently(List(CONTENDER_COUNT) { reserve(listOf(abundantId, scarceId)) })

        // then - 실패한 요청이 abundant를 줄인 채 끝났다면 재고가 더 많이 빠진다
        results.count { it.isSuccess } shouldBe 1
        stockOf(scarceId) shouldBe 0
        withClue("실패한 요청의 재고 감소는 전부 롤백되어야 합니다") {
            stockOf(abundantId) shouldBe ABUNDANT_STOCK - 1
        }
        reservationRepository.count() shouldBe 1
    }

    /**
     * 모든 스레드를 같은 시점에 풀어 실제로 경합시킨다. 순차 실행이면 경합 자체가 재현되지 않는다.
     */
    private fun runConcurrently(tasks: List<() -> Unit>): List<Result<Unit>> {
        // ready로 전원이 게이트 앞에 도착한 것을 확인한 뒤 start를 풀어야 먼저 출발한 스레드가 앞서 나가지 않는다
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

    private fun List<Result<Unit>>.failures(): List<Throwable> = mapNotNull { it.exceptionOrNull() }

    /**
     * 요청에 담긴 상품 순서를 그대로 유지한다. 상품별 연산으로 쪼개는 순간 이 순서가 잠금 순서가 되어 데드락이 난다.
     */
    private fun reserve(ids: List<Long>): () -> Unit = { reserveProducts(Command(ids.map { Change(it, 1L) })) }

    private fun reserve(id: Long): () -> Unit = reserve(listOf(id))

    // 포트로 저장한다. 재고를 어디에 심는지는 구현체가 정한다
    private fun saveProduct(stock: Long): Long =
        productRepository
            .save(
                Product(
                    ownerId = sellerId,
                    name = "Test Product",
                    code = ProductCode("CONCURRENT%03d".format(codeSequence.incrementAndGet())),
                    price = Money.of(10000),
                    stock = stock,
                ),
            ).id

    private fun stockOf(id: Long): Long = stockProbe.stockOf(id)

    companion object {
        private const val LIMITED_STOCK = 10L
        private const val ABUNDANT_STOCK = 1000L
        private const val CONTENDER_COUNT = 30
        private const val READY_TIMEOUT_SECONDS = 10L
    }
}
