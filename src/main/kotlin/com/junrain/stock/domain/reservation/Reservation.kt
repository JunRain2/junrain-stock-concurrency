package com.junrain.stock.domain.reservation

import com.junrain.stock.domain.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

/**
 * 구매 요청이 점유한 상품과 수량. 항목은 JSON 한 컬럼에 담는다.
 *
 * 항목별 조회/집계를 하지 않기 때문에 자식 테이블 대신 JSON으로 둔다.
 *
 * 이 행은 재고 차감이 성공한 **뒤에** 저장된다. 행이 있다는 것은 차감이 확정됐다는 뜻이고,
 * 그 역은 성립하지 않는다 - 차감 직후 죽으면 행 없이 재고만 잡힌다. 그건 만료 시각에 회수된다.
 *
 * 상태 컬럼이 없다. 차감 여부를 사후에 판정하지 않으므로 중간 상태를 표현할 이유가 없고,
 * 만료·확정 같은 종료 상태는 그 유스케이스가 생길 때 붙인다.
 */
@Entity
@Table(name = "reservations")
class Reservation(
    @Column(name = "trx_id", unique = true, length = 36)
    val trxId: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "items", columnDefinition = "json")
    val items: List<Item>,
    /** 점유 만료 시각. 이 값으로 회수를 돌리지 않는다 - 회수 기준은 재고를 차감한 저장소가 따로 들고 있다 */
    @Column(name = "expire_at", nullable = false)
    val expireAt: LocalDateTime,
) : BaseEntity() {
    init {
        require(trxId.isNotBlank()) { "트랜잭션 ID는 필수입니다" }
        require(items.isNotEmpty()) { "예약 항목이 없습니다" }
        require(items.distinctBy { it.productId }.size == items.size) { "같은 상품을 중복해서 예약할 수 없습니다" }
    }

    data class Item(
        val productId: Long,
        val quantity: Long,
    ) {
        init {
            require(quantity > 0) { "예약 수량은 1 이상이어야 합니다" }
        }
    }
}
