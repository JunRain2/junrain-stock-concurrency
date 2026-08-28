package com.junrain.stock.infra.common.concurrent

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * 테스트에서는 켜지 않는다. 정합 배치는 테스트가 원하는 시점에 직접 부르는 편이 낫고,
 * 배경에서 도는 배치가 검증 중인 재고를 먼저 되돌리면 테스트가 흔들린다.
 */
@Configuration
@Profile("!test")
@EnableScheduling
class SchedulingConfig
