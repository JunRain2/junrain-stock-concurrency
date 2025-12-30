package com.junrain.stock.config

import eu.rekawek.toxiproxy.ToxiproxyClient
import org.springframework.context.annotation.Configuration
import org.testcontainers.containers.Network
import org.testcontainers.toxiproxy.ToxiproxyContainer

@Configuration
class ToxiproxyTestContainerConfig {
    companion object {
        const val TOXI_REDIS_PORT = 8666

        val toxiNetwork: Network = Network.newNetwork()

        val toxiproxy = ToxiproxyContainer("ghcr.io/shopify/toxiproxy")
            .apply {
                withNetwork(toxiNetwork)
                withReuse(true)
                start()
            }

        val toxiproxyClient = ToxiproxyClient(toxiproxy.host, toxiproxy.controlPort)
    }
}