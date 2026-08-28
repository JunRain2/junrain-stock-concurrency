package com.junrain.stock.infra.product

import com.junrain.stock.application.product.port.StockWriter
import com.junrain.stock.domain.product.exception.ProductNotFoundException
import com.junrain.stock.domain.product.exception.StockUnavailableException
import com.junrain.stock.domain.product.exception.StockUnstableException
import com.junrain.stock.infra.product.redis.StockSuspects
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.redisson.api.RScript
import org.redisson.api.RedissonClient
import org.redisson.client.RedisConnectionException
import org.redisson.client.RedisTimeoutException
import org.redisson.client.codec.StringCodec
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

private val logger = KotlinLogging.logger { }

/**
 * Lua 스크립트로 n개 상품의 재고를 원자적으로 차감/증가시킨다.
 *
 * 스크립트 한 번의 실행이 곧 원자 단위라 "전부 충분할 때만 전부 차감"이 보장된다.
 * 오버셀은 이 원자성이 매 순간 막고, 언더셀은 허용한다.
 *
 * **응답을 못 받으면 적용 여부를 판정하지 않는다.** 적용됐다면 재고가 사라져 언더셀이 되고,
 * 미적용이면 아무 일도 없다. 어느 쪽도 오버셀이 아니므로 판정할 이유가 없다.
 * 그래서 op_id도 WAL도 없다 — 새어나간 재고는
 * [com.junrain.stock.infra.product.redis.StockReconciler]가 총량으로 되돌린다.
 *
 * 대신 **샐 수 있었던 순간에 상품 id만 [StockSuspects]에 표시한다.** 응답을 못 받은 차감과
 * 차감 뒤 롤백된 트랜잭션 둘 다다. 표시가 있어야 회수가 전수 스캔 없이 그 상품만 본다.
 */
