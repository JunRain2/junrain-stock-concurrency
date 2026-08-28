package com.junrain.stock.infra.product.redis

import com.junrain.stock.application.product.ReserveProducts
import com.junrain.stock.application.product.ReserveProducts.Command
import com.junrain.stock.application.product.ReserveProducts.Command.Change
import com.junrain.stock.config.StockProbe
import com.junrain.stock.domain.common.Money
import com.junrain.stock.domain.member.Member
import com.junrain.stock.domain.member.MemberRepository
import com.junrain.stock.domain.member.MemberType
import com.junrain.stock.domain.product.Product
import com.junrain.stock.domain.product.ProductRepository
import com.junrain.stock.domain.product.vo.ProductCode
import com.junrain.stock.domain.reservation.ReservationRepository
import com.junrain.stock.infra.product.mysql.JpaProductRepository
import io.kotest.assertions.withClue
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.support.TransactionTemplate

/**
 * 정합 배치는 "어느 연산이 샜는가"를 판정하지 않는다. 상품별 총량만 다시 계산한다.
 *
 * 유예를 3초로, 점유 트랜잭션 상한을 2초로 줄여 돌린다. 둘의 대소 관계가 안전성의 전제이므로 함께 줄인다.
 */
@SpringBootTest(
    properties = [
        "stock.strategy=redis",
        "stock.reconcile.grace-seconds=3",
        "stock.reserve.timeout-seconds=2",
        "stock.reconcile.suspect-delay-seconds=3600",
    ],
)
class StockReconcilerTest
    @Autowired
    constructor(
        private val reconciler: StockReconciler,
        private val reserveProducts: ReserveProducts,
        private val productRepository: ProductRepository,
        private val jpaProductRepository: JpaProductRepository,
        private val reservationRepository: ReservationRepository,
        private val memberRepository: MemberRepository,
        private val redissonClient: RedissonClient,
        private val stockProbe: StockProbe,
        private val transactionTemplate: TransactionTemplate,
        private val stockSuspects: StockSuspects,
    ) {
        private var productId: Long = 0
        private val initialStock = 100L

        @BeforeEach
        fun setUp() {
            redissonClient.keys.flushall()
            stockSuspects.drain()
            val sellerId = memberRepository.save(Member(memberType = MemberType.SELLER, name = "Test Seller")).id
            productId =
                productRepository
                    .save(
                        Product(
                            ownerId = sellerId,
                            name = "Test Product",
                            code = ProductCode("RECONCILE001"),
                            price = Money.of(10000),
                            stock = initialStock,
                        ),
                    ).id
        }

        @AfterEach
        fun tearDown() {
            reservationRepository.deleteAll()
            jpaProductRepository.deleteAll()
            memberRepository.deleteAll()
        }

        private fun stock() = stockProbe.stockOf(productId)

        /** 예약 없이 재고만 깎아 언더셀 누수를 만든다. 타임아웃으로 응답만 잃은 차감과 같은 상태다 */
        private fun leak(quantity: Long) = redissonClient.getAtomicLong("product_stock:$productId").addAndGet(-quantity)

        @Test
        fun `예약 없이 사라진 재고를 되돌린다`() {
            leak(5)

            reconciler.reconcileAll()

            stock() shouldBe initialStock
        }

        @Test
        fun `예약이 한 건도 없이 재고 전량이 사라져도 되돌린다`() {
            // 누수의 정체는 Reservation의 부재다. 예약 쪽에서 후보를 뽑으면 이 상품은 행이 없어 영영 안 잡힌다.
            // 그래서 스캔 대상이 products다 - "있어야 할 양"을 아는 쪽이 거기다
            leak(initialStock) // 전량 차감 후 타임아웃, Reservation 없음

            stock() shouldBe 0 // 팔린 것 없이 품절 상태

            reconciler.reconcileAll()

            stock() shouldBe initialStock
        }

        @Test
        fun `예약과 재고가 맞으면 아무것도 건드리지 않는다`() {
            reserveProducts(Command(listOf(Change(productId, 3))))

            reconciler.reconcileAll()

            stock() shouldBe initialStock - 3
        }

        @Test
        fun `유예 중에 들어온 점유는 오버셀을 만들지 않는다`() {
            // Redis 스냅샷 뒤, 예약 합계를 읽기 전에 점유가 끼어드는 창이 안전성의 핵심이다
            leak(5)

            val running = Thread { reconciler.reconcileAll() }.apply { start() }
            Thread.sleep(500)
            reserveProducts(Command(listOf(Change(productId, 3))))
            running.join()

            // 스냅샷에 없던 점유 때문에 delta를 과소평가한다. 덜 되돌리는 건 언더셀이라 허용된다
            withClue("되돌린 재고가 팔 수 있는 양을 넘으면 오버셀이다") {
                stock() shouldBeLessThanOrEqual initialStock - 3
            }

            // 다음 회차가 남은 몫을 마저 되돌린다
            reconciler.reconcileAll()

            stock() shouldBe initialStock - 3
        }

        @Test
        fun `스냅샷 뒤에 커밋되는 점유도 오버셀을 만들지 않는다`() {
            // 차감은 커밋보다 먼저 일어난다. 그래서 "차감은 스냅샷에 잡혔는데 예약은 아직 안 보이는" 창이 생긴다.
            // 이 창을 누수로 오인해 되돌리면, 뒤이어 커밋되는 순간 재고가 본래보다 많아진다 = 오버셀.
            // 유예가 점유 트랜잭션 상한보다 길다는 전제가 이걸 막는다
            leak(5)

            val slowReserve =
                Thread {
                    transactionTemplate.execute {
                        reserveProducts(Command(listOf(Change(productId, 3))))
                        Thread.sleep(600) // 차감은 끝났고 커밋만 남은 상태
                    }
                }.apply { start() }

            Thread.sleep(200) // 차감이 스냅샷보다 먼저 잡히도록
            reconciler.reconcileAll()
            slowReserve.join()

            // 누수 5만 되돌아오고, 늦게 커밋된 점유 3은 그대로 빠져 있어야 한다
            stock() shouldBe initialStock - 3
        }

        @Test
        fun `롤백된 차감은 표시되고 빠른 경로가 그것만 회수한다`() {
            // 전수 스캔을 기다리지 않는다. 샐 수 있었던 순간을 프로세스가 알고 있으므로 그 상품만 확인한다
            assertThrows<IllegalStateException> {
                transactionTemplate.execute {
                    reserveProducts(Command(listOf(Change(productId, 3))))
                    error("Reservation 저장 실패")
                }
            }

            stock() shouldBe initialStock - 3 // 롤백은 Redis를 되돌리지 않는다

            reconciler.reconcileSuspects()

            stock() shouldBe initialStock
        }

        @Test
        fun `표시가 없으면 빠른 경로는 아무 일도 하지 않는다`() {
            // 평시 비용이 0이어야 한다. 타임아웃이 없으면 쿼리조차 나가지 않는다
            leak(5)

            reconciler.reconcileSuspects()

            stock() shouldBe initialStock - 5
        }
    }
