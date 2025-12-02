package com.example.demo.common

import com.redis.testcontainers.RedisContainer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@SpringBootTest
@Testcontainers
abstract class IntegrationTestBase {

    companion object {
        @Container
        private val mysqlContainer = MySQLContainer(DockerImageName.parse("mysql:8.0"))
            .apply {
                withDatabaseName("test_db")
                withUsername("test")
                withPassword("test")
                withReuse(true)
            }

        @Container
        private val redisContainer = RedisContainer(DockerImageName.parse("redis:7-alpine"))
            .apply {
                withReuse(true)
            }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl)
            registry.add("spring.datasource.username", mysqlContainer::getUsername)
            registry.add("spring.datasource.password", mysqlContainer::getPassword)
            registry.add("spring.datasource.driver-class-name") { "com.mysql.cj.jdbc.Driver" }
            registry.add("spring.jpa.hibernate.ddl-auto") { "create-drop" }

            registry.add("spring.data.redis.host", redisContainer::getHost)
            registry.add("spring.data.redis.port", redisContainer::getFirstMappedPort)

            // Redisson 설정
            registry.add("spring.data.redis.url") {
                "redis://${redisContainer.host}:${redisContainer.firstMappedPort}"
            }
        }
    }
}
