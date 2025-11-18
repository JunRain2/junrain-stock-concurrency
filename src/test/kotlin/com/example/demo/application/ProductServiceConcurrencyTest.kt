package com.example.demo.application

import com.example.demo.domain.product.Product
import com.example.demo.domain.product.ProductRepository
import com.example.demo.domain.product.vo.Money
import com.example.demo.domain.product.vo.ProductCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

@SpringBootTest
class ProductServiceConcurrencyTest {

    @Autowired
    private lateinit var productService: ProductService

    @Autowired
    private lateinit var productRepository: ProductRepository

    @BeforeEach
    fun setUp() {
        productRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        productRepository.deleteAll()
    }

    @Test
    fun `동시에 100개의 구매 요청이 들어올 때 재고 감소가 정확하게 처리되어야 한다`() {
        // given: 초기 재고 100개인 상품 생성
        val initialStock = 100
        val product = productRepository.save(
            Product(
                name = "테스트 상품", code = ProductCode("TEST001"), price = Money.of(10000), stock = initialStock.toLong()
            )
        )

        val threadCount = 100
        val executorService = Executors.newFixedThreadPool(32)
        val latch = CountDownLatch(threadCount)

        // when: 100개의 스레드가 동시에 1개씩 구매
        repeat(threadCount) {
            executorService.submit {
                try {
                    productService.purchaseWithLock(product.id, 1)
                } catch (e: Exception) {
                    println("Purchase failed: ${e.message}")
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executorService.shutdown()

        // then: 재고가 0이 되어야 함
        val result = productRepository.findById(product.id).get()
        assertEquals(0L, result.stock, "재고는 0이 되어야 합니다")
    }

    @Test
    fun `동시에 구매 요청이 들어왔을 때 재고보다 많은 수량을 구매하면 일부는 실패해야 한다`() {
        // given: 초기 재고 10개인 상품 생성
        val initialStock = 10
        val product = productRepository.save(
            Product(
                name = "테스트 상품",
                code = ProductCode("TEST002"),
                price = Money.of(10000),
                stock = initialStock.toLong()
            )
        )

        val threadCount = 20
        val executorService = Executors.newFixedThreadPool(20)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        // when: 20개의 스레드가 동시에 1개씩 구매 시도
        repeat(threadCount) {
            executorService.submit {
                try {
                    productService.purchaseWithLock(product.id, 1)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    failCount.incrementAndGet()
                    println("Purchase failed: ${e.message}")
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executorService.shutdown()

        // then: 성공한 구매는 10개, 실패한 구매는 10개여야 함
        println("Success: ${successCount.get()}, Fail: ${failCount.get()}")
        assertEquals(initialStock, successCount.get(), "성공한 구매는 초기 재고 수량과 같아야 합니다")
        assertEquals(threadCount - initialStock, failCount.get(), "실패한 구매는 (전체 시도 - 재고)와 같아야 합니다")

        // 최종 재고는 0이어야 함
        val result = productRepository.findById(product.id).get()
        assertEquals(0L, result.stock, "재고는 0이 되어야 합니다")
    }

    @Test
    fun `여러 스레드가 서로 다른 수량으로 동시 구매할 때 재고가 정확하게 감소해야 한다`() {
        // given: 초기 재고 1000개인 상품 생성
        val initialStock = 1000
        val product = productRepository.save(
            Product(
                name = "테스트 상품",
                code = ProductCode("TEST003"),
                price = Money.of(10000),
                stock = initialStock.toLong()
            )
        )

        val threadCount = 100
        val quantityPerPurchase = 5L
        val executorService = Executors.newFixedThreadPool(32)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)

        // when: 100개의 스레드가 동시에 5개씩 구매
        repeat(threadCount) {
            executorService.submit {
                try {
                    productService.purchaseWithLock(product.id, quantityPerPurchase)
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

        // then: 재고가 정확하게 감소해야 함
        val expectedRemainingStock = initialStock.toLong() - (successCount.get() * quantityPerPurchase)
        val result = productRepository.findById(product.id).get()
        assertEquals(
            expectedRemainingStock,
            result.stock,
            "재고는 ${expectedRemainingStock}이 되어야 합니다. (초기: $initialStock, 성공: ${successCount.get()}회 x ${quantityPerPurchase}개)"
        )
    }
}
