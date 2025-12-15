package com.example.demo.product.command.infrastructure

import com.example.demo.global.contract.InfraException
import com.example.demo.global.logging.ErrorLogRepository
import com.example.demo.global.logging.ErrorLogType
import com.example.demo.product.command.domain.ProductStockService
import com.example.demo.product.command.domain.StockChange
import com.example.demo.product.command.infrastructure.mysql.JpaProductRepository
import com.example.demo.product.command.infrastructure.redis.RedisStockRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.redisson.client.RedisConnectionException
import org.redisson.client.RedisException
import org.redisson.client.RedisTimeoutException
import org.springframework.stereotype.Service
import java.util.*

private val logger = KotlinLogging.logger { }

@Service
class ProductStockServiceImpl(
    private val redisStockRepository: RedisStockRepository,
    private val jpaProductRepository: JpaProductRepository,
    private val errorLogRepository: ErrorLogRepository,
    private val applicationScope: CoroutineScope,
) : ProductStockService {
    override fun reserve(vararg changes: StockChange) {
        val requestKey = UUID.randomUUID().toString()

        val minusStocks = changes.map { StockChange(it.productId, -it.quantity) }.toTypedArray()
        try {
            redisStockRepository.updateStocks(requestKey, *minusStocks)
        } catch (e: Exception) {
            if (e is RedisException) {
                when (e) {
                    // Redis에 도달하지 못했기에 롤백 필요 X
                    is RedisConnectionException -> {
                        logger.error { "RedisConnectionException 발생 : ${e.message}" }
                    }
                    // Redis에 도달했는지 안했는지 알 수 없기에 추가로직이 필요
                    is RedisTimeoutException -> {
                        logger.error { "RedisTimeoutException 발생 데이터베이스에 저장 : ${e.message}" }
                        applicationScope.launch {
                            saveRedisStockChangeException(requestKey, *changes)
                        }
                    }
                    // 그 외, 인프라 예외 -> 롤백 처리함
                    else -> {
                        logger.error(e) { "Redis 예외 발생" }
                        applicationScope.launch {
                            increaseRedisStock(*changes)
                        }
                    }
                }
                throw InfraException(e)
            }
            throw e
        }
    }

    private fun saveRedisStockChangeException(requestKey: String, vararg changes: StockChange) {
        errorLogRepository.saveErrorLog(
            requestKey = requestKey, reason = ErrorLogType.STOCK_CHANGE, content = changes.toList()
        )
    }

    override fun cancelReservation(vararg changes: StockChange) {
        increaseRedisStock(*changes)
    }

    private fun increaseRedisStock(vararg changes: StockChange) {
        val requestKey = UUID.randomUUID().toString()

        try {
            redisStockRepository.updateStocks(requestKey, *changes)
        } catch (e: Exception) {
            when (e) {
                is RedisConnectionException, is RedisTimeoutException -> {
                    logger.error(e) { "Stock 증가 도중 예외 발생 DB에 저장 : ${e.message}" }
                    applicationScope.launch {
                        saveRedisStockChangeException(requestKey, *changes)
                    }
                    throw InfraException(e)
                }
            }
            throw e
        }
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