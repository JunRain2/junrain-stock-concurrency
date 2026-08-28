package com.junrain.stock.infra.common.redis

import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RedissonConfig {
    /**
     * 재시도를 끈다(기본 3회). **이 설정이 오버셀을 막는 유일한 장치다.**
     *
     * 차감 스크립트에는 멱등키가 없다. 응답 타임아웃 뒤 커넥션 계층이 같은 명령을 다시 보내면
     * 그대로 두 번 차감되고, 그 순간 재고는 실제보다 적어지는 게 아니라 **팔린 양이 부풀어** 오버셀이 된다.
     *
     * 응답을 못 받은 요청은 판정하지 않고 409로 끝낸다. 적용됐다면 언더셀로 남고,
     * 그건 [com.junrain.stock.infra.product.redis.StockReconciler]가 총량으로 되돌린다.
     */
    @Bean
    fun disableRedissonRetry() = RedissonAutoConfigurationCustomizer { it.useSingleServer().setRetryAttempts(0) }
}
