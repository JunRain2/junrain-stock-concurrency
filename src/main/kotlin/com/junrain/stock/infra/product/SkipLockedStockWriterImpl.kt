package com.junrain.stock.infra.product

import com.junrain.stock.application.product.port.StockWriter
import com.junrain.stock.domain.product.exception.ProductNotFoundException
import com.junrain.stock.domain.product.exception.StockUnavailableException
import com.junrain.stock.domain.product.exception.StockUnstableException
import com.junrain.stock.infra.product.mysql.JdbcStockItemRepository
import com.junrain.stock.infra.product.mysql.JpaProductRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.dao.CannotAcquireLockException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger { }

/**
 * 재고를 낱개 행(stock_items)으로 물리적으로 쪼개고 SELECT ... FOR UPDATE SKIP LOCKED로 나눠 잡는 전략.
 *
 * 상품별로 SELECT+UPDATE를 따로 하므로, CASE-UPDATE 방식과 달리 같은 상품이 여러 번 들어와도
 * SQL이 깨지지 않는다(중복 가드 불필요) - 두 번째 요청은 남은 가용 행을 마저 잡을 뿐이다.
 * SKIP LOCKED는 절대 대기하지 않으므로 락 순서로 인한 데드락도 구조상 없다.
 */
@Component
@ConditionalOnProperty(name = [StockStrategy.PROPERTY], havingValue = "skip-locked")
class SkipLockedStockWriterImpl(
    private val jdbcStockItemRepository: JdbcStockItemRepository,
    private val jpaProductRepository: JpaProductRepository,
) : StockWriter {
    @Transactional
    override fun decrease(changes: List<StockWriter.StockChange>) {
        changes.forEach { change ->
            val reservedIds =
                try {
                    jdbcStockItemRepository.selectAvailableForUpdateSkipLocked(change.productId, change.quantity)
                } catch (e: CannotAcquireLockException) {
                    logger.warn(e) { "재고 변경 실패(재시도 가능) : 락 획득 실패 $change" }
                    throw StockUnstableException("락 획득 실패", e)
                }

            if (reservedIds.size < change.quantity) {
                val reason = "재고부족(id=${change.productId}, 요청=${change.quantity}, 확보=${reservedIds.size})"
                logger.warn { "재고 감소 실패(재시도 불가) : $reason" }
                throw StockUnavailableException(reason)
            }

            jdbcStockItemRepository.markSold(reservedIds)
        }
    }

    @Transactional
    override fun increase(changes: List<StockWriter.StockChange>) {
        changes.forEach { change ->
            if (!jpaProductRepository.existsById(change.productId)) throw ProductNotFoundException()

            jdbcStockItemRepository.insertAvailable(change.productId, change.quantity)
        }
    }
}
