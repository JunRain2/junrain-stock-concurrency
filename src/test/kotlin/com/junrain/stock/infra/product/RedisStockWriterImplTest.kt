package com.junrain.stock.infra.product

import com.junrain.stock.application.product.port.StockWriter
import com.junrain.stock.config.RedisTestContainersConfig.Companion.redisProxy
import com.junrain.stock.config.awaitRecovered
import com.junrain.stock.domain.product.exception.StockUnavailableException
import com.junrain.stock.domain.product.exception.StockUnstableException
import eu.rekawek.toxiproxy.model.ToxicDirection
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.assertThrows
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 이 층에서만 확인할 수 있는 것만 남긴다.
 *
 * 정상 차감/없는 상품/중복 거부/동시 요청은 [com.junrain.stock.application.product.ReserveProductsIntegrationTest]와
 * [com.junrain.stock.application.product.ReserveProductsConcurrencyTest]가 유스케이스 층에서 이미 검증한다.
 */
@SpringBootTest
class RedisStockWriterImplTest
    @Autowired
    constructor(
        private val stockWriter: RedisStockWriterImpl,
        private val redissonClient: RedissonClient,
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

        private fun stockKey(productId: Long) = "available_stock:$productId"

        private fun body(trxId: String) = redissonClient.getMap<String, String>("reservation:$trxId", StringCodec.INSTANCE)

        private fun expireIndex() =
            redissonClient.getScoredSortedSet<String>(RedisStockWriterImpl.EXPIRE_INDEX_KEY, StringCodec.INSTANCE)

        private fun change(
            productId: Long,
            quantity: Long,
        ) = StockWriter.StockChange(productId, quantity)

        private fun reserve(
            trxId: String,
            vararg changes: StockWriter.StockChange,
        ) = stockWriter.reserve(trxId, changes.toList(), EXPIRE_AT)

        @Test
        fun `하나라도 재고가 모자라면 아무것도 감소하지 않는다`() {
            seed(1, 10)
            seed(2, 1)
            seed(3, 30)

            val trxId = UUID.randomUUID().toString()
            assertThrows<StockUnavailableException> { reserve(trxId, change(1, 1), change(2, 2), change(3, 3)) }

            // Lua가 전부 검사한 뒤에 차감하므로 부분 차감이 생기면 안 된다
            assertEquals(10, stockOf(1))
            assertEquals(1, stockOf(2))
            assertEquals(30, stockOf(3))
        }

        @Test
        fun `거절된 차감은 점유 기록도 만료 등재도 남기지 않는다`() {
            // 남으면 스케줄러가 일어나지도 않은 차감을 되돌려 오버셀이 난다
            seed(1, 1)

            val trxId = UUID.randomUUID().toString()
            assertThrows<StockUnavailableException> { reserve(trxId, change(1, 5)) }

            assertTrue(body(trxId).isEmpty())
            assertNull(expireIndex().getScore(trxId))
        }

        @Test
        fun `없는 상품이 섞이면 아무것도 감소하지 않고 기록도 남지 않는다`() {
            seed(1, 10)

            val trxId = UUID.randomUUID().toString()
            assertThrows<StockUnavailableException> { reserve(trxId, change(1, 1), change(2, 1)) }

            assertEquals(10, stockOf(1))
            assertTrue(body(trxId).isEmpty())
        }

        @Test
        fun `점유에 성공하면 되돌릴 수량과 만료 시각이 같이 남는다`() {
            // 보상이 MySQL을 다시 읽지 않아도 되도록, 복구 근거를 Redis 안에서 자기완결적으로 둔다
            seed(1, 10)
            seed(2, 10)

            val trxId = UUID.randomUUID().toString()
            reserve(trxId, change(1, 3), change(2, 4))

            assertEquals(mapOf(stockKey(1) to "3", stockKey(2) to "4"), body(trxId).readAllMap())

            // 만료 등재가 곧 "차감이 적용됐다"의 증거다. 없으면 되돌릴 진입점이 사라진다.
            // score는 호출자가 넘긴 값 그대로여야 한다 - 구현체가 자기 시계로 다시 찍으면 안 된다
            val expected = EXPIRE_AT.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            assertEquals(expected.toDouble(), expireIndex().getScore(trxId))

            // TTL을 걸면 스케줄러보다 먼저 사라져 되돌릴 수량을 잃는다
            assertEquals(-1, body(trxId).remainTimeToLive())
        }

        @Test
        fun `같은 trxId로 다시 불러도 두 번 차감되지 않는다`() {
            // 오버셀을 막는 책임이 Redisson 재시도 설정에서 스크립트 안으로 옮겨 온 자리다
            seed(1, 10)

            val trxId = UUID.randomUUID().toString()
            reserve(trxId, change(1, 3))
            reserve(trxId, change(1, 3))

            assertEquals(7, stockOf(1))
        }

        @Nested
        @Tag("fault")
        inner class NetworkFault {
            @Test
            fun `Redis 응답이 끊기면 적용 여부를 판정하지 않고 재시도 가능 예외로 바꾼다`() {
                // 여기서 "실패했겠지"라고 보상하면, 실제로 적용된 경우 재고가 늘어나 오버셀이 난다
                seed(1, 10)

                val toxic = redisProxy.toxics().timeout("timeout", ToxicDirection.DOWNSTREAM, 0)
                try {
                    assertThrows<StockUnstableException> { reserve(UUID.randomUUID().toString(), change(1, 1)) }
                } finally {
                    toxic.remove()
                    redissonClient.awaitRecovered()
                }
            }
        }

        companion object {
            private val EXPIRE_AT: LocalDateTime = LocalDateTime.now().plusMinutes(10)
        }
    }
