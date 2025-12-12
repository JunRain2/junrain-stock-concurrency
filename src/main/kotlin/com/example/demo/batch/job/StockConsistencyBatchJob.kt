package com.example.demo.batch.job

import com.example.demo.global.logging.ErrorLogRepository
import com.example.demo.global.logging.ErrorLogType
import com.example.demo.product.command.domain.StockChange
import com.example.demo.product.command.infrastructure.redis.RedisStockRepository
import com.fasterxml.jackson.core.type.TypeReference
import io.github.oshai.kotlinlogging.KotlinLogging
import org.quartz.DisallowConcurrentExecution
import org.quartz.JobExecutionContext
import org.redisson.client.RedisConnectionException
import org.redisson.client.RedisTimeoutException
import org.springframework.scheduling.quartz.QuartzJobBean
import org.springframework.stereotype.Component
import java.util.*

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
        // 타이밍 이슈로 인해 1분 전의 데이터를 가져오기
        errorLogRepository.findAllErrorLog(
            reason = ErrorLogType.STOCK_CHANGE,
            typeRef = object : TypeReference<List<StockChange>>() {},
        ).forEach { errorLog ->
            // 실행했다는 표시 -> 다시 실행 안되게
            errorLogRepository.setExecuted(errorLog.requestKey)

            // Redis에 requestKey가 존재하지 않는 경우 재시도
            if (!redisStockRepository.hasRequestKey(errorLog.requestKey)) {
                val newRequestKey = UUID.randomUUID().toString()
                try {
                    redisStockRepository.updateStocks(
                        requestKey = newRequestKey, stockChanges = errorLog.content.toTypedArray()
                    )
                } catch (e: Exception) {
                    when (e) {
                        // 전송이 안된거니깐 재시도
                        is RedisTimeoutException, is RedisConnectionException -> {
                            errorLogRepository.saveErrorLog(
                                newRequestKey, ErrorLogType.STOCK_CHANGE, errorLog.content
                            )
                        }

                        else -> {
                            logger.error(e) { "예외 발생 심각한지 확인 바람" }
                        }
                    }
                }
            }
        }
    }
}