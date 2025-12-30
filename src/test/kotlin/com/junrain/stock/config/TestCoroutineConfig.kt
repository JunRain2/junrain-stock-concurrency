package com.junrain.stock.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("test")
class TestCoroutineConfig {
    @Bean
    fun applicationScope() = CoroutineScope(Dispatchers.Unconfined)
}
