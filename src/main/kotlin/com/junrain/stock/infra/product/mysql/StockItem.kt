package com.junrain.stock.infra.product.mysql

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table

/**
 * SKIP LOCKED 전략 전용 재고 낱개 행. 다른 전략의 [com.junrain.stock.domain.product.Product.stock]과 무관하다.
 *
 * 도메인 포트가 주고받는 애그리거트가 아니라 이 전략 하나만 아는 저장 기법 디테일이라 infra에 둔다.
 * ddl-auto=create가 테이블을 만들도록 엔티티는 두되, 실제 쿼리는 [JdbcStockItemRepository]의 원본 SQL로 한다.
 */
@Entity
@Table(
    name = "stock_items",
    indexes = [Index(name = "idx_stock_items_product_status_id", columnList = "product_id, status, id")],
)
class StockItem(
    @Column(name = "product_id", nullable = false)
    val productId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: StockItemStatus = StockItemStatus.AVAILABLE,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}

enum class StockItemStatus {
    AVAILABLE,
    SOLD,
}
