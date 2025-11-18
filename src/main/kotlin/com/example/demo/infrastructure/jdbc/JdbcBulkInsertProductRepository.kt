package com.example.demo.infrastructure.jdbc

import com.example.demo.domain.product.BulkInsertProductRepository
import com.example.demo.domain.product.Product
import com.example.demo.ui.dto.response.RegisterProductResponse
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class JdbcBulkInsertProductRepository(
    private val jdbcTemplate: JdbcTemplate
) : BulkInsertProductRepository {
    override fun saveAllAndReturnFailed(products: List<Product>): List<RegisterProductResponse.FailedRegisterProduct> {
        val sql = """
            INSERT IGNORE INTO product (name, code, amount, currency_code, stock)
            VALUES (?, ?, ?, ?, ?)
        """.trimIndent()

        val affectedRows = jdbcTemplate.batchUpdate(sql, products, products.size) { ps, product ->
            ps.setString(1, product.name)
            ps.setString(2, product.code.code)
            ps.setBigDecimal(3, product.price.amount)
            ps.setString(4, product.price.currencyCode.name)
            ps.setLong(5, product.stock)
        }

        // batchUpdate returns Array<IntArray>, flatten to get individual results
        val results = affectedRows.firstOrNull() ?: intArrayOf()

        return products.filterIndexed { idx, _ ->
            idx < results.size && results[idx] != 1
        }.map {
            RegisterProductResponse.FailedRegisterProduct(
                name = it.name,
                price = it.price.amount.toLong(),
                stock = it.stock,
                message = "데이터를 삽입하던 도중 문제가 발생했습니다."
            )
        }
    }
}