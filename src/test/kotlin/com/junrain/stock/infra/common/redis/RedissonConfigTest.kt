package com.junrain.stock.infra.common.redis

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.redisson.config.Config
import kotlin.test.Test

class RedissonConfigTest {
    @Test
    fun `커넥션 재시도는 꺼져 있어야 한다`() {
        // 차감 스크립트에 멱등키가 없다. 재시도가 켜지면 같은 차감이 두 번 적용돼 곧바로 오버셀이다
        val config = Config()

        RedissonConfig().disableRedissonRetry().customize(config)

        withClue("retryAttempts를 올리면 오버셀이 난다. 올려야 한다면 멱등키부터 되살릴 것") {
            config.useSingleServer().retryAttempts shouldBe 0
        }
    }
}
