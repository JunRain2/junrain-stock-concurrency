package com.example.demo.common

import com.example.demo.common.ToxiproxyTestContainerConfig.Companion.TOXI_REDIS_PORT
import com.example.demo.common.ToxiproxyTestContainerConfig.Companion.toxiNetwork
import com.example.demo.common.ToxiproxyTestContainerConfig.Companion.toxiproxy
import com.example.demo.common.ToxiproxyTestContainerConfig.Companion.toxiproxyClient
import org.springframework.context.annotation.Configuration
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

@Configuration
class RedisTestContainersConfig() {
    companion object {
        private const val REDIS_PORT = 6379

        private val redisContainer = GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .apply {
                withExposedPorts(REDIS_PORT)
                withNetwork(toxiNetwork)
                withNetworkAliases("redis")
                withReuse(true)
                start()
            }

        val redisProxy =
            toxiproxyClient.createProxy("redis", "0.0.0.0:$TOXI_REDIS_PORT", "redis:$REDIS_PORT")

        init {
            System.setProperty("spring.data.redis.host", toxiproxy.host)
            System.setProperty(
                "spring.data.redis.port",
                toxiproxy.getMappedPort(TOXI_REDIS_PORT).toString()
            )
        }
    }
}
