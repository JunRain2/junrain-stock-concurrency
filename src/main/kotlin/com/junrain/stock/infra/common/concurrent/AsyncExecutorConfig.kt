package com.junrain.stock.infra.common.concurrent

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Configuration
@Profile("!test")
class AsyncExecutorConfig {
    /**
     * 요청 처리와 분리해서 실행하는 비동기 작업용 executor.
     * 반환 타입이 ExecutorService라 Spring이 종료 시 close()를 호출하고,
     * close()는 진행 중인 작업이 끝날 때까지 대기한다.
     */
    @Bean
    fun asyncExecutor(): ExecutorService = Executors.newVirtualThreadPerTaskExecutor()
}
