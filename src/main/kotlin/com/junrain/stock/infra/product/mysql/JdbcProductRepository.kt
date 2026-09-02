package com.junrain.stock.infra.product.mysql

import com.junrain.stock.domain.product.Product
import com.junrain.stock.domain.product.exception.ProductCreationException
import com.junrain.stock.domain.product.exception.ProductDuplicateCodeException
import com.junrain.stock.domain.product.vo.ProductCode
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.CannotGetJdbcConnectionException
import org.springframework.jdbc.core.ConnectionCallback
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.Statement
import java.sql.Timestamp
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

@Component
class JdbcProductRepository(
    private val jdbcTemplate: JdbcTemplate,
    @param:Value("\${bulk-insert.chunk-size}") private val chunkSize: Int,
) {
    /**
     * 여러 상품을 신규로만 삽입한다. 이미 있는 코드는 덮어쓰지 않고 실패로 돌려준다. 부분 성공을 허용한다.
     *
     * 실패한 청크를 서버가 다시 시도하지 않는다. 커넥션을 못 받았으면 전건을 포기하고,
     * 그 밖의 실패는 그 청크만 실패로 확정한다.
     *
     * @return 입력과 같은 길이, 같은 순서. i번째 원소가 i번째 입력 행의 결과다
     */
    fun bulkInsert(products: List<Product>): List<Result<Long>> {
        if (products.isEmpty()) return emptyList()

        val now = Timestamp.valueOf(LocalDateTime.now())
        val results = MutableList<Result<Long>?>(products.size) { null }
        val chunks = chunksWithOffset(products)

        try {
            // 대여는 요청당 1회지만 트랜잭션은 열지 않는다(설계 문서 8.4). autocommit이라 청크 하나가
            // 커밋 하나이고, 여기서 setAutoCommit(false)를 넣으면 5,000행이 한 트랜잭션이 되어
            // 청크 단위 부분 성공이 깨진다.
            jdbcTemplate.execute(
                ConnectionCallback { connection ->
                    chunks.forEach { chunk ->
                        try {
                            writeChunk(connection, chunk, now, results)
                        } catch (e: SQLException) {
                            // 서버 안에서 다시 두드리면 그 부하를 우리가 키운다. 클라이언트 재전송은 그 사이 자원을
                            // 놓아주므로 부하 조절이 시스템 바깥에서 일어난다 - 재전송은 INSERT IGNORE 덕분에 안전하다.
                            logger.error(e) { "청크 삽입 실패 - 이 청크를 실패로 확정한다" }
                            giveUp(chunk, results)
                        }
                    }
                },
            )
        } catch (e: CannotGetJdbcConnectionException) {
            // 대여가 한 번뿐이라 여기 오면 아직 아무 청크도 돌지 않았다
            logger.error(e) { "커넥션 획득 실패 - 전건 포기한다" }
            chunks.forEach { giveUp(it, results) }
        }

        return results.requireNoNulls()
    }

    // 청크 하나가 chunkSize * COLUMN_COUNT 개의 파라미터를 쓴다. MySQL prepared statement 상한이
    // 65,535개라 chunk-size가 8,191을 넘으면 문장이 깨진다. max_allowed_packet도 같이 본다.
    private fun chunksWithOffset(products: List<Product>): List<IndexedChunk> =
        (products.indices step chunkSize).map { offset ->
            IndexedChunk(offset, products.subList(offset, minOf(offset + chunkSize, products.size)))
        }

    /**
     * 청크 하나를 삽입하고 그 결과를 [results]의 제자리에 꽂는다.
     *
     * @throws SQLException 삽입 실패. 호출자가 이 청크를 실패로 확정한다
     */
    private fun writeChunk(
        connection: Connection,
        chunk: IndexedChunk,
        now: Timestamp,
        results: MutableList<Result<Long>?>,
    ) {
        val idByCode = findIdByCode(connection, insertAtOnce(connection, chunk.products, now))

        chunk.products.forEachIndexed { i, product ->
            val id = idByCode[product.code]
            results[chunk.offset + i] =
                when {
                    id != null -> Result.success(id)

                    // 문장은 성공했는데 이 코드가 안 들어갔다 = INSERT IGNORE가 조용히 스킵한 기존 코드다
                    else -> Result.failure(ProductDuplicateCodeException(product.code))
                }
        }
    }

    /**
     * 멀티밸류 INSERT 한 문장을 보내고 실제로 삽입된 행의 id를 돌려준다.
     *
     * 이미 있는 코드는 `INSERT IGNORE`가 조용히 스킵하므로 반환 목록에 없다.
     */
    private fun insertAtOnce(
        connection: Connection,
        products: List<Product>,
        now: Timestamp,
    ): List<Long> {
        // rewriteBatchedStatements가 기본값 false라 batchUpdate는 드라이버가 행마다 한 번씩 서버로 보낸다
        // (5,000행 = 왕복 5,000회). 드라이버 옵션은 URL 하나로 조용히 꺼질 수 있어서 성능을 거기에 걸지 않았다.
        // IGNORE 대신 ON DUPLICATE KEY UPDATE를 쓰지 않은 이유는 설계 문서 3.1(mysql-insert-ignore-설계.md)에 있다.
        val sql = INSERT_PREFIX + List(products.size) { VALUES_TUPLE }.joinToString(",")

        return connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { statement ->
            products.forEachIndexed { i, product -> bind(statement, i * COLUMN_COUNT, product, now) }
            statement.executeUpdate()
            statement.generatedKeys.use { keys ->
                buildList { while (keys.next()) add(keys.getLong(1)) }
            }
        }
    }

    private fun bind(
        ps: PreparedStatement,
        base: Int,
        product: Product,
        now: Timestamp,
    ) {
        ps.setLong(base + 1, product.ownerId)
        ps.setString(base + 2, product.code.code)
        ps.setBigDecimal(base + 3, product.price.amount)
        ps.setString(base + 4, product.price.currencyCode.name)
        ps.setLong(base + 5, product.stock)
        ps.setString(base + 6, product.name)
        ps.setTimestamp(base + 7, now)
        ps.setTimestamp(base + 8, now)
    }

    /**
     * 방금 심은 id들로 상품코드를 역조회한다. 여기 나온 코드가 이번에 실제로 성공한 코드다.
     *
     * id는 우리가 심은 행의 것이므로 다른 요청의 행이 섞일 수 없다.
     */
    private fun findIdByCode(
        connection: Connection,
        insertedIds: List<Long>,
    ): Map<ProductCode, Long> {
        // 무관하게 정확하다. auto-increment gap 역산, affected rows 등 기각한 판별 방식은 설계 문서 9장에 있다.
        if (insertedIds.isEmpty()) return emptyMap()

        val sql =
            """
            SELECT id, product_code
            FROM products
            WHERE id IN (${List(insertedIds.size) { "?" }.joinToString(",")})
            """.trimIndent()

        return connection.prepareStatement(sql).use { statement ->
            insertedIds.forEachIndexed { i, id -> statement.setLong(i + 1, id) }
            statement.executeQuery().use { rows ->
                buildMap { while (rows.next()) put(ProductCode(rows.getString("product_code")), rows.getLong("id")) }
            }
        }
    }

    /** 청크의 모든 행을 "그대로 다시 보내면 되는" 실패로 확정한다. */
    private fun giveUp(
        chunk: IndexedChunk,
        results: MutableList<Result<Long>?>,
    ) {
        chunk.products.forEachIndexed { i, product ->
            results[chunk.offset + i] = Result.failure(ProductCreationException(product.code))
        }
    }

    /** 청크가 원래 입력의 어느 위치에서 시작했는지 같이 들고 다닌다. 결과를 입력 순서로 되돌리는 유일한 근거다. */
    private data class IndexedChunk(
        val offset: Int,
        val products: List<Product>,
    )

    companion object {
        private const val COLUMN_COUNT = 8

        private const val INSERT_PREFIX =
            "INSERT IGNORE INTO products " +
                "(owner_id, product_code, product_price, product_currency_code, stock, name, created_at, updated_at) " +
                "VALUES "

        private const val VALUES_TUPLE = "(?, ?, ?, ?, ?, ?, ?, ?)"
    }
}