@Component
@ConditionalOnProperty(name = [StockStrategy.PROPERTY], havingValue = "redis")
class RedisStockWriterImpl(
    private val redissonClient: RedissonClient,
    private val stockSuspects: StockSuspects,
    meterRegistry: MeterRegistry,
) : StockWriter {
    /**
     * 반환값은 항상 `{status, index}` 두 칸이다. index는 실패한 상품의 순번(1-based)이고 실패가 아니면 0이다.
     *
     * - 성공한 경우 `{0, 0}`을 반환. 이때만 재고가 움직인다
     * - 하나라도 재고가 부족한 경우 `{2, index}`를 반환(감소 전용). 검사를 모두 통과해야 차감하므로 부분 차감은 없다
     * - 재고 키가 없는 상품이 섞인 경우 `{3, index}`를 반환. 마찬가지로 아무것도 건드리지 않는다
     */
    private val decreaseScript = loadScript("redis/decrease_stock.lua")

    private val increaseScript = loadScript("redis/increase_stock.lua")

    /** 적용 여부를 모른 채 끝난 차감 횟수. 언더셀 누수의 상한을 이 값으로 가늠한다 */
    private val unknownOutcomes: Counter =
        Counter
            .builder("stock.decrease.unknown")
            .description("적용 여부를 확인하지 못하고 실패한 재고 차감 횟수")
            .register(meterRegistry)

    override fun decrease(changes: List<StockWriter.StockChange>) {
        val validated = validated(changes)

        val (status, index) = execute(decreaseScript, validated)

        when (status) {
            // 차감은 트랜잭션 밖에서 이미 일어났다. 롤백돼도 재고는 빠진 채 남으므로 회수 대상이다.
            // 여기서 되돌리지는 않는다 - 증가 주체가 둘이 되면 같은 몫을 둘이 올려 오버셀이다
            STATUS_OK -> suspectOnRollback(validated)

            STATUS_INSUFFICIENT -> {
                val change = validated[index - 1]
                val reason = "재고부족(id=${change.productId}, 요청=${change.quantity})"
                logger.warn { "재고 감소 실패(재시도 불가) : $reason" }
                throw StockUnavailableException(reason)
            }

            STATUS_NOT_FOUND -> {
                val reason = "상품없음(id=${validated[index - 1].productId})"
                logger.warn { "재고 감소 실패(재시도 불가) : $reason" }
                throw StockUnavailableException(reason)
            }

            else -> {
                error("알 수 없는 스크립트 반환값: $status")
            }
        }
    }

    /** 이 차감을 감싼 트랜잭션이 롤백되면 회수 대상으로 표시한다. Redis는 건드리지 않는다. */
    private fun suspectOnRollback(changes: List<StockWriter.StockChange>) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return

        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCompletion(status: Int) {
                    if (status != TransactionSynchronization.STATUS_ROLLED_BACK) return

                    stockSuspects.add(changes.map { it.productId })
                }
            },
        )
    }

    /**
     * 증가는 되돌리지 않는다. 보상 감소는 그 사이 들어온 정상 점유를 밟아 오버셀을 만든다.
     *
     * 증가 주체는 [com.junrain.stock.infra.product.redis.StockReconciler] 하나뿐이다.
     * 둘이 되면 같은 누수를 둘이 되돌려 이중 증가 = 오버셀이 된다.
     */
    override fun increase(changes: List<StockWriter.StockChange>) {
        val validated = validated(changes)

        val (status, index) = execute(increaseScript, validated)

        when (status) {
            STATUS_OK -> Unit

            STATUS_NOT_FOUND -> {
                logger.warn { "재고 증가 실패 : 상품없음(id=${validated[index - 1].productId})" }
                throw ProductNotFoundException()
            }

            else -> {
                error("알 수 없는 스크립트 반환값: $status")
            }
        }
    }

    private fun validated(changes: List<StockWriter.StockChange>): List<StockWriter.StockChange> {
        require(changes.isNotEmpty()) { "재고를 변경할 상품이 없습니다." }
        require(changes.distinctBy { it.productId }.size == changes.size) { "같은 상품이 여러 번 들어왔습니다." }

        return changes
    }

    /** @return (status, index) */
    private fun execute(
        script: String,
        changes: List<StockWriter.StockChange>,
    ): Pair<Long, Int> {
        val keys = changes.map<StockWriter.StockChange, Any> { stockKey(it.productId) }
        val argv = changes.map<StockWriter.StockChange, Any> { it.quantity.toString() }.toTypedArray()

        val result =
            try {
                // StringCodec 필수. 기본 코덱이면 키/인자가 직렬화돼 스크립트가 못 읽는다
                redissonClient
                    .getScript(StringCodec.INSTANCE)
                    .eval<List<Long>>(RScript.Mode.READ_WRITE, script, RScript.ReturnType.MULTI, keys, *argv)
            } catch (e: RedisTimeoutException) {
                // 적용됐는지 알 수 없다. 판정하지 않고 재시도 가능으로 넘긴다 — 적용됐다면 언더셀로 남는다
                throw unstable("Redis 응답 타임아웃", changes, e)
            } catch (e: RedisConnectionException) {
                throw unstable("Redis 연결 실패", changes, e)
            }

        return result[0] to result[1].toInt()
    }

    private fun unstable(
        reason: String,
        changes: List<StockWriter.StockChange>,
        cause: Exception,
    ): StockUnstableException {
        val productIds = changes.map { it.productId }

        unknownOutcomes.increment()
        stockSuspects.add(productIds)
        logger.error(cause) { "$reason, 대상=$productIds" }

        return StockUnstableException(reason, cause)
    }

    private fun loadScript(path: String) =
        ClassPathResource(path).inputStream.bufferedReader().use { it.readText() }

    // 키 생성을 밖으로 빼면 어디서든 끌어다 쓸 수 있게 되어 Redis 세부사항이 위로 새어 나간다
    private fun stockKey(productId: Long) = "product_stock:$productId"

    companion object {
        private const val STATUS_OK = 0L
        private const val STATUS_INSUFFICIENT = 2L
        private const val STATUS_NOT_FOUND = 3L
    }
}
