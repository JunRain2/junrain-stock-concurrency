package com.junrain.stock.infra.product

import com.junrain.stock.application.product.port.StockWriter
import com.junrain.stock.domain.product.exception.StockUnavailableException
import com.junrain.stock.domain.product.exception.StockUnstableException
import com.junrain.stock.infra.product.redis.StockOpLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import org.redisson.api.RScript
import org.redisson.api.RedissonClient
import org.redisson.client.RedisConnectionException
import org.redisson.client.RedisTimeoutException
import org.redisson.client.codec.StringCodec
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import java.util.*
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger { }

/**
 * Lua 스크립트로 n개 상품의 재고를 원자적으로 차감한다.
 *
 * 스크립트 한 번의 실행이 곧 원자 단위라 "전부 충분할 때만 전부 차감"이 보장된다.
 * op_id를 스크립트가 기록하므로 같은 op_id로 다시 실행해도 두 번 차감되지 않는다.
 */
@Component
@ConditionalOnProperty(name = [StockStrategy.PROPERTY], havingValue = "redis")
class RedisStockWriterImpl(
    private val redissonClient: RedissonClient,
    private val stockOpLogger: StockOpLogger,
) : StockWriter {
    /**
     * 반환값은 항상 `{status, index}` 두 칸이다. index는 실패한 상품의 순번(1-based)이고 실패가 아니면 0이다.
     *
     * - 성공한 경우 `{0, 0}`을 반환. 이때만 재고가 줄고 op_id 키가 남는다
     * - op_id가 이미 존재하는 경우 `{1, 0}`을 반환. 재고를 건드리지 않는다
     * - 하나라도 재고가 부족한 경우 `{2, index}`를 반환. 검사를 모두 통과해야 차감하므로 부분 차감은 없다
     * - 재고 키가 없는 상품이 섞인 경우 `{3, index}`를 반환. 마찬가지로 아무것도 건드리지 않는다
     */
    private val decreaseScript =
        ClassPathResource("redis/decrease_stock.lua").inputStream.bufferedReader().use { it.readText() }

    override fun decrease(changes: List<StockWriter.StockChange>) {
        require(changes.isNotEmpty()) { "재고를 변경할 상품이 없습니다." }
        require(changes.distinctBy { it.productId }.size == changes.size) { "같은 상품이 여러 번 들어왔습니다." }

        // 로그 순서를 요청마다 뒤바뀌지 않게 고정한다
        val sorted = changes.sortedBy { it.productId }
        val opId = UUID.randomUUID().toString()

        // 전송 전 pending. 타임아웃으로 응답을 못 받아도 이 기록은 남는다
        stockOpLogger.pending(opId, sorted)

        val (status, index) = execute(opId, sorted)

        // 응답을 받았다는 건 적용 여부가 확정됐다는 뜻이다. 재고가 모자랐다면 스크립트가 아무것도 건드리지 않았다.
        // pending으로 남는 건 응답을 못 받아 적용 여부를 모르는 경우뿐이다
        stockOpLogger.done(opId)

        when (status) {
            // ALREADY_APPLIED는 같은 op_id가 이미 적용된 것. 재시도가 원본을 따라잡았을 뿐이라 성공으로 본다
            STATUS_OK, STATUS_ALREADY_APPLIED -> {
                Unit
            }

            STATUS_INSUFFICIENT -> {
                val change = sorted[index - 1]
                val reason = "재고부족(id=${change.productId}, 요청=${change.quantity})"
                logger.warn { "재고 감소 실패(재시도 불가) : $reason" }
                throw StockUnavailableException(reason)
            }

            STATUS_NOT_FOUND -> {
                val reason = "상품없음(id=${sorted[index - 1].productId})"
                logger.warn { "재고 감소 실패(재시도 불가) : $reason" }
                throw StockUnavailableException(reason)
            }

            else -> {
                error("알 수 없는 스크립트 반환값: $status")
            }
        }
    }

    override fun increase(changes: List<StockWriter.StockChange>) {
        TODO("보상 경로는 다음 스텝")
    }

    /** @return (status, index) */
    private fun execute(
        opId: String,
        changes: List<StockWriter.StockChange>,
    ): Pair<Long, Int> {
        val keys = listOf<Any>(opKey(opId)) + changes.map { stockKey(it.productId) }
        val argv =
            (listOf<Any>(OP_KEY_TTL_SECONDS.toString()) + changes.map { it.quantity.toString() }).toTypedArray()

        val result =
            try {
                // StringCodec 필수. 기본 코덱이면 키/인자가 직렬화돼 스크립트가 못 읽는다
                redissonClient
                    .getScript(StringCodec.INSTANCE)
                    .eval<List<Long>>(RScript.Mode.READ_WRITE, decreaseScript, RScript.ReturnType.MULTI, keys, *argv)
            } catch (e: RedisTimeoutException) {
                // 적용됐는지 알 수 없다. pending만 남기고 재시도 가능으로 넘긴다
                logger.error(e) { "Redis 응답 타임아웃, op_id=$opId" }
                throw StockUnstableException("Redis 응답 타임아웃", e)
            } catch (e: RedisConnectionException) {
                logger.error(e) { "Redis 연결 실패, op_id=$opId" }
                throw StockUnstableException("Redis 연결 실패", e)
            }

        return result[0] to result[1].toInt()
    }

    // 키 생성을 밖으로 빼면 어디서든 끌어다 쓸 수 있게 되어 Redis 세부사항이 위로 새어 나간다
    private fun stockKey(productId: Long) = "product_stock:$productId"

    private fun opKey(opId: String) = "stock:op:$opId"

    companion object {
        private val OP_KEY_TTL_SECONDS = TimeUnit.DAYS.toSeconds(1)

        private const val STATUS_OK = 0L
        private const val STATUS_ALREADY_APPLIED = 1L
        private const val STATUS_INSUFFICIENT = 2L
        private const val STATUS_NOT_FOUND = 3L
    }
}
