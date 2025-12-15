package com.example.demo.global.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("!test")
class CoroutineConfig {
    @Bean
    fun applicationScope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}