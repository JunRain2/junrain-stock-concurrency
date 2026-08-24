package com.junrain.stock.infra.product

import com.junrain.stock.application.product.port.StockChange
import com.junrain.stock.application.product.port.StockReservation
import com.junrain.stock.infra.product.mysql.JpaProductRepository
import com.junrain.stock.infra.product.redis.RedisStockRepository
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.Executor

@Service
class StockReservationImpl(
    private val redisStockRepository: RedisStockRepository,
    private val jpaProductRepository: JpaProductRepository,
    private val asyncExecutor: Executor,
) : StockReservation {
    override fun reserve(vararg changes: StockChange) {
        val requestKey = UUID.randomUUID().toString()
        redisStockRepository.decreaseStock(requestKey, *changes)
    }

    override fun cancelReservation(vararg changes: StockChange) {
        increaseRedisStock(*changes)
    }

    private fun increaseRedisStock(vararg changes: StockChange) {
        val requestKey = UUID.randomUUID().toString()

        redisStockRepository.increaseStock(requestKey, *changes)
    }

    override fun decrease(vararg changes: StockChange) {
        changes.forEach { change ->
            jpaProductRepository.updateProductStock(
                change.productId,
                -change.quantity,
            )
        }
    }

    override fun increase(vararg changes: StockChange) {
        // DB 선 증가
        changes.forEach { change ->
            jpaProductRepository.updateProductStock(
                change.productId,
                change.quantity,
            )
        }

        // Redis 증가 -> 비동기로 실행
        asyncExecutor.execute {
            increaseRedisStock(*changes)
        }
    }
}
