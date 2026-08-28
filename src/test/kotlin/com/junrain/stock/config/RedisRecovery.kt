package com.junrain.stock.config

import org.redisson.api.RedissonClient

private const val ATTEMPTS = 40
private const val INTERVAL_MILLIS = 250L

/**
 * 장애를 걷어도 커넥션 풀은 곧바로 회복되지 않는다.
 *
 * 재시도를 껐으므로([com.junrain.stock.infra.common.redis.RedissonConfig]) 상처 난 커넥션이 다음 명령을
 * 그대로 실패시킨다. 프록시를 테스트끼리 공유하니 상처를 다음 테스트로 넘기지 않고 여기서 회복시킨다.
 */
fun RedissonClient.awaitRecovered() {
    repeat(ATTEMPTS) {
        runCatching { keys.count() }.onSuccess { return }
        Thread.sleep(INTERVAL_MILLIS)
    }
    error("Redis 커넥션이 회복되지 않았습니다.")
}
