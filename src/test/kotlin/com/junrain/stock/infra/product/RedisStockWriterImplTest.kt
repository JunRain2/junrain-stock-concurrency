package com.junrain.stock.infra.product

import com.junrain.stock.application.product.port.StockWriter
import com.junrain.stock.config.RedisTestContainersConfig.Companion.redisProxy
import com.junrain.stock.domain.product.exception.StockUnavailableException
import com.junrain.stock.domain.product.exception.StockUnstableException
import eu.rekawek.toxiproxy.model.ToxicDirection
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import org.redisson.api.RScript
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
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
        private val stockWriter: StockWriter,
        private val redissonClient: RedissonClient,
    ) {
        private val script =
            ClassPathResource("redis/decrease_stock.lua").inputStream.bufferedReader().use { it.readText() }

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

        private fun opKey(opId: String) = "stock:op:$opId"

        private fun change(
            productId: Long,
            quantity: Long,
        ) = StockWriter.StockChange(productId, quantity)

        @Test
        fun `하나라도 재고가 모자라면 아무것도 감소하지 않고 done으로 닫는다`() {
            seed(1, 10)
            seed(2, 1)
            seed(3, 30)
            val offset = opLogOffset()

            assertThrows<StockUnavailableException> {
                stockWriter.decrease(listOf(change(1, 1), change(2, 2), change(3, 3)))
            }

            // Lua가 전부 검사한 뒤에 차감하므로 부분 차감이 생기면 안 된다
            assertEquals(10, stockOf(1))
            assertEquals(1, stockOf(2))
            assertEquals(30, stockOf(3))

            // 응답을 받은 실패라 적용 여부가 확정됐다. 재시도 대상이 아니므로 done으로 닫혀야 한다
            assertTrue(opLogSince(offset).contains("done"), "비즈니스 실패도 done으로 닫혀야 한다")
        }

        @Test
        fun `같은 op_id로 다시 실행해도 두 번 차감되지 않는다`() {
            // op_id는 구현체 내부에서 발급하므로 재시도 멱등은 스크립트를 직접 돌려야 확인된다
            seed(1, 10)
            val opId = "fixed-op-id"

            repeat(2) { evalScript(opId, productId = 1, quantity = 3) }

            assertEquals(7, stockOf(1))
        }

        private fun evalScript(
            opId: String,
            productId: Long,
            quantity: Long,
        ): List<Long> =
            redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                script,
                RScript.ReturnType.MULTI,
                listOf<Any>(opKey(opId), stockKey(productId)),
                "86400",
                quantity.toString(),
            )

        @Test
        fun `Redis 응답이 끊기면 재시도 가능 예외로 바꾸고 pending만 남는다`() {
            seed(1, 10)
            val offset = opLogOffset()

            val toxic = redisProxy.toxics().timeout("timeout", ToxicDirection.DOWNSTREAM, 0)
            try {
                assertThrows<StockUnstableException> { stockWriter.decrease(listOf(change(1, 1))) }
            } finally {
                toxic.remove()
                awaitConnectionRecovered()
            }

            val appended = opLogSince(offset)
            assertTrue(appended.contains("pending"), "pending 기록이 남아야 한다")
            assertTrue(!appended.contains("done"), "적용 여부를 모르므로 done은 없어야 한다")
        }

        /**
         * 토식을 걷어도 커넥션 풀은 곧바로 회복되지 않는다.
         * 프록시를 테스트 클래스끼리 공유하므로, 상처 난 커넥션을 다음 테스트에 넘기지 않고 여기서 회복시킨다.
         */
        private fun awaitConnectionRecovered() {
            repeat(RECOVERY_ATTEMPTS) {
                runCatching { redissonClient.keys.count() }.onSuccess { return }
                Thread.sleep(RECOVERY_INTERVAL_MILLIS)
            }
            error("Redis 커넥션이 회복되지 않았습니다.")
        }

        private fun opLogFile() = File("logs/stock-op.log")

        private fun opLogOffset() = opLogFile().takeIf { it.exists() }?.length() ?: 0

        private fun opLogSince(offset: Long): String =
            opLogFile().inputStream().use {
                it.skip(offset)
                it.readBytes().decodeToString()
            }

        companion object {
            private const val RECOVERY_ATTEMPTS = 20
            private const val RECOVERY_INTERVAL_MILLIS = 200L
        }
    }
