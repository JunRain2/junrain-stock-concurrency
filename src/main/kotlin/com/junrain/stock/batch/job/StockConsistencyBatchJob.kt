package com.junrain.stock.batch.job

import com.junrain.stock.config.jdbc.ErrorLogRepository
import com.junrain.stock.config.jdbc.ErrorLogType
import com.junrain.stock.product.command.domain.StockChange
import com.junrain.stock.product.command.infrastructure.redis.RedisStockRepository
import com.fasterxml.jackson.core.type.TypeReference
import io.github.oshai.kotlinlogging.KotlinLogging
import org.quartz.DisallowConcurrentExecution
import org.quartz.JobExecutionContext
import org.redisson.client.RedisConnectionException
import org.redisson.client.RedisException
import org.redisson.client.RedisTimeoutException
import org.springframework.scheduling.quartz.QuartzJobBean
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger { }

@Component
@DisallowConcurrentExecution // 동시 실행 방지
class StockConsistencyBatchJob(
    private val errorLogRepository: ErrorLogRepository,
    private val redisStockRepository: RedisStockRepository
) : QuartzJobBean() {
    /**
     * requestKey + 실패했던 요청 가져오기 created_at 내림차순 으로
     * requestKey 재시도 요청 -> 플래그 true로 만들기
     * 여기는 판단하는 곳이 아님, 그냥 실행하는 곳임
     *
     * RedisStockRepository 를 가져다 쓸까 말까?
     * 가져다 쓰자 어차피 Redis와 MySQL의 정합성을 맞추기 위한
     * 코드라 둘 중 하나라도 계약이 바뀌면 바뀌는게 맞아
     */
    override fun executeInternal(context: JobExecutionContext) {
        errorLogRepository.findAllErrorLog(
            reason = ErrorLogType.STOCK_CHANGE,
            typeRef = object : TypeReference<List<StockChange>>() {},
        ).forEach { errorLog ->
            val requestKey = errorLog.requestKey

            try {
                // Redis 상태 확인 -> 예외 발생시 이후에도 예외가 계속될 수 있다 판단하여 Job 자체를 실패 처리
                if (redisStockRepository.hasRequestKey(requestKey)) {
                    logger.info { "이미 Redis에 존재: $requestKey" }
                    errorLogRepository.setExecuted(requestKey)
                    return@forEach
                }

                // 재시도 실행
                redisStockRepository.increaseStock(
                    requestKey = requestKey, stockChanges = errorLog.content.toTypedArray()
                )

                errorLogRepository.setExecuted(requestKey)
                logger.info { "재시도 성공: $requestKey" }
            } catch (e: Exception) {
                when (e) {
                    // 네트워크 장애 - 다음 스케줄에서 재시도
                    is RedisTimeoutException, is RedisConnectionException -> {
                        logger.warn { "네트워크 장애로 재시도 대기: $requestKey" }
                    }

                    // 기타 Redis 예외 - 성공 처리
                    is RedisException -> {
                        logger.info { "Redis 예외지만 성공 처리: $requestKey - ${e.message}" }
                        errorLogRepository.setExecuted(requestKey)
                    }

                    // 예상치 못한 예외 - 성공 처리 (무한 재시도 방지)
                    else -> {
                        logger.error(e) { "예상치 못한 예외, 성공 처리: $requestKey" }
                        errorLogRepository.setExecuted(requestKey)
                    }
                }
            }
        }
    }
}
