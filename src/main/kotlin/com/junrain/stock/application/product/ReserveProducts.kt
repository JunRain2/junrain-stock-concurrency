package com.junrain.stock.application.product

import com.junrain.stock.application.product.port.StockWriter
import com.junrain.stock.domain.reservation.Reservation
import com.junrain.stock.domain.reservation.ReservationRepository
import com.junrain.stock.domain.reservation.TrxIdGenerator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 사용자가 제품을 예약하면, Product.stock이 감소하고, Reservation 객체가 생성된다.
 */
@Service
class ReserveProducts(
    private val stockWriter: StockWriter,
    private val reservationRepository: ReservationRepository,
    private val trxIdGenerator: TrxIdGenerator,
) {
    @Transactional
    operator fun invoke(command: Command): Result {
        // 1. Products의 재고를 감소 - 하나라도 실패하면 예외로 트랜잭션이 롤백된다
        stockWriter.decrease(command.toStockChanges())

        // 2. 재고 감소에 성공하면 Reservation을 생성
        val reservation =
            reservationRepository.save(
                Reservation(
                    trxId = trxIdGenerator.generate(),
                    items = command.changes.map { Reservation.Item(it.productId, it.quantity) },
                ),
            )

        return Result(reservation.trxId)
    }

    private fun Command.toStockChanges() = changes.map { StockWriter.StockChange(it.productId, it.quantity) }

    data class Command(
        val changes: List<Change>,
    ) {
        init {
            require(changes.isNotEmpty()) { "예약할 상품이 없습니다." }
            require(changes.distinctBy { it.productId }.size == changes.size) { "같은 상품을 중복해서 예약할 수 없습니다." }
        }

        data class Change(
            val productId: Long,
            val quantity: Long,
        )
    }

    data class Result(
        val trxId: String,
    )
}
