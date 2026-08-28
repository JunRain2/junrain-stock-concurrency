package com.junrain.stock.infra.product.redis

import com.junrain.stock.infra.product.RedisStockWriterImpl
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.redisson.api.RScript
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger { }

/**
 * 만기된 점유를 조건 없이 되돌린다. 만료 인덱스에 등재됐다는 것이 곧 차감이 적용됐다는 증거라,
 * 정상 종료한 예약인지 응답을 못 받고 죽은 요청인지 판정하지 않는다.
 *
 * `reservation:{trxId}`에는 TTL이 없다. 이 회수가 돌지 않으면 잡힌 재고도 기록도 무한히 쌓인다.
 *
 * 이중 회수는 스크립트의 `ZREM` 성공 여부가 막는다. 여럿이 같은 대상을 동시에 뽑아도 실제로
 * 제거한 하나만 되돌린다 - 중복 조회라는 낭비만 남고 재고는 틀어지지 않는다.
 */
@Component
class ExpiredReservationReclaimer(
    private val redissonClient: RedissonClient,
    meterRegistry: MeterRegistry,
    @param:Value("\${stock.reclaim.batch-size}") private val batchSize: Int,
) {
    private val releaseScript = ClassPathResource("redis/release_stock.lua").inputStream.bufferedReader().use { it.readText() }

    /** 되돌린 예약 수의 누계. 죽은 요청이 만료까지 잠가 둔 재고가 실제로 돌아오는지를 이 값으로 본다 */
    private val reclaimed: Counter =
        Counter
            .builder("stock.reclaim.released")
            .description("만료로 회수된 점유 수")
            .register(meterRegistry)

    /**
     * 한 번 부를 때 한 묶음만 처리한다. 밀린 만큼은 다음 호출이 이어받는다.
     *
     * 밀린 대상이 [batchSize]보다 많으면 회수가 호출 간격만큼 늦어진다. 회수 지연이
     * 문제가 되면 배치를 키우거나 빈 결과가 나올 때까지 도는 쪽으로 바꾼다.
     */
    fun reclaimExpired() {
        // score는 점유 시각에 앱이 계산해 넣은 epoch ms다. 같은 기준으로 now를 만든다
        val expired =
            redissonClient
                .getScoredSortedSet<String>(RedisStockWriterImpl.EXPIRE_INDEX_KEY, StringCodec.INSTANCE)
                .valueRange(Double.NEGATIVE_INFINITY, true, System.currentTimeMillis().toDouble(), true, 0, batchSize)
                .toList()

        if (expired.isEmpty()) return

        // 하나가 실패해도 나머지는 되돌린다. 실패한 건은 등재가 남아 다음 호출이 다시 집는다
        val released =
            expired.count { trxId ->
                runCatching { release(trxId) }
                    .onFailure { logger.warn(it) { "점유 회수 실패(trxId=$trxId)" } }
                    .getOrDefault(false)
            }

        reclaimed.increment(released.toDouble())
        logger.info { "만료 점유 회수 : 대상=${expired.size}건, 회수=${released}건" }
    }

    /** @return 실제로 되돌렸으면 true. false는 이미 다른 실행자나 확정이 가져갔다는 뜻이다 */
    private fun release(trxId: String): Boolean =
        redissonClient
            .getScript(StringCodec.INSTANCE)
            .eval<Long>(
                RScript.Mode.READ_WRITE,
                releaseScript,
                RScript.ReturnType.INTEGER,
                listOf(RedisStockWriterImpl.reservationKey(trxId), RedisStockWriterImpl.EXPIRE_INDEX_KEY),
                trxId,
            ) == 1L
}
