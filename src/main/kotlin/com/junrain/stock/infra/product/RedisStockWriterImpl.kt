package com.junrain.stock.infra.product

import com.junrain.stock.application.product.port.StockWriter
import com.junrain.stock.domain.product.exception.StockUnavailableException
import com.junrain.stock.domain.product.exception.StockUnstableException
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.redisson.api.RScript
import org.redisson.api.RedissonClient
import org.redisson.client.RedisConnectionException
import org.redisson.client.RedisTimeoutException
import org.redisson.client.codec.StringCodec
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.ZoneId

private val logger = KotlinLogging.logger { }

/**
 * Lua 스크립트로 n개 상품의 재고를 원자적으로 점유한다.
 *
 * 스크립트 한 번의 실행이 곧 원자 단위라 "전부 충분할 때만 전부 차감"이 보장된다.
 * 오버셀은 이 원자성이 매 순간 막고, 언더셀은 회복될 때까지 허용한다.
 *
 * **응답을 못 받아도 판정하지 않고 재시도하지도 않는다.** 재시도 가능 예외로 바꿔 던지고 끝낸다.
 * 적용됐다면 만료 인덱스에 등재돼 있고, 만료 시각이 되면 스케줄러가 조건 없이 되돌린다.
 * 정상 종료한 예약도 만료되면 같은 처리를 받으므로, 둘을 구분할 이유가 애초에 없다.
 */
@Component
class RedisStockWriterImpl(
    private val redissonClient: RedissonClient,
    meterRegistry: MeterRegistry,
) : StockWriter {
    /**
     * 반환값은 항상 `{status, index}` 두 칸이다. index는 실패한 상품의 순번(1-based)이고 실패가 아니면 0이다.
     *
     * - 성공한 경우 `{0, 0R}`. 이때만 재고가 움직이고 점유 기록이 남는다
     * - 같은 trxId가 이미 처리된 경우 `{1, 0}`. 아무것도 건드리지 않는다
     * - 하나라도 재고가 부족한 경우 `{2, index}`. 검사를 모두 통과해야 차감하므로 부분 차감은 없다
     * - 재고 키가 없는 상품이 섞인 경우 `{3, index}`. 마찬가지로 아무것도 건드리지 않는다
     */
    private val reserveScript = ClassPathResource("redis/reserve_stock.lua").inputStream.bufferedReader().use { it.readText() }

    /** 적용 여부를 모른 채 끝난 차감 횟수. 만료까지 이어지는 언더셀 누수의 상한을 이 값으로 가늠한다 */
    private val unknownOutcomes: Counter =
        Counter
            .builder("stock.decrease.unknown")
            .description("적용 여부를 확인하지 못하고 실패한 재고 차감 횟수")
            .register(meterRegistry)

    override fun reserve(
        trxId: String,
        changes: List<StockWriter.StockChange>,
        expireAt: LocalDateTime,
    ) {
        require(changes.isNotEmpty()) { "재고를 변경할 상품이 없습니다." }
        require(changes.distinctBy { it.productId }.size == changes.size) { "같은 상품이 여러 번 들어왔습니다." }

        val keys =
            buildList<Any> {
                add(reservationKey(trxId))
                add(EXPIRE_INDEX_KEY)
                addAll(changes.map { stockKey(it.productId) })
            }
        val argv =
            buildList<Any> {
                add(trxId)
                // ZSET score는 숫자여야 정렬·범위 조회가 된다. 회수 스케줄러도 같은 변환으로 now를 만든다
                add(expireAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli().toString())
                addAll(changes.map { it.quantity.toString() })
            }.toTypedArray()

        val result =
            try {
                // StringCodec 필수. 기본 코덱이면 키/인자가 직렬화돼 스크립트가 못 읽는다
                redissonClient
                    .getScript(StringCodec.INSTANCE)
                    .eval<List<Long>>(RScript.Mode.READ_WRITE, reserveScript, RScript.ReturnType.MULTI, keys, *argv)
            } catch (e: RedisTimeoutException) {
                throw unstable("Redis 응답 타임아웃", changes, e)
            } catch (e: RedisConnectionException) {
                throw unstable("Redis 연결 실패", changes, e)
            }

        val status = result[0]
        val index = result[1].toInt()

        when (status) {
            STATUS_OK -> {
                Unit
            }

            // 같은 예약이 두 번 도착했다. 첫 번째가 이미 재고를 잡아 뒀으므로 성공과 결과가 같다
            STATUS_ALREADY_APPLIED -> {
                logger.info { "이미 처리된 예약(trxId=$trxId)" }
            }

            STATUS_INSUFFICIENT -> {
                val change = changes[index - 1]
                throw unavailable("재고부족(id=${change.productId}, 요청=${change.quantity})")
            }

            STATUS_NOT_FOUND -> {
                throw unavailable("상품없음(id=${changes[index - 1].productId})")
            }

            else -> {
                error("알 수 없는 스크립트 반환값: $status")
            }
        }
    }

    private fun unavailable(reason: String): StockUnavailableException {
        logger.warn { "재고 점유 실패(재시도 불가) : $reason" }

        return StockUnavailableException(reason)
    }

    /** 적용됐는지 알 수 없다. 판정하지 않고 재시도 가능으로 넘긴다 - 적용됐다면 만료까지 언더셀로 남는다 */
    private fun unstable(
        reason: String,
        changes: List<StockWriter.StockChange>,
        cause: Exception,
    ): StockUnstableException {
        unknownOutcomes.increment()
        logger.error(cause) { "$reason, 대상=${changes.map { it.productId }}" }

        return StockUnstableException(reason, cause)
    }

    // 키 생성을 밖으로 빼면 어디서든 끌어다 쓸 수 있게 되어 Redis 세부사항이 위로 새어 나간다
    private fun stockKey(productId: Long) = "available_stock:$productId"

    companion object {
        /** 만료 인덱스. member=trxId, score=만료 시각(ms). 회수 스케줄러가 이 하나만 훑는다 */
        const val EXPIRE_INDEX_KEY = "reservation_expire"

        /** 점유 기록 키는 Reservation.trxId를 그대로 쓴다. 되돌릴 수량이 이 해시에 들어 있다 */
        fun reservationKey(trxId: String) = "reservation:$trxId"

        private const val STATUS_OK = 0L
        private const val STATUS_ALREADY_APPLIED = 1L
        private const val STATUS_INSUFFICIENT = 2L
        private const val STATUS_NOT_FOUND = 3L
    }
}
