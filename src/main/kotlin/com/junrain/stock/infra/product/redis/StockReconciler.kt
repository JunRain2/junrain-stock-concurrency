package com.junrain.stock.infra.product.redis

import com.junrain.stock.application.product.port.StockWriter
import com.junrain.stock.infra.product.StockStrategy
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

private val logger = KotlinLogging.logger { }

/**
 * 언더셀로 새어나간 재고를 총량으로 되돌린다.
 *
 * 응답 타임아웃, 트랜잭션 롤백, 프로세스 크래시 — 원인이 무엇이든 결과는 하나다.
 * "차감은 됐는데 그에 대응하는 예약이 없다." 그래서 어느 연산이 샜는지 판정하지 않고
 * 상품별 총량만 다시 계산한다.
 *
 * ```
 * expected = products.stock − SUM(reservations.items[].quantity)
 * delta    = expected − actual(Redis)
 * delta > 0 이면 INCRBY. delta ≤ 0 이면 아무것도 하지 않는다
 * ```
 *
 * ### 이 계산이 서 있는 전제
 *
 * 1. **`products.stock`은 움직이지 않는다.** Redis 전략에서는 아무도 쓰지 않으므로 초기 재고 그대로다
 *    (`JdbcProductRepository.applyStockDeltas`를 부르는 건 `single-update` 전략의 구현체뿐이다).
 * 2. **팔 수 있는 양을 올리는 주체는 이 클래스 하나다.** 둘이 되면 같은 몫을 둘이 올려 오버셀이 난다.
 *
 * 둘 다 코드가 강제하지 않는다. 지금 안 깨지는 이유는 구매 확정·재입고·취소가 아직 없기 때문이다.
 * 붙일 때 이 배치가 같이 움직여야 하는 지점은 설계 문서
 * `docs/usecase/재고점유/전략/redis-lua-설계.md` 9절에 정리해 뒀다.
 *
 * **증가 주체는 이 클래스 하나뿐이어야 한다.** 인라인 보상을 같이 두면 같은 누수를 둘이 되돌려
 * 이중 증가 = 오버셀이 된다. 그래서 [com.junrain.stock.infra.product.RedisStockWriterImpl]은
 * 롤백 보상을 하지 않는다.
 *
 * MySQL 전략은 차감과 예약이 한 트랜잭션이라 누수 자체가 없다. Redis 전략에서만 뜬다.
 *
 * ponytail: 인스턴스마다 돈다. 둘이 동시에 돌면 각자 delta를 계산해 둘 다 증가시키므로 이중 증가다.
 * 다중 인스턴스로 가면 리더 선출이나 분산락이 필요하다.
 */
