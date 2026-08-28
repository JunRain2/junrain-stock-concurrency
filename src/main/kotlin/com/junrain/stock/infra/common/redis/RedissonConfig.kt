package com.junrain.stock.infra.common.redis

import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RedissonConfig {
    /**
     * 재시도를 끈다(기본 3회).
     *
     * 점유 스크립트는 `reservation:{trxId}` 존재 검사로 멱등하므로 같은 명령이 두 번 도착해도
     * 두 번 차감되지 않는다. 즉 이 설정은 더 이상 오버셀을 막는 장치가 아니다 - 그 책임은
     * 설정값에서 스크립트 안으로 옮겨 갔다.
     *
     * 그래도 꺼 두는 이유는 다르다. Redis가 느려진 상황에서 재시도는 이미 밀린 단일 스레드에
     * 같은 부하를 배로 얹는다. 응답을 못 받은 요청은 409로 끝내고, 적용됐다면 만료 시각에 회수된다.
     * 재시도를 켜는 것 자체는 이제 안전하다.
     */
    @Bean
    fun disableRedissonRetry() = RedissonAutoConfigurationCustomizer { it.useSingleServer().setRetryAttempts(0) }
}
