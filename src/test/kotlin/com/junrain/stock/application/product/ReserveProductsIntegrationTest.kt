package com.junrain.stock.application.product

import com.junrain.stock.application.product.ReserveProducts.Command
import com.junrain.stock.application.product.ReserveProducts.Command.Change
import com.junrain.stock.config.RedisTestContainersConfig.Companion.redisProxy
import com.junrain.stock.config.StockProbe
import com.junrain.stock.config.awaitRecovered
import com.junrain.stock.domain.common.Money
import com.junrain.stock.domain.member.Member
import com.junrain.stock.domain.member.MemberRepository
import com.junrain.stock.domain.member.MemberType
import com.junrain.stock.domain.product.Product
import com.junrain.stock.domain.product.ProductRepository
import com.junrain.stock.domain.product.exception.StockUnavailableException
import com.junrain.stock.domain.product.exception.StockUnstableException
import com.junrain.stock.domain.product.vo.ProductCode
import com.junrain.stock.domain.reservation.ReservationRepository
import com.junrain.stock.infra.product.mysql.JpaProductRepository
import eu.rekawek.toxiproxy.model.ToxicDirection
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDateTime

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

    @Autowired
    private lateinit var redissonClient: RedissonClient

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
        stockProbe.clearExpireIndex()
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
        val saved =
            withClue("발급된 trxId로 조회되어야 합니다") {
                reservationRepository.findByTrxId(result.trxId).shouldNotBeNull()
            }

        saved.expireAt shouldBeAfter LocalDateTime.now()
        withClue("되돌릴 수량이 Redis에 자기완결적으로 남아야 합니다") {
            stockProbe.reservationBody(result.trxId) shouldBe mapOf("available_stock:$productId" to "$RESERVE_QUANTITY")
        }
        withClue("만료 회수 대상으로 등재돼야 합니다") {
            stockProbe.expireIndexMembers() shouldBe setOf(result.trxId)
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
    fun `여러 상품 중 하나라도 실패하면 재고가 그대로고 Reservation도 남지 않는다`() {
        // given
        val otherId = saveProduct("RESERVE003")

        // when & then
        shouldThrow<StockUnavailableException> {
            reserveProducts(Command(listOf(Change(productId, RESERVE_QUANTITY), Change(otherId, initialStock + 1))))
        }

        withClue("Lua가 전부 검사한 뒤에 차감하므로 부분 차감이 없어야 합니다") {
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

    /**
     * 재고를 먼저 잡고 예약을 기록한다. 그래서 응답을 못 받으면 남는 것은 "행 없이 잡힌 재고"뿐이고,
     * 그 회수 진입점은 MySQL이 아니라 Redis의 만료 인덱스다. 적용 여부를 판정할 이유가 없다.
     */
    @Nested
    @Tag("fault")
    inner class NetworkFault {
        @Test
        fun `Redis 응답이 끊기면 재시도 가능 예외를 던지고 회수 진입점을 Redis에 남긴다`() {
            val toxic = redisProxy.toxics().timeout("timeout", ToxicDirection.DOWNSTREAM, 0)
            try {
                shouldThrow<StockUnstableException> {
                    reserveProducts(Command(listOf(Change(productId, RESERVE_QUANTITY))))
                }
            } finally {
                toxic.remove()
                redissonClient.awaitRecovered()
            }

            withClue("응답만 못 받았을 뿐 차감은 적용됐습니다") {
                currentStock() shouldBe initialStock - RESERVE_QUANTITY
            }
            withClue("Redis를 먼저 부르므로 예약 행은 남지 않습니다") {
                reservationRepository.count() shouldBe 0
            }

            val trxId = stockProbe.expireIndexMembers().single()
            withClue("등재된 기록만으로 되돌릴 수 있어야 합니다") {
                stockProbe.reservationBody(trxId) shouldBe mapOf("available_stock:$productId" to "$RESERVE_QUANTITY")
            }
        }
    }

    private infix fun LocalDateTime.shouldBeAfter(other: LocalDateTime) = withClue("$this 는 $other 이후여야 합니다") { isAfter(other) shouldBe true }


    companion object {
        private const val RESERVE_QUANTITY = 10L
        private const val OTHER_QUANTITY = 5L
        private const val REPEAT_COUNT = 3
        private const val MISSING_ID_OFFSET = 9999L
    }
}
