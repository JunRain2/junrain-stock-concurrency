package com.junrain.stock.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.util.concurrent.Executor

private val logger = KotlinLogging.logger { }

@Configuration
@Profile("test")
class TestAsyncExecutorConfig {
    /**
     * 호출 스레드에서 즉시 실행해 비동기 작업의 결과를 대기 없이 단언할 수 있게 한다.
     * 단, 예외는 삼킨다 - 별도 스레드에서 도는 실제 executor와 동일하게
     * 작업 내부 예외가 호출자에게 전파되지 않아야 하기 때문.
     */
    @Bean
    fun asyncExecutor(): Executor =
        Executor { command ->
            runCatching { command.run() }
                .onFailure { logger.warn(it) { "비동기 작업 실패" } }
        }
}
