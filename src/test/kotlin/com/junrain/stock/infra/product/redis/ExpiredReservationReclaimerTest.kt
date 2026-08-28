package com.junrain.stock.infra.product.redis

import com.junrain.stock.application.product.port.StockWriter
import com.junrain.stock.config.StockProbe
import com.junrain.stock.infra.product.RedisStockWriterImpl
import org.junit.jupiter.api.BeforeEach
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
class ExpiredReservationReclaimerTest
    @Autowired
    constructor(
        private val reclaimer: ExpiredReservationReclaimer,
        private val stockWriter: RedisStockWriterImpl,
        private val redissonClient: RedissonClient,
        private val probe: StockProbe,
    ) {
        @BeforeEach
        fun beforeEach() {
            redissonClient.keys.flushall()
        }

        private fun seed(
            productId: Long,
            quantity: Long,
        ) = redissonClient.getAtomicLong("available_stock:$productId").set(quantity)

        private fun reserve(
            trxId: String,
            expireAt: LocalDateTime,
            vararg changes: StockWriter.StockChange,
        ) = stockWriter.reserve(trxId, changes.toList(), expireAt)

        @Test
        fun `만기된 점유는 되돌리고 기록을 지운다`() {
            seed(1, 10)
            seed(2, 10)

            val trxId = UUID.randomUUID().toString()
            reserve(trxId, PAST, StockWriter.StockChange(1, 3), StockWriter.StockChange(2, 4))

            reclaimer.reclaimExpired()

            assertEquals(10, probe.stockOf(1))
            assertEquals(10, probe.stockOf(2))
            // 기록과 등재가 남으면 다음 회수가 같은 몫을 또 되돌려 오버셀이 난다
            assertTrue(probe.reservationBody(trxId).isEmpty())
            assertTrue(probe.expireIndexMembers().isEmpty())
        }

        @Test
        fun `만기 전 점유는 건드리지 않는다`() {
            seed(1, 10)

            val trxId = UUID.randomUUID().toString()
            reserve(trxId, LocalDateTime.now().plusMinutes(10), StockWriter.StockChange(1, 3))

            reclaimer.reclaimExpired()

            assertEquals(7, probe.stockOf(1))
            assertEquals(setOf(trxId), probe.expireIndexMembers())
        }

        @Test
        fun `두 번 돌아도 두 번 되돌리지 않는다`() {
            // ZREM 성공을 가드로 쓰는 이유. 중복 실행이 곧 이중 증가면 오버셀이다
            seed(1, 10)
            reserve(UUID.randomUUID().toString(), PAST, StockWriter.StockChange(1, 3))

            reclaimer.reclaimExpired()
            reclaimer.reclaimExpired()

            assertEquals(10, probe.stockOf(1))
        }

        @Test
        fun `기록이 사라진 등재는 재고를 늘리지 않고 정리만 한다`() {
            // 있지도 않은 차감을 되돌리면 없던 재고가 생긴다
            seed(1, 10)

            val trxId = UUID.randomUUID().toString()
            reserve(trxId, PAST, StockWriter.StockChange(1, 3))
            redissonClient.getBucket<String>(RedisStockWriterImpl.reservationKey(trxId)).delete()

            reclaimer.reclaimExpired()

            assertEquals(7, probe.stockOf(1))
            assertTrue(probe.expireIndexMembers().isEmpty())
        }

        companion object {
            private val PAST: LocalDateTime = LocalDateTime.now().minusMinutes(1)
        }
    }
