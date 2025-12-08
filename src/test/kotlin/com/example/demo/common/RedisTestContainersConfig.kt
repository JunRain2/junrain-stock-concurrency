package com.example.demo.common

import com.redis.testcontainers.RedisContainer
import org.springframework.context.annotation.Configuration
import org.testcontainers.utility.DockerImageName

@Configuration
class RedisTestContainersConfig {
    companion object {
        val redisContainer: RedisContainer = RedisContainer(DockerImageName.parse("redis:7-alpine"))
            .apply {
                start()
            }

        init {
            System.setProperty("spring.data.redis.host", redisContainer.host)
            System.setProperty("spring.data.redis.port", redisContainer.firstMappedPort.toString())
            System.setProperty("spring.data.redis.url", "redis://${redisContainer.host}:${redisContainer.firstMappedPort}")
        }
    }
}
