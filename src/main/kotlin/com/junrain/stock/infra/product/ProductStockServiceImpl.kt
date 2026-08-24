package com.junrain.stock.infra.product

import com.junrain.stock.domain.product.ProductStockService
import com.junrain.stock.domain.product.StockChange
import com.junrain.stock.infra.product.mysql.JpaProductRepository
import com.junrain.stock.infra.product.redis.RedisStockRepository
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.Executor

@Service
class ProductStockServiceImpl(
    private val redisStockRepository: RedisStockRepository,
    private val jpaProductRepository: JpaProductRepository,
    private val asyncExecutor: Executor,
) : ProductStockService {
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
