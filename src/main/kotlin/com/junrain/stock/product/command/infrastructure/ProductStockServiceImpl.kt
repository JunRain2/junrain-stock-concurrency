package com.junrain.stock.product.command.infrastructure

import com.junrain.stock.product.command.domain.ProductStockService
import com.junrain.stock.product.command.domain.StockChange
import com.junrain.stock.product.command.infrastructure.mysql.JpaProductRepository
import com.junrain.stock.product.command.infrastructure.redis.RedisStockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service
import java.util.*

@Service
class ProductStockServiceImpl(
    private val redisStockRepository: RedisStockRepository,
    private val jpaProductRepository: JpaProductRepository,
    private val applicationScope: CoroutineScope,
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
                change.productId, -change.quantity
            )
        }
    }

    override fun increase(vararg changes: StockChange) {
        // DB 선 증가
        changes.forEach { change ->
            jpaProductRepository.updateProductStock(
                change.productId, change.quantity
            )
        }

        // Redis 증가 -> 비동기로 실행
        applicationScope.launch {
            increaseRedisStock(*changes)
        }
    }
}