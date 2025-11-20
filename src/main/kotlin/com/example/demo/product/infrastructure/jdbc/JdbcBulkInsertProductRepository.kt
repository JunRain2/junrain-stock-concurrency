package com.example.demo.product.infrastructure.jdbc

import com.example.demo.product.domain.product.BulkInsertProductRepository
import com.example.demo.product.domain.product.Product
import com.example.demo.product.ui.dto.response.BulkRegisterProductResponse
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class JdbcBulkInsertProductRepository(
    private val jdbcTemplate: JdbcTemplate
) : BulkInsertProductRepository {
    override fun saveAllAndReturnFailed(products: List<Product>): List<BulkRegisterProductResponse.FailedRegisterProduct> {
        val sql = """
            INSERT IGNORE INTO product (name, product_code, product_price, product_currency_code, stock, owner_id)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()

        val affectedRows = jdbcTemplate.batchUpdate(sql, products, products.size) { ps, product ->
            ps.setString(1, product.name)
            ps.setString(2, product.code.code)
            ps.setBigDecimal(3, product.price.amount)
            ps.setString(4, product.price.currencyCode.name)
            ps.setLong(5, product.stock)
            ps.setLong(6, product.ownerId)
        }

        // batchUpdate returns Array<IntArray>, flatten to get individual results
        val results = affectedRows.firstOrNull() ?: intArrayOf()

        return products.filterIndexed { idx, _ ->
            idx < results.size && results[idx] != 1
        }.map {
            BulkRegisterProductResponse.FailedRegisterProduct(
                name = it.name,
                price = it.price.amount.toLong(),
                stock = it.stock,
                message = "중복된 상품 코드입니다. (코드: ${it.code.code})"
            )
        }
    }
}