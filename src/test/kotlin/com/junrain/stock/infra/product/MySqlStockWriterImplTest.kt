package com.junrain.stock.infra.product

import com.junrain.stock.application.product.port.StockWriter
import com.junrain.stock.domain.common.Money
import com.junrain.stock.domain.member.Member
import com.junrain.stock.domain.member.MemberRepository
import com.junrain.stock.domain.member.MemberType
import com.junrain.stock.domain.product.Product
import com.junrain.stock.domain.product.exception.StockUnstableException
import com.junrain.stock.domain.product.vo.ProductCode
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

/**
 * MySQL 차감 전략의 락 순서 검증.
 *
 * 데드락은 InnoDB의 행 잠금 순서에서만 생기므로 구현체를 직접 주입해 확인한다.
 * 유스케이스도 Reservation도 거치지 않는다 — 여기서 볼 것은 [MySqlStockWriterImpl]의 UPDATE 하나뿐이다.
 */
@SpringBootTest
class MySqlStockWriterImplTest {
    @Autowired
    private lateinit var stockWriter: MySqlStockWriterImpl

    @Autowired
    private lateinit var jpaProductRepository: JpaProductRepository

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
        jpaProductRepository.deleteAll()
        memberRepository.deleteAll()
    }

    @Test
    fun `상품 목록이 완전히 겹치고 순서가 반대여도 데드락 없이 전부 성공한다`() {
        // given - 두 그룹이 같은 세 상품을 정반대 순서로 요청한다
        val ids = List(3) { saveProduct(stock = ABUNDANT_STOCK) }

        // when
        val results =
            runConcurrently(
                List(GROUP_SIZE) { decrease(ids) } + List(GROUP_SIZE) { decrease(ids.reversed()) },
            )

        // then - UPDATE 한 방이라 InnoDB가 PK 오름차순으로 잠근다. 요청 순서와 무관하게 두 그룹의 락 순서가 같다
        assertNoDeadlock(results)
        ids.forEach { stockOf(it) shouldBe ABUNDANT_STOCK - GROUP_SIZE * 2 }
    }

    @Test
    fun `상품 목록이 일부만 겹치고 순서가 반대여도 데드락 없이 전부 성공한다`() {
        // given - 겹치는 구간(2, 3)을 두 그룹이 정반대 순서로 잡으러 간다
        val ids = List(6) { saveProduct(stock = ABUNDANT_STOCK) }
        val ascending = ids.subList(0, 4)
        val descending = ids.subList(2, 6).reversed()

        // when
        val results =
            runConcurrently(
                List(GROUP_SIZE) { decrease(ascending) } + List(GROUP_SIZE) { decrease(descending) },
            )

        // then
        assertNoDeadlock(results)
        listOf(ids[0], ids[1], ids[4], ids[5]).forEach { stockOf(it) shouldBe ABUNDANT_STOCK - GROUP_SIZE }
        withClue("겹치는 상품은 양쪽 그룹만큼 줄어야 합니다") {
            listOf(ids[2], ids[3]).forEach { stockOf(it) shouldBe ABUNDANT_STOCK - GROUP_SIZE * 2 }
        }
    }

    private fun assertNoDeadlock(results: List<Result<Unit>>) {
        val failures = results.mapNotNull { it.exceptionOrNull() }
        withClue("데드락 희생자가 없어야 합니다 : ${failures.map { it::class.simpleName }}") {
            failures.count { it is StockUnstableException } shouldBe 0
        }
        failures.size shouldBe 0
    }

    /**
     * 요청에 담긴 상품 순서를 그대로 유지한다. 상품별 UPDATE로 쪼개는 순간 이 순서가 락 순서가 되어 데드락이 난다.
     */
    private fun decrease(ids: List<Long>): () -> Unit = {
        stockWriter.decrease(ids.map { StockWriter.StockChange(it, 1L) })
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

    private fun saveProduct(stock: Long): Long =
        jpaProductRepository
            .save(
                Product(
                    ownerId = sellerId,
                    name = "Test Product",
                    code = ProductCode("MYSQLLOCK%03d".format(codeSequence.incrementAndGet())),
                    price = Money.of(10000),
                    stock = stock,
                ),
            ).id

    private fun stockOf(id: Long): Long = jpaProductRepository.findById(id).orElseThrow().stock

    companion object {
        private const val ABUNDANT_STOCK = 1000L
        private const val GROUP_SIZE = 10
        private const val READY_TIMEOUT_SECONDS = 10L
    }
}
