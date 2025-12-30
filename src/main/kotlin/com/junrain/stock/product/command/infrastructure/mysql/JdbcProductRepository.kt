package com.junrain.stock.product.command.infrastructure.mysql

import com.junrain.stock.product.command.domain.Product
import com.junrain.stock.product.command.domain.vo.ProductCode
import com.junrain.stock.product.exception.ProductCreationException
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataAccessException
import org.springframework.dao.TransientDataAccessException
import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
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
    suspend fun bulkInsert(
        products: List<Product>, createdAt: LocalDateTime = LocalDateTime.now()
    ): List<Result<ProductCode>> = buildList {
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

            delay(delay.milliseconds)
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

        addAll(retry.map {
            Result.failure(
                ProductCreationException(code = it.code)
            )
        })
    }


    private fun saveAndReturnedResult(
        products: List<Product>, createdAt: LocalDateTime
    ): List<Result<ProductCode>> {
        val sql = """
            INSERT 
            INTO products (owner_id, product_code, product_price, product_currency_code, stock, name, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE product_code = product_code
            """.trimIndent()

        val now = Timestamp.valueOf(createdAt)

        jdbcTemplate.batchUpdate(
            sql, object : BatchPreparedStatementSetter {
                override fun setValues(ps: java.sql.PreparedStatement, i: Int) {
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
            })

        return products.indices.map { idx ->
            Result.success(products[idx].code)
        }
    }
}