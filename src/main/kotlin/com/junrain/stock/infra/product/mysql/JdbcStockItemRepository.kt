package com.junrain.stock.infra.product.mysql

import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.PreparedStatement

@Component
class JdbcStockItemRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    /**
     * 가용 행 중 [quantity]개를 잠그고 확보한다. 대기하지 않으므로 다른 트랜잭션이 이미 잠근 행은 건너뛴다.
     *
     * @return 확보한 행의 id 목록. [quantity]보다 적으면 재고가 모자란 것이다
     */
    fun selectAvailableForUpdateSkipLocked(
        productId: Long,
        quantity: Long,
    ): List<Long> =
        jdbcTemplate.queryForList(
            """
            SELECT id FROM stock_items
            WHERE product_id = ? AND status = 'AVAILABLE'
            ORDER BY id
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """.trimIndent(),
            Long::class.java,
            productId,
            quantity,
        )

    fun markSold(ids: List<Long>) {
        if (ids.isEmpty()) return

        jdbcTemplate.update(
            "UPDATE stock_items SET status = 'SOLD' WHERE id IN (${ids.joinToString(", ") { "?" }})",
            *ids.toTypedArray(),
        )
    }

    /**
     * 신규 가용 행을 [quantity]개 추가한다. 재입고를 기존 SOLD 행 복원이 아니라 새 물량으로 다룬다 -
     * 행은 서로 구분할 의미가 없는 fungible 단위라 어떤 행을 되돌릴지 고를 이유가 없다.
     */
    fun insertAvailable(
        productId: Long,
        quantity: Long,
    ) {
        if (quantity <= 0) return

        jdbcTemplate.batchUpdate(
            "INSERT INTO stock_items (product_id, status) VALUES (?, 'AVAILABLE')",
            object : BatchPreparedStatementSetter {
                override fun setValues(
                    ps: PreparedStatement,
                    i: Int,
                ) {
                    ps.setLong(1, productId)
                }

                override fun getBatchSize(): Int = quantity.toInt()
            },
        )
    }
}