@Component
@ConditionalOnProperty(name = [StockStrategy.PROPERTY], havingValue = "redis")
class StockReconciler(
    private val redissonClient: RedissonClient,
    private val jdbcTemplate: JdbcTemplate,
    private val stockWriter: StockWriter,
    private val stockSuspects: StockSuspects,
    meterRegistry: MeterRegistry,
    @param:Value("\${stock.reconcile.grace-seconds}") private val graceSeconds: Long,
    @param:Value("\${stock.reserve.timeout-seconds}") private val reserveTimeoutSeconds: Long,
    @param:Value("\${stock.reconcile.chunk-size:1000}") private val chunkSize: Int,
) {
    /** 되돌린 재고의 누적 수량 */
    private val repaired: Counter =
        Counter
            .builder("stock.reconcile.repaired")
            .description("정합 배치가 되돌린 재고 수량의 누계")
            .register(meterRegistry)

    /** 마지막 실행에서 관측한 누수 총합. 수렴 여부(불리언)가 아니라 하한 간격의 크기를 본다 */
    private val gap = AtomicLong(0)

    init {
        // 유예가 점유 트랜잭션 상한보다 짧으면 진행 중인 점유를 누수로 오인해 오버셀이 난다.
        // 조용히 넘어가면 알 방법이 없으므로 기동을 막는다
        require(graceSeconds > reserveTimeoutSeconds) {
            "stock.reconcile.grace-seconds($graceSeconds)는 stock.reserve.timeout-seconds($reserveTimeoutSeconds)보다 커야 합니다."
        }

        meterRegistry.gauge("stock.reconcile.gap", gap) { it.get().toDouble() }
    }

    /**
     * 빠른 경로. 샐 수 있었던 상품만 확인한다.
     *
     * 표시가 없으면 **쿼리 한 줄도 나가지 않는다.** 타임아웃은 상시가 아니라 가끔이므로 평시 비용이 0이다.
     */
    @Scheduled(fixedDelayString = "\${stock.reconcile.suspect-delay-seconds}", timeUnit = TimeUnit.SECONDS)
    fun reconcileSuspects() {
        if (stockSuspects.isEmpty()) return

        val suspects = stockSuspects.drain()
        logger.info { "표시된 상품 회수 시작 : ${suspects.size}건" }

        // 실패해도 다시 표시하지 않는다. 다음 전수 스캔이 안전망이다
        reconcile(suspects)
    }

    /**
     * 안전망. 표시를 남기지 못한 누수만 남으므로 드물게 돈다.
     *
     * 프로세스가 차감과 커밋 사이에 죽으면 표시도 함께 사라진다. 그건 [reconcileSuspects]가 영영 못 잡는다.
     */
    @Scheduled(cron = "\${stock.reconcile.cron}")
    fun reconcileAll() = reconcile(productIds = null)

    private fun reconcile(productIds: Set<Long>?) {
        // 1단계: Redis를 **먼저** 읽는다. 예약 합계를 먼저 읽으면 그 뒤 들어온 차감이 delta에 통째로
        // 누수로 잡혀 되돌려진다 = 오버셀. 늦게 읽은 쪽이 delta를 줄이는 방향으로 작용해야 한다
        val snapshot = snapshotRedisStock(productIds)
        if (snapshot.isEmpty()) return

        // 유예. 스냅샷 시점에 진행 중이던 트랜잭션이 커밋될 시간을 준다.
        // 차감은 커밋보다 먼저 일어나므로, 이 시간을 안 주면 "차감은 스냅샷에 잡혔는데 예약은 아직
        // 안 보이는" 요청이 누수로 오인돼 되돌려진다. 그 뒤 커밋되면 그게 오버셀이다.
        // ReserveProducts의 트랜잭션 타임아웃이 그 창의 상한이고, init의 require가 둘의 관계를 강제한다
        Thread.sleep(graceSeconds * 1_000)

        // 2단계: 예약 합계는 유예 뒤에 읽는다. 늦게 읽을수록 delta를 과소평가해 안전한 쪽으로 틀린다
        val reservedByProductId = reservedQuantities(snapshot.keys)

        var observed = 0L
        var applied = 0L

        snapshot.forEach { (productId, state) ->
            val expected = state.initial - (reservedByProductId[productId] ?: 0L)
            val delta = expected - state.actual

            if (delta <= 0) return@forEach

            observed += delta

            runCatching { stockWriter.increase(listOf(StockWriter.StockChange(productId, delta))) }
                .onSuccess {
                    applied += delta
                    logger.info { "재고 회수 id=$productId, 수량=$delta (기대=$expected, 실제=${state.actual})" }
                }.onFailure { logger.warn(it) { "재고 회수 실패 id=$productId, 수량=$delta" } }
        }

        gap.set(observed)
        repaired.increment(applied.toDouble())

        if (observed > 0) logger.info { "정합 완료 : 대상=${snapshot.size}건, 관측 누수=$observed, 회수=$applied" }
    }

    /**
     * 초기 재고와 현재 Redis 재고를 함께 담는다. [productIds]가 있으면 그 상품만, 없으면 전량이다.
     *
     * ponytail: 전수 스캔은 대상을 전부 메모리에 올린다. 상품 수가 문제가 되면 id 구간별로
     * 1·2단계를 쌍으로 돌린다(유예도 구간마다 필요하다).
     */
    private fun snapshotRedisStock(productIds: Set<Long>?): Map<Long, StockState> =
        buildMap {
            var lastId = 0L
            val targets = productIds?.sorted()

            while (true) {
                val page =
                    if (targets == null) {
                        jdbcTemplate.query(
                            "SELECT id, stock FROM products WHERE id > ? ORDER BY id LIMIT ?",
                            { rs, _ -> rs.getLong("id") to rs.getLong("stock") },
                            lastId,
                            chunkSize,
                        )
                    } else {
                        val chunk = targets.filter { it > lastId }.take(chunkSize)
                        if (chunk.isEmpty()) {
                            emptyList()
                        } else {
                            jdbcTemplate.query(
                                "SELECT id, stock FROM products WHERE id IN (${chunk.joinToString(", ") { "?" }})",
                                { rs, _ -> rs.getLong("id") to rs.getLong("stock") },
                                *chunk.toTypedArray(),
                            )
                        }
                    }
                if (page.isEmpty()) break

                val actualByKey =
                    redissonClient
                        .getBuckets(StringCodec.INSTANCE)
                        .get<String>(*page.map { stockKey(it.first) }.toTypedArray())

                page.forEach { (productId, initial) ->
                    // 키가 없으면 심기가 실패한 상품이다. 0에서 채워 넣으면 존재한 적 없는 재고를 만든다
                    val actual = actualByKey[stockKey(productId)]
                    if (actual == null) {
                        logger.warn { "재고 키 없음, 회수 대상에서 제외 id=$productId" }
                        return@forEach
                    }
                    put(productId, StockState(initial = initial, actual = actual.toLong()))
                }

                lastId = page.maxOf { it.first }
            }
        }

    /**
     * 상품별 예약 수량 합계.
     *
     * ponytail: 상품을 좁혀도 reservations는 전량 스캔이다 — `items`가 JSON이라 `product_id`로
     * 인덱스를 못 탄다. 좁히기는 반환 행 수와 GROUP BY 비용만 줄인다. 이 쿼리가 뜨거워지면
     * `items` 배열에 multi-valued index를 걸거나, reservation id 워터마크로 상품별 누계를
     * 증분 유지하는 쪽으로 바꾼다. 지금은 표시된 상품이 있을 때만 나가므로 뜸하게 돈다.
     */
    private fun reservedQuantities(productIds: Set<Long>): Map<Long, Long> {
        if (productIds.isEmpty()) return emptyMap()

        val placeholders = productIds.joinToString(", ") { "?" }

        return jdbcTemplate
            .query(
                """
                SELECT jt.product_id AS product_id, SUM(jt.quantity) AS reserved
                FROM reservations r,
                     JSON_TABLE(r.items, '$[*]' COLUMNS (
                       product_id BIGINT PATH '$.productId',
                       quantity   BIGINT PATH '$.quantity')) jt
                WHERE jt.product_id IN ($placeholders)
                GROUP BY jt.product_id
                """.trimIndent(),
                { rs, _ -> rs.getLong("product_id") to rs.getLong("reserved") },
                *productIds.toTypedArray(),
            ).toMap()
    }

    private fun stockKey(productId: Long) = "product_stock:$productId"

    private data class StockState(
        val initial: Long,
        val actual: Long,
    )
}
