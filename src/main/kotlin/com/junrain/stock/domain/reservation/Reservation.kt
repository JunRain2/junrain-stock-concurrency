package com.junrain.stock.domain.reservation

import com.junrain.stock.domain.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * 재고 점유에 성공한 구매 요청. 점유한 상품과 수량을 JSON 한 컬럼에 담는다.
 *
 * 항목별 조회/집계를 하지 않기 때문에 자식 테이블 대신 JSON으로 둔다.
 */
@Entity
@Table(name = "reservations")
class Reservation(
    @Column(name = "trx_id", unique = true, length = 36)
    val trxId: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "items", columnDefinition = "json")
    val items: List<Item>,
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
