package com.example.demo.product.application

import com.example.demo.common.IntegrationTestBase
import com.example.demo.member.command.domain.Member
import com.example.demo.member.command.domain.MemberRepository
import com.example.demo.member.command.domain.MemberType
import com.example.demo.product.command.application.ProductPurchaseService
import com.example.demo.product.command.application.dto.PurchaseProductCommand
import com.example.demo.product.command.domain.Product
import com.example.demo.product.command.domain.ProductRepository
import com.example.demo.global.contract.vo.Money
import com.example.demo.product.command.domain.vo.ProductCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProductPurchaseServiceAtomicityTest : IntegrationTestBase() {

    @Autowired
    private lateinit var productPurchaseService: ProductPurchaseService

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    private lateinit var testMember: Member

    @BeforeEach
    fun setUp() {
        productRepository.deleteAll()
        memberRepository.deleteAll()
        testMember = memberRepository.save(Member(memberType = MemberType.SELLER, name = "Test Seller"))
    }

    @AfterEach
    fun tearDown() {
        productRepository.deleteAll()
        memberRepository.deleteAll()
    }

    @Test
    fun `여러 상품을 한 번에 구매할 때 모두 성공하거나 모두 실패해야 한다 - 원자성 보장`() {
        // given: 세 개의 상품 생성 (재고 각각 100개)
        val product1 = productRepository.save(
            Product(
                ownerId = testMember.id,
                name = "상품1",
                code = ProductCode("ATOMIC001"),
                price = Money.of(10000),
                stock = 100
            )
        )
        val product2 = productRepository.save(
            Product(
                ownerId = testMember.id,
                name = "상품2",
                code = ProductCode("ATOMIC002"),
                price = Money.of(20000),
                stock = 100
            )
        )
        val product3 = productRepository.save(
            Product(
                ownerId = testMember.id,
                name = "상품3",
                code = ProductCode("ATOMIC003"),
                price = Money.of(30000),
                stock = 100
            )
        )

        // when: 세 상품을 동시에 구매
        val commands = listOf(
            PurchaseProductCommand(product1.id, 10),
            PurchaseProductCommand(product2.id, 20),
            PurchaseProductCommand(product3.id, 30)
        )

        productPurchaseService.decreaseStock(commands)

        // then: 모든 상품의 재고가 정확하게 감소해야 함
        val result1 = productRepository.findById(product1.id).get()
        val result2 = productRepository.findById(product2.id).get()
        val result3 = productRepository.findById(product3.id).get()

        assertEquals(90L, result1.stock)
        assertEquals(80L, result2.stock)
        assertEquals(70L, result3.stock)
    }

    @Test
    fun `여러 상품 중 하나라도 재고가 부족하면 전체가 롤백되어야 한다`() {
        // given: 세 개의 상품 생성 (product2의 재고만 부족)
        val product1 = productRepository.save(
            Product(
                ownerId = testMember.id,
                name = "상품1",
                code = ProductCode("ROLLBACK001"),
                price = Money.of(10000),
                stock = 100
            )
        )
        val product2 = productRepository.save(
            Product(
                ownerId = testMember.id,
                name = "상품2",
                code = ProductCode("ROLLBACK002"),
                price = Money.of(20000),
                stock = 5  // 재고 부족
            )
        )
        val product3 = productRepository.save(
            Product(
                ownerId = testMember.id,
                name = "상품3",
                code = ProductCode("ROLLBACK003"),
                price = Money.of(30000),
                stock = 100
            )
        )

        // when & then: 예외가 발생해야 함
        val commands = listOf(
            PurchaseProductCommand(product1.id, 10),
            PurchaseProductCommand(product2.id, 10),  // 재고 부족으로 실패
            PurchaseProductCommand(product3.id, 30)
        )

        try {
            productPurchaseService.decreaseStock(commands)
            throw AssertionError("재고 부족 예외가 발생해야 합니다")
        } catch (e: IllegalArgumentException) {
            // 예외 발생 확인
            assertTrue(e.message!!.contains("재고가 없습니다"))
        }

        // then: 모든 상품의 재고가 원래대로 유지되어야 함 (롤백)
        val result1 = productRepository.findById(product1.id).get()
        val result2 = productRepository.findById(product2.id).get()
        val result3 = productRepository.findById(product3.id).get()

        assertEquals(100L, result1.stock, "product1의 재고는 롤백되어 100이어야 합니다")
        assertEquals(5L, result2.stock, "product2의 재고는 원래대로 5여야 합니다")
        assertEquals(100L, result3.stock, "product3의 재고는 롤백되어 100이어야 합니다")
    }

    @Test
    fun `동시에 같은 여러 상품을 구매할 때 데드락 없이 원자적으로 처리되어야 한다`() {
        // given: 두 개의 상품 생성
        val product1 = productRepository.save(
            Product(
                ownerId = testMember.id,
                name = "상품1",
                code = ProductCode("DEADLOCK001"),
                price = Money.of(10000),
                stock = 100
            )
        )
        val product2 = productRepository.save(
            Product(
                ownerId = testMember.id,
                name = "상품2",
                code = ProductCode("DEADLOCK002"),
                price = Money.of(20000),
                stock = 100
            )
        )

        val threadCount = 20
        val executorService = Executors.newFixedThreadPool(20)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        // when: 여러 스레드가 동시에 두 상품을 역순으로 구매 시도
        // Thread 1: [product1, product2]
        // Thread 2: [product2, product1]
        // -> ORDER BY를 사용하지 않으면 데드락 발생 가능
        repeat(threadCount) { i ->
            executorService.submit {
                try {
                    val commands = if (i % 2 == 0) {
                        // 짝수 스레드: product1 -> product2 순서
                        listOf(
                            PurchaseProductCommand(product1.id, 2),
                            PurchaseProductCommand(product2.id, 3)
                        )
                    } else {
                        // 홀수 스레드: product2 -> product1 순서
                        listOf(
                            PurchaseProductCommand(product2.id, 3),
                            PurchaseProductCommand(product1.id, 2)
                        )
                    }

                    productPurchaseService.decreaseStock(commands)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    failCount.incrementAndGet()
                    println("Purchase failed: ${e.javaClass.simpleName} - ${e.message}")
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executorService.shutdown()

        // then: 모든 구매가 성공해야 함 (데드락이 발생하지 않음)
        println("Success: ${successCount.get()}, Fail: ${failCount.get()}")
        assertEquals(threadCount, successCount.get(), "데드락 없이 모든 구매가 성공해야 합니다")

        // 재고 확인
        val result1 = productRepository.findById(product1.id).get()
        val result2 = productRepository.findById(product2.id).get()

        assertEquals(100L - (threadCount * 2L), result1.stock)
        assertEquals(100L - (threadCount * 3L), result2.stock)
    }

    @Test
    fun `동시에 같은 상품 목록을 구매할 때 Lost Update가 발생하지 않아야 한다`() {
        // given: 세 개의 상품 생성
        val product1 = productRepository.save(
            Product(
                ownerId = testMember.id,
                name = "상품1",
                code = ProductCode("LOSTUPDATE001"),
                price = Money.of(10000),
                stock = 500
            )
        )
        val product2 = productRepository.save(
            Product(
                ownerId = testMember.id,
                name = "상품2",
                code = ProductCode("LOSTUPDATE002"),
                price = Money.of(20000),
                stock = 500
            )
        )
        val product3 = productRepository.save(
            Product(
                ownerId = testMember.id,
                name = "상품3",
                code = ProductCode("LOSTUPDATE003"),
                price = Money.of(30000),
                stock = 500
            )
        )

        val threadCount = 50
        val executorService = Executors.newFixedThreadPool(32)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)

        // when: 50개의 스레드가 동시에 세 상품을 구매
        repeat(threadCount) {
            executorService.submit {
                try {
                    val commands = listOf(
                        PurchaseProductCommand(product1.id, 3),
                        PurchaseProductCommand(product2.id, 5),
                        PurchaseProductCommand(product3.id, 7)
                    )

                    productPurchaseService.decreaseStock(commands)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    println("Purchase failed: ${e.message}")
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executorService.shutdown()

        // then: 재고가 정확하게 감소해야 함 (Lost Update 없음)
        val result1 = productRepository.findById(product1.id).get()
        val result2 = productRepository.findById(product2.id).get()
        val result3 = productRepository.findById(product3.id).get()

        val expectedStock1 = 500L - (successCount.get() * 3L)
        val expectedStock2 = 500L - (successCount.get() * 5L)
        val expectedStock3 = 500L - (successCount.get() * 7L)

        assertEquals(expectedStock1, result1.stock, "product1의 재고는 정확히 ${expectedStock1}이어야 합니다")
        assertEquals(expectedStock2, result2.stock, "product2의 재고는 정확히 ${expectedStock2}이어야 합니다")
        assertEquals(expectedStock3, result3.stock, "product3의 재고는 정확히 ${expectedStock3}이어야 합니다")
    }

    @Test
    fun `높은 동시성 환경에서 여러 상품을 동시 구매할 때 재고 부족으로 일부만 성공해야 한다`() {
        // given: 세 개의 상품 생성 (재고 제한적)
        val product1 = productRepository.save(
            Product(
                ownerId = testMember.id,
                name = "상품1",
                code = ProductCode("HIGHCON001"),
                price = Money.of(10000),
                stock = 50
            )
        )
        val product2 = productRepository.save(
            Product(
                ownerId = testMember.id,
                name = "상품2",
                code = ProductCode("HIGHCON002"),
                price = Money.of(20000),
                stock = 50
            )
        )
        val product3 = productRepository.save(
            Product(
                ownerId = testMember.id,
                name = "상품3",
                code = ProductCode("HIGHCON003"),
                price = Money.of(30000),
                stock = 50
            )
        )

        val threadCount = 30
        val executorService = Executors.newFixedThreadPool(30)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        // when: 30개의 스레드가 동시에 각 상품을 2개씩 구매 시도
        // 예상: 최대 25번만 성공 (50 / 2 = 25)
        repeat(threadCount) {
            executorService.submit {
                try {
                    val commands = listOf(
                        PurchaseProductCommand(product1.id, 2),
                        PurchaseProductCommand(product2.id, 2),
                        PurchaseProductCommand(product3.id, 2)
                    )

                    productPurchaseService.decreaseStock(commands)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    failCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executorService.shutdown()

        // then
        println("Success: ${successCount.get()}, Fail: ${failCount.get()}")
        assertEquals(25, successCount.get(), "재고가 50개이므로 2개씩 25번만 성공해야 합니다")
        assertEquals(5, failCount.get(), "나머지 5번은 재고 부족으로 실패해야 합니다")

        // 재고 확인: 모두 0이어야 함
        val result1 = productRepository.findById(product1.id).get()
        val result2 = productRepository.findById(product2.id).get()
        val result3 = productRepository.findById(product3.id).get()

        assertEquals(0L, result1.stock, "product1의 재고는 0이어야 합니다")
        assertEquals(0L, result2.stock, "product2의 재고는 0이어야 합니다")
        assertEquals(0L, result3.stock, "product3의 재고는 0이어야 합니다")
    }
}
