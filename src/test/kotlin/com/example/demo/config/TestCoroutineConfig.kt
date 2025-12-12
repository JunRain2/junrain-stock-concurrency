package com.example.demo.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class TestCoroutineConfig {
    @Bean
    @Primary
    fun applicationScope() = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
}
