package com.junrain.stock.config

import com.junrain.stock.infra.product.RedisStockWriterImpl
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import org.springframework.stereotype.Component

/**
 * 예약 가능한 재고와 점유 기록을 읽는 테스트 전용 프로브.
 *
 * 통합 테스트가 실제 저장소를 확인하는 건 정상이다. 다만 저장소를 아는 지점을 여기 한 곳으로 모아,
 * 키 형식이 바뀌어도 테스트 본문은 그대로 두게 한다.
 */
@Component
class StockProbe(
    private val redissonClient: RedissonClient,
) {
    fun stockOf(productId: Long): Long = redissonClient.getAtomicLong("available_stock:$productId").get()

    /** 되돌릴 수량. 재고 키 -> 수량 */
    fun reservationBody(trxId: String): Map<String, String> =
        redissonClient.getMap<String, String>("reservation:$trxId", StringCodec.INSTANCE).readAllMap()

    /** 만료 회수 대상으로 등재된 trxId. 이 등재가 곧 "차감이 적용됐다"의 증거다 */
    fun expireIndexMembers(): Set<String> =
        redissonClient.getScoredSortedSet<String>(RedisStockWriterImpl.EXPIRE_INDEX_KEY, StringCodec.INSTANCE).readAll().toSet()

    /** 만료 인덱스는 테스트 사이에 공유되는 단일 키라 직접 비워 준다 */
    fun clearExpireIndex() {
        redissonClient.getScoredSortedSet<String>(RedisStockWriterImpl.EXPIRE_INDEX_KEY, StringCodec.INSTANCE).delete()
    }
}
