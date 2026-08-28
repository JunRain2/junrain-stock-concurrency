package com.junrain.stock.infra.product.redis

import com.junrain.stock.infra.product.StockStrategy
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * 재고가 샜을 수 있는 상품 id를 모은다.
 *
 * 누수는 Reservation의 부재라 예약 쪽에서 후보를 뽑을 수 없다. 대신 **샐 수 있는 순간**은 프로세스가 알고 있다 —
 * 응답을 못 받은 차감, 차감 뒤 롤백된 트랜잭션. 그때 상품 id만 집어넣으면
 * [StockReconciler]가 전수 스캔 없이 그 상품만 확인한다.
 *
 * 틀려도 되는 목록이다. 그게 WAL과 결정적으로 다른 점이다.
 *
 * - 유실돼도 된다 — 전수 스캔이 안전망으로 남는다. 그래서 디스크도 fsync도 필요 없다
 * - 헛되이 들어와도 된다 — 실제로 안 샜으면 delta가 0이라 아무 일도 안 일어난다
 *
 * 그래서 사용자 응답 경로에 붙는 비용이 실패 경로에서의 집합 add 하나뿐이다.
 */
@Component
@ConditionalOnProperty(name = [StockStrategy.PROPERTY], havingValue = "redis")
class StockSuspects {
    private val productIds = ConcurrentHashMap.newKeySet<Long>()

    fun add(ids: Collection<Long>) {
        productIds.addAll(ids)
    }

    /**
     * 모인 id를 꺼내며 비운다.
     *
     * 꺼낸 원소만 정확히 지운다. 꺼내는 도중에 다시 들어온 id는 집합에 남아 다음 회차가 가져간다 —
     * 복사 후 일괄 삭제로 하면 그 틈에 들어온 id를 처리하지 않고 지워버린다.
     */
    fun drain(): Set<Long> =
        buildSet {
            val iterator = productIds.iterator()
            while (iterator.hasNext()) {
                add(iterator.next())
                iterator.remove()
            }
        }

    fun isEmpty() = productIds.isEmpty()
}
