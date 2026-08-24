package com.junrain.stock.infra.product

import com.junrain.stock.application.product.port.StockWriter
import com.junrain.stock.domain.product.exception.ProductNotFoundException
import com.junrain.stock.domain.product.exception.StockUnavailableException
import com.junrain.stock.domain.product.exception.StockUnstableException
import com.junrain.stock.infra.product.mysql.JdbcProductRepository
import com.junrain.stock.infra.product.mysql.JpaProductRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.CannotAcquireLockException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger { }

@Component
class MySqlStockWriterImpl(
    private val jpaProductRepository: JpaProductRepository,
    private val jdbcProductRepository: JdbcProductRepository,
) : StockWriter {
    @Transactional
    override fun decrease(changes: List<StockWriter.StockChange>) =
        // 감소이므로 부호를 뒤집는다
        applyOrThrow(changes.map { StockDelta(it.productId, -it.quantity) }) {
            val reason = diagnose(changes)
            logger.warn { "재고 감소 실패(재시도 불가) : $reason" }
            throw StockUnavailableException(reason)
        }

    @Transactional
    override fun increase(changes: List<StockWriter.StockChange>) =
        // 증가는 재고 조건에 걸릴 일이 없다. 갱신된 행이 모자라면 없는 상품이 섞였다는 뜻
        applyOrThrow(changes.map { StockDelta(it.productId, it.quantity) }) { throw ProductNotFoundException() }

    /**
     * 재고 UPDATE의 두 가지 실패를 한곳에서 처리한다. 증감 양쪽 다 같은 UPDATE라 위험도 같다.
     *
     * - 락 획득 실패: 재고 자체는 멀쩡한 일시 장애이므로 재시도 가능 예외로 바꾼다
     * - 갱신된 행 부족: 조건에 걸린 상품이 있다는 뜻. 의미가 증감마다 다르므로 [onShortfall]에 맡긴다
     */
    private fun applyOrThrow(
        deltas: List<StockDelta>,
        onShortfall: () -> Nothing,
    ) {
        val updated =
            try {
                jdbcProductRepository.applyStockDeltas(deltas)
            } catch (e: CannotAcquireLockException) {
                // 데드락 희생자로 지목되거나 락 대기가 만료된 경우
                logger.warn(e) { "재고 변경 실패(재시도 가능) : 락 획득 실패 $deltas" }
                throw StockUnstableException("락 획득 실패", e)
            }

        // onShortfall이 던지는 예외가 락 실패로 오해받지 않도록 try 밖에서 판정한다
        if (updated != deltas.size) onShortfall()
    }

    /**
     * 조건에 걸린 상품을 찾아 로그용 문자열을 만든다. 실패한 경우에만 도는 추가 조회다.
     *
     * 같은 트랜잭션 안이라 이미 갱신된 행은 감소된 재고가 보인다. 원인 추정용 로그일 뿐 판정 근거가 아니다.
     */
    private fun diagnose(changes: List<StockWriter.StockChange>): String {
        val products = jpaProductRepository.findAllById(changes.map { it.productId }).associateBy { it.id }

        return changes.joinToString(", ") { change ->
            val product = products[change.productId]
            if (product == null) {
                "상품없음(id=${change.productId})"
            } else {
                "재고부족(id=${change.productId}, 요청=${change.quantity}, 현재=${product.stock})"
            }
        }
    }
}
