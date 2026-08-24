package com.junrain.stock.infra.product.mysql

import com.junrain.stock.domain.product.Product
import com.junrain.stock.domain.product.exception.ProductCreationException
import com.junrain.stock.domain.product.vo.ProductCode
import com.junrain.stock.infra.product.StockDelta
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataAccessException
import org.springframework.dao.TransientDataAccessException
import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

@Component
class JdbcProductRepository(
    private val jdbcTemplate: JdbcTemplate,
    @param:Value("\${bulk-insert.chunk-size}") private val chunkSize: Int,
    @param:Value("\${bulk-insert.retry-milliseconds}") private val retryDelays: List<Long>,
) {
    fun bulkInsert(
        products: List<Product>,
        createdAt: LocalDateTime = LocalDateTime.now(),
    ): List<Result<ProductCode>> =
        buildList {
            var retry = mutableListOf<Product>()
            products.chunked(chunkSize).forEach { chunked ->
                try {
                    addAll(saveAndReturnedResult(chunked, createdAt))
                } catch (e: TransientDataAccessException) {
                    retry.addAll(chunked)
                } catch (e: DataAccessException) {
                    addAll(chunked.map { Result.failure(ProductCreationException(it.code)) })
                }
            }

            for (delay in retryDelays) {
                if (retry.isEmpty()) break

                Thread.sleep(delay)
                val tmp = mutableListOf<Product>()
                retry.chunked(chunkSize).forEach { chunked ->
                    try {
                        addAll(saveAndReturnedResult(chunked, createdAt))
                    } catch (e: TransientDataAccessException) {
                        logger.error(e) { "데이터베이스에 일시적으로 접근 불가" }
                        tmp.addAll(chunked)
                    } catch (e: DataAccessException) {
                        addAll(chunked.map { Result.failure(ProductCreationException(it.code)) })
                    }
                }
                retry = tmp
            }

            addAll(
                retry.map {
                    Result.failure(
                        ProductCreationException(code = it.code),
                    )
                },
            )
        }

    private fun saveAndReturnedResult(
        products: List<Product>,
        createdAt: LocalDateTime,
    ): List<Result<ProductCode>> {
        val sql =
            """
            INSERT
            INTO products (owner_id, product_code, product_price, product_currency_code, stock, name, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE product_code = product_code
            """.trimIndent()

        val now = Timestamp.valueOf(createdAt)

        jdbcTemplate.batchUpdate(
            sql,
            object : BatchPreparedStatementSetter {
                override fun setValues(
                    ps: java.sql.PreparedStatement,
                    i: Int,
                ) {
                    val product = products[i]
                    ps.setLong(1, product.ownerId)
                    ps.setString(2, product.code.code)
                    ps.setBigDecimal(3, product.price.amount)
                    ps.setString(4, product.price.currencyCode.name)
                    ps.setLong(5, product.stock)
                    ps.setString(6, product.name)
                    ps.setTimestamp(7, now)
                    ps.setTimestamp(8, now)
                }

                override fun getBatchSize(): Int = products.size
            },
        )

        return products.indices.map { idx ->
            Result.success(products[idx].code)
        }
    }

    /**
     * 여러 상품의 재고를 UPDATE 한 방으로 증감한다. 상품 수만큼 왕복하지 않기 위해 CASE로 묶는다.
     *
     * 재고가 모자란 행은 WHERE 조건에 걸려 갱신되지 않으므로,
     * 반환값이 상품 수보다 작으면 실패한 상품이 있다는 뜻이다.
     *
     * 같은 상품이 여러 번 오면 CASE는 첫 값만, IN은 한 번만 쓰므로 갱신 행 수와 요청 수가 어긋난다.
     * 조용히 합산하면 호출부가 오해하므로 예외로 막는다.
     * 정렬은 SQL 텍스트를 결정적으로 만들어 문 캐시를 태우기 위함이다.
     * 데드락은 단일 UPDATE가 PK 인덱스를 오름차순 스캔하는 것으로 막힌다.
     *
     * @return 갱신된 행 수. 요청한 상품 수보다 작으면 조건에 걸린 상품이 있다는 뜻이다
     * @throws IllegalArgumentException 같은 상품이 여러 번 들어온 경우
     */
    fun applyStockDeltas(deltas: List<StockDelta>): Int {
        if (deltas.isEmpty()) return 0
        require(deltas.distinctBy { it.productId }.size == deltas.size) {
            "같은 상품의 재고를 중복해서 변경할 수 없습니다."
        }

        val sorted = deltas.sortedBy { it.productId }

        val stockCase = sorted.joinToString(" ") { "WHEN ? THEN ?" }
        val sql =
            """
            UPDATE products
            SET stock = stock + CASE id $stockCase END
            WHERE id IN (${sorted.joinToString(", ") { "?" }})
              AND stock + CASE id $stockCase END >= 0
            """.trimIndent()

        val args =
            buildList {
                sorted.forEach { (id, quantity) ->
                    add(id)
                    add(quantity)
                }
                addAll(sorted.map { it.productId })
                sorted.forEach { (id, quantity) ->
                    add(id)
                    add(quantity)
                }
            }

        return jdbcTemplate.update(sql, *args.toTypedArray())
    }
}
