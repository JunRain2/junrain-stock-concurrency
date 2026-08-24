package com.junrain.stock.application.product

import com.junrain.stock.application.product.ReserveProducts.Command
import com.junrain.stock.application.product.ReserveProducts.Command.Change
import com.junrain.stock.domain.common.Money
import com.junrain.stock.domain.member.Member
import com.junrain.stock.domain.member.MemberRepository
import com.junrain.stock.domain.member.MemberType
import com.junrain.stock.domain.product.Product
import com.junrain.stock.domain.product.ProductRepository
import com.junrain.stock.domain.product.exception.StockUnavailableException
import com.junrain.stock.domain.product.vo.ProductCode
import com.junrain.stock.domain.reservation.ReservationRepository
import com.junrain.stock.support.StockProbe
import com.junrain.stock.infra.product.mysql.JpaProductRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ReserveProductsIntegrationTest {
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

    private var productId: Long = 0
    private var sellerId: Long = 0
    private val initialStock = 100L

    @BeforeEach
    fun setUp() {
        sellerId = memberRepository.save(Member(memberType = MemberType.SELLER, name = "Test Seller")).id
        productId = saveProduct("RESERVE001")
    }

    @AfterEach
    fun tearDown() {
        reservationRepository.deleteAll()
        jpaProductRepository.deleteAll()
        memberRepository.deleteAll()
    }

    // 포트로 저장한다. 재고를 어디에 심는지는 구현체가 정한다
    private fun saveProduct(code: String): Long =
        productRepository
            .save(
                Product(
                    ownerId = sellerId,
                    name = "Test Product",
                    code = ProductCode(code),
                    price = Money.of(10000),
                    stock = initialStock,
                ),
            ).id

    private fun stockOf(id: Long): Long = stockProbe.stockOf(id)

    private fun currentStock(): Long = stockOf(productId)

    @Test
    fun `예약에 성공하면 재고가 감소하고 Reservation이 생성된다`() {
        // when
        val result = reserveProducts(Command(listOf(Change(productId, RESERVE_QUANTITY))))

        // then
        currentStock() shouldBe initialStock - RESERVE_QUANTITY
        withClue("발급된 trxId로 조회되어야 합니다") {
            reservationRepository.findByTrxId(result.trxId).shouldNotBeNull()
        }
    }

    @Test
    fun `여러 상품을 한 번에 예약하면 각 재고가 줄고 items가 그대로 저장된다`() {
        // given
        val otherId = saveProduct("RESERVE002")

        // when
        val result =
            reserveProducts(Command(listOf(Change(productId, RESERVE_QUANTITY), Change(otherId, OTHER_QUANTITY))))

        // then
        currentStock() shouldBe initialStock - RESERVE_QUANTITY
        stockOf(otherId) shouldBe initialStock - OTHER_QUANTITY

        // JSON 컬럼에 담긴 항목이 요청 그대로 복원되어야 한다
        val saved = reservationRepository.findByTrxId(result.trxId).shouldNotBeNull()
        saved.items.associate { it.productId to it.quantity } shouldBe
            mapOf(productId to RESERVE_QUANTITY, otherId to OTHER_QUANTITY)
    }

    @Test
    fun `재고가 모자라면 예외가 발생하고 재고와 Reservation 모두 그대로다`() {
        // when & then
        shouldThrow<StockUnavailableException> { reserveProducts(Command(listOf(Change(productId, initialStock + 1)))) }

        currentStock() shouldBe initialStock
        withClue("실패한 요청은 Reservation을 남기지 않아야 합니다") {
            reservationRepository.count() shouldBe 0
        }
    }

    @Test
    fun `없는 상품을 예약하면 예외가 발생하고 Reservation이 생성되지 않는다`() {
        // given - 저장된 적 없는 id
        val missingId = productId + MISSING_ID_OFFSET

        // when & then - 상품 없음과 재고 부족을 구분하지 않으므로 StockUnavailableException이다
        shouldThrow<StockUnavailableException> { reserveProducts(Command(listOf(Change(missingId, 1L)))) }

        reservationRepository.count() shouldBe 0
    }

    @Test
    fun `여러 상품 중 하나라도 실패하면 재고와 Reservation이 전부 롤백된다`() {
        // given
        val otherId = saveProduct("RESERVE003")

        // when & then
        shouldThrow<StockUnavailableException> {
            reserveProducts(Command(listOf(Change(productId, RESERVE_QUANTITY), Change(otherId, initialStock + 1))))
        }

        withClue("성공했던 상품도 롤백되어야 합니다") {
            currentStock() shouldBe initialStock
        }
        stockOf(otherId) shouldBe initialStock
        reservationRepository.count() shouldBe 0
    }

    @Test
    fun `연속으로 예약하면 재고가 누적 감소하고 trxId는 매번 새로 발급된다`() {
        // when
        val trxIds =
            (1..REPEAT_COUNT).map { reserveProducts(Command(listOf(Change(productId, RESERVE_QUANTITY)))).trxId }

        // then
        currentStock() shouldBe initialStock - RESERVE_QUANTITY * REPEAT_COUNT
        withClue("trxId는 요청마다 달라야 합니다") {
            trxIds.distinct().size shouldBe REPEAT_COUNT
        }
        reservationRepository.count() shouldBe REPEAT_COUNT.toLong()
    }

    @Test
    fun `Command는 빈 목록과 중복 상품을 거부한다`() {
        shouldThrow<IllegalArgumentException> { Command(emptyList()) }
        shouldThrow<IllegalArgumentException> {
            Command(listOf(Change(productId, RESERVE_QUANTITY), Change(productId, OTHER_QUANTITY)))
        }

        withClue("유스케이스에 도달하기 전에 막혀야 합니다") {
            currentStock() shouldBe initialStock
        }
    }

    companion object {
        private const val RESERVE_QUANTITY = 10L
        private const val OTHER_QUANTITY = 5L
        private const val REPEAT_COUNT = 3
        private const val MISSING_ID_OFFSET = 9999L
    }
}
