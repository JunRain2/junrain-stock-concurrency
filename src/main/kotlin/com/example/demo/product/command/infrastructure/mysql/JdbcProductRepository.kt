package com.example.demo.product.command.infrastructure.mysql

import com.example.demo.product.command.domain.Product
import com.example.demo.product.exception.ProductCreationException
import com.example.demo.product.exception.ProductDuplicateCodeException
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.TransientDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Connection
import java.sql.Statement
import java.sql.Timestamp
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.milliseconds

private val logger = KotlinLogging.logger {}

@Component
class JdbcProductRepository(
    private val jdbcTemplate: JdbcTemplate,
    @param:Value("\${bulk-insert.chunk-size}") private val chunkSize: Int,
    @param:Value("\${bulk-insert.retry-milliseconds}") private val retryDelays: List<Long>
) {
    suspend fun bulkInsert(products: List<Product>): List<Result<Long>> = buildList {
        var retry = mutableListOf<Product>()
        products.chunked(chunkSize).forEach { chunked ->
            try {
                addAll(saveAndReturnedResult(chunked))
            } catch (e: TransientDataAccessException) {
                retry.addAll(chunked)
            }
        }

        for (delay in retryDelays) {
            if (retry.isEmpty()) break

            delay(delay.milliseconds)
            val tmp = mutableListOf<Product>()
            retry.chunked(chunkSize).forEach { chunked ->
                try {
                    addAll(saveAndReturnedResult(chunked))
                } catch (e: TransientDataAccessException) {
                    logger.error(e) { "데이터베이스에 일시적으로 접근 불가" }
                    tmp.addAll(chunked)
                }
            }
            retry = tmp
        }

        addAll(retry.map {
            Result.failure(
                ProductCreationException(
                    productCode = it.code.code,
                    exception = DataAccessResourceFailureException("재시도 실패")
                )
            )
        })
    }


    private fun saveAndReturnedResult(products: List<Product>): List<Result<Long>> {
        val sql = """
        INSERT IGNORE 
        INTO products (owner_id, product_code, product_price, product_currency_code, stock, name, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """.trimIndent()

        val now = Timestamp.valueOf(LocalDateTime.now())

        return jdbcTemplate.execute { conn: Connection ->
            conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
                products.forEach { product ->
                    ps.setLong(1, product.ownerId)
                    ps.setString(2, product.code.code)
                    ps.setBigDecimal(3, product.price.amount)
                    ps.setString(4, product.price.currencyCode.name)
                    ps.setLong(5, product.stock)
                    ps.setString(6, product.name)
                    ps.setTimestamp(7, now)
                    ps.setTimestamp(8, now)
                    ps.addBatch()
                }

                val rows = ps.executeBatch()
                val generatedKeys = ps.generatedKeys

                buildList {
                    products.forEachIndexed { idx, product ->
                        if (rows[idx] == 0) {
                            add(
                                Result.failure(
                                    ProductCreationException(
                                        productCode = product.code.code,
                                        exception = ProductDuplicateCodeException(product.code)
                                    )
                                )
                            )
                        } else if (generatedKeys.next()) {
                            add(Result.success(generatedKeys.getLong(1)))
                        } else {
                            add(
                                Result.failure(
                                    ProductCreationException(
                                        productCode = product.code.code,
                                        exception = IllegalStateException("Generated key를 가져올 수 없습니다.")
                                    )
                                )
                            )
                        }
                    }
                }
            }
        } ?: emptyList()
    }
}