package com.junrain.stock.infra.product.redis

import com.fasterxml.jackson.databind.ObjectMapper
import com.junrain.stock.application.product.port.StockWriter
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/** 전용 파일 어펜더로 빠진다. logback-spring.xml의 stock-op 로거 참고. */
private val opLogger = KotlinLogging.logger("stock-op")

/**
 * Redis 재고 연산의 선행 기록(WAL).
 *
 * Redis 호출이 타임아웃 나면 적용 여부를 알 수 없다. 그래서 호출 전에 pending, 성공 후에 done을 남긴다.
 * pending만 있고 done이 없는 op_id가 재시도 대상이다. 수집·재시도는 다음 스텝.
 */
@Component
class StockOpLogger(
    private val objectMapper: ObjectMapper,
) {
    fun pending(
        opId: String,
        changes: List<StockWriter.StockChange>,
    ) = opLogger.info { "pending\t$opId\t${objectMapper.writeValueAsString(changes)}" }

    fun done(opId: String) = opLogger.info { "done\t$opId" }
}
