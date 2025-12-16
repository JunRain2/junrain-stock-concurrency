package com.example.demo.product.command.infrastructure.mysql

import com.example.demo.global.contract.BatchResult
import com.example.demo.global.contract.FailedItem
import com.example.demo.product.command.domain.Product
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.TransientDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.lang.Thread.sleep
import java.sql.Timestamp
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

@Component
class JdbcProductRepository(
    private val jdbcTemplate: JdbcTemplate,
    @param:Value("\${bulk-insert.chunk-size}") private val chunkSize: Int,
    @param:Value("\${bulk-insert.retry-milliseconds}") private val retryDelays: List<Long> // 백오프 지수를 구현해보기
) {
    fun bulkInsert(products: List<Product>): BatchResult<Product> {
        val success = mutableListOf<Product>()
        val failure = mutableListOf<FailedItem<Product>>()

        var retry = mutableListOf<Product>()

        products.chunked(chunkSize).forEach { data ->
            try {
                saveAndReturnedResult(data).forEachIndexed { idx, result ->
                    result.onSuccess {
                        success.add(it)
                    }.onFailure {
                        failure.add(
                            FailedItem(
                                item = data[idx],
                                reason = it
                            )
                        )
                    }
                }
            } catch (e: TransientDataAccessException) {
                logger.warn { "데이터베이스와 연동하던 중 일시적인 장애가 발생했습니다. message : ${e.message}" }
                retry.addAll(data)
            }
        }

        for (delay in retryDelays) {
            if (retry.isEmpty()) break

            sleep(delay)

            val tmp = mutableListOf<Product>()
            retry.chunked(chunkSize).forEach { data ->
                try {
                    saveAndReturnedResult(data).forEachIndexed { idx, result ->
                        result.onSuccess {
                            success.add(it)
                        }.onFailure {
                            failure.add(
                                FailedItem(
                                    item = data[idx],
                                    reason = it
                                )
                            )
                        }
                    }
                } catch (e: TransientDataAccessException) {
                    tmp.addAll(data)
                }
            }

            retry = tmp
        }

        if (retry.isNotEmpty()) {
            logger.warn { "데이터베이스에 연결할 수 없습니다." }
            failure.addAll(retry.map {
                FailedItem(
                    item = it,
                    reason = DataAccessResourceFailureException("데이터베이스에 장애가 발생했습니다.")
                )
            })
        }

        return BatchResult(
            succeeded = success,
            failed = failure
        )
    }

    private fun saveAndReturnedResult(products: List<Product>): List<Result<Product>> {
        val sql = """
            INSERT IGNORE 
            INTO products (owner_id, product_code, product_price, product_currency_code, stock, name, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        val now = Timestamp.valueOf(LocalDateTime.now())
        val affectedRows = jdbcTemplate.batchUpdate(sql, products, products.size) { ps, product ->
            ps.setLong(1, product.ownerId)
            ps.setString(2, product.code.code)
            ps.setBigDecimal(3, product.price.amount)
            ps.setString(4, product.price.currencyCode.name)
            ps.setLong(5, product.stock)
            ps.setString(6, product.name)
            ps.setTimestamp(7, now)
            ps.setTimestamp(8, now)
        }

        val results = affectedRows.firstOrNull() ?: return emptyList()

        return products.mapIndexed { idx, product ->
            if (idx < results.size && results[idx] == 1) {
                Result.success(product)
            } else {
                logger.warn { "$product 삽입 중 데이터 유효성 문제가 발생했습니다." }
                Result.failure(DataIntegrityViolationException("데이터 유효성 문제가 발생"))
            }
        }
    }
}