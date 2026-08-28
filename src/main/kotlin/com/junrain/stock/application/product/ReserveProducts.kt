package com.junrain.stock.application.product

import com.junrain.stock.application.product.port.StockWriter
import com.junrain.stock.domain.reservation.Reservation
import com.junrain.stock.domain.reservation.ReservationRepository
import com.junrain.stock.domain.reservation.TrxIdGenerator
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 사용자가 제품을 예약하면 재고가 감소하고 Reservation이 생성된다.
 *
 * **순서가 설계다.** 재고를 먼저 잡고 예약을 기록한다. 뒤집으면 "행은 있는데 재고는 안 깎였다"가
 * 되어 오버셀이고, 이 순서에서 생길 수 있는 최악은 "재고는 깎였는데 행이 없다" - 언더셀이며
 * 만료 시각에 회수된다.
 *
 * **`@Transactional`을 붙이지 않는다.** 커밋해야 할 쓰기가 하나뿐이라 붙일 이유가 없고,
 * 붙이면 Redis 왕복 내내 DB 커넥션과 트랜잭션을 붙잡고 있게 된다.
 */
@Service
class ReserveProducts(
    private val stockWriter: StockWriter,
    private val reservationRepository: ReservationRepository,
    private val trxIdGenerator: TrxIdGenerator,
    @param:Value("\${stock.reserve.hold-minutes}") private val holdMinutes: Long,
) {
    operator fun invoke(command: Command): Result {
        val trxId = trxIdGenerator.generate()

        // 한 번만 계산해서 두 저장소에 같은 값을 넣는다. 각자 자기 시계로 찍게 두면 같은 예약의
        // 만료 시각이 두 개가 되고, 회수 시점과 화면에 보이는 시각이 어긋난다
        val expireAt = LocalDateTime.now().plusMinutes(holdMinutes)

        // 실패는 예외로 끝난다. 아직 아무것도 기록하지 않았으므로 지울 것도 없다
        stockWriter.reserve(trxId, command.toStockChanges(), expireAt)

        // 여기서 죽으면 재고는 잡힌 채 예약이 남지 않는다. 만료 시각에 회수되므로 판정하지 않는다
        reservationRepository.save(command.toReservation(trxId, expireAt))

        return Result(trxId)
    }

    private fun Command.toReservation(
        trxId: String,
        expireAt: LocalDateTime,
    ) = Reservation(
        trxId = trxId,
        items = changes.map { Reservation.Item(it.productId, it.quantity) },
        expireAt = expireAt,
    )

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
