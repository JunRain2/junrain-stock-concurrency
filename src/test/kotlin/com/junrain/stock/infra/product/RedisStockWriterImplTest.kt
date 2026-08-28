package com.junrain.stock.infra.product

import com.junrain.stock.application.product.port.StockWriter
import com.junrain.stock.config.RedisTestContainersConfig.Companion.redisProxy
import com.junrain.stock.config.awaitRecovered
import com.junrain.stock.domain.product.exception.ProductNotFoundException
import com.junrain.stock.domain.product.exception.StockUnavailableException
import com.junrain.stock.domain.product.exception.StockUnstableException
import eu.rekawek.toxiproxy.model.ToxicDirection
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.assertThrows
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 이 층에서만 확인할 수 있는 것만 남긴다.
 *
 * 정상 차감/없는 상품/중복 거부/동시 요청은 [com.junrain.stock.application.product.ReserveProductsIntegrationTest]와
 * [com.junrain.stock.application.product.ReserveProductsConcurrencyTest]가 유스케이스 층에서 이미 검증한다.
 */
@SpringBootTest(properties = ["stock.strategy=redis"])
class RedisStockWriterImplTest
    @Autowired
    constructor(
        private val stockWriter: RedisStockWriterImpl,
        private val redissonClient: RedissonClient,
        private val transactionTemplate: TransactionTemplate,
    ) {
        @BeforeEach
        fun beforeEach() {
            redissonClient.keys.flushall()
        }

        private fun seed(
            productId: Long,
            quantity: Long,
        ) = redissonClient.getAtomicLong(stockKey(productId)).set(quantity)

        private fun stockOf(productId: Long) = redissonClient.getAtomicLong(stockKey(productId)).get()

        private fun stockKey(productId: Long) = "product_stock:$productId"

        private fun change(
            productId: Long,
            quantity: Long,
        ) = StockWriter.StockChange(productId, quantity)

        @Test
        fun `하나라도 재고가 모자라면 아무것도 감소하지 않는다`() {
            seed(1, 10)
            seed(2, 1)
            seed(3, 30)

            assertThrows<StockUnavailableException> {
                stockWriter.decrease(listOf(change(1, 1), change(2, 2), change(3, 3)))
            }

            // Lua가 전부 검사한 뒤에 차감하므로 부분 차감이 생기면 안 된다
            assertEquals(10, stockOf(1))
            assertEquals(1, stockOf(2))
            assertEquals(30, stockOf(3))
        }

        @Test
        fun `차감을 감싼 트랜잭션이 롤백돼도 재고는 그대로 빠져 있다`() {
            // 의도된 언더셀이다. 인라인 보상을 두면 정합 배치와 함께 이중 증가 = 오버셀이 되므로
            // 증가 주체를 StockReconciler 하나로 묶었다. 되돌리는 건 그쪽 몫이다
            seed(1, 10)

            assertThrows<IllegalStateException> {
                transactionTemplate.execute {
                    stockWriter.decrease(listOf(change(1, 3)))
                    error("Reservation 저장 실패")
                }
            }

            assertEquals(7, stockOf(1))
        }

        @Test
        fun `없는 상품이 섞이면 증가도 전부 취소된다`() {
            // 부분 증가는 되돌릴 방법이 없다. 되돌리려면 감소해야 하고 그게 곧 오버셀이다
            seed(1, 10)

            assertThrows<ProductNotFoundException> {
                stockWriter.increase(listOf(change(1, 5), change(2, 5)))
            }

            assertEquals(10, stockOf(1))
        }

        @Nested
        @Tag("fault")
        inner class NetworkFault {
            @Test
            fun `Redis 응답이 끊기면 재시도 가능 예외로 바꾼다`() {
                // 적용 여부는 판정하지 않는다. 적용됐다면 언더셀로 남고 정합 배치가 되돌린다
                seed(1, 10)

                val toxic = redisProxy.toxics().timeout("timeout", ToxicDirection.DOWNSTREAM, 0)
                try {
                    assertThrows<StockUnstableException> { stockWriter.decrease(listOf(change(1, 1))) }
                } finally {
                    toxic.remove()
                    redissonClient.awaitRecovered()
                }
            }
        }
    }
