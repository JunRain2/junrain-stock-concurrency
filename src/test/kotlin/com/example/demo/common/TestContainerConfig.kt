package com.example.demo.common

import com.redis.testcontainers.RedisContainer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration
class TestContainerConfig {

    companion object {
        private val mysqlContainer = MySQLContainer(DockerImageName.parse("mysql:8.0"))
            .apply {
                withDatabaseName("test_db")
                withUsername("test")
                withPassword("test")
                withReuse(true)
                start()
            }

        private val redisContainer = RedisContainer(DockerImageName.parse("redis:7-alpine"))
            .apply {
                withReuse(true)
                start()
            }
    }

    @Bean
    fun mysqlContainer(): MySQLContainer<*> = mysqlContainer

    @Bean
    fun redisContainer(): RedisContainer = redisContainer
}
