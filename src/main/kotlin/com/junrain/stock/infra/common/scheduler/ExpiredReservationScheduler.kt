package com.junrain.stock.infra.common.scheduler

import com.junrain.stock.infra.product.redis.ExpiredReservationReclaimer
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * 만료 회수를 언제 도는지만 정한다. 무엇을 하는지는 [ExpiredReservationReclaimer]가 안다.
 *
 * 이전 실행이 끝난 뒤부터 간격을 센다(fixedDelay). 회수가 길어지면 다음 실행이 그만큼 밀린다.
 */
@Component
class ExpiredReservationScheduler(
    private val reclaimer: ExpiredReservationReclaimer,
) {
    @Scheduled(fixedDelayString = "\${stock.reclaim.delay-seconds}", timeUnit = TimeUnit.SECONDS)
    fun reclaimExpiredReservations() {
        reclaimer.reclaimExpired()
    }
}
