package com.example.demo.product.command.infrastructure.jdbc

import com.example.demo.product.command.domain.BulkInsertProductRepository
import com.example.demo.product.command.domain.Product
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.LocalDateTime

@Repository
class JdbcBulkInsertProductRepository(
    private val jdbcTemplate: JdbcTemplate
) : BulkInsertProductRepository {
    override fun saveAllAndReturnFailed(products: List<Product>): List<Product> {
        val sql = """
            INSERT IGNORE INTO products (owner_id, product_code, product_price, product_currency_code, stock, name, created_at, updated_at)
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

        return affectedRows.firstOrNull()?.let { results ->
            products.filterIndexed { idx, _ -> idx < results.size && results[idx] != 1 }
        } ?: emptyList()
    }
}