package com.example.demo.product.command.infrastructure

import com.example.demo.global.contract.BatchResult
import com.example.demo.product.command.domain.Product
import com.example.demo.product.command.domain.ProductRepository
import com.example.demo.product.command.domain.StockChange
import com.example.demo.product.command.infrastructure.mysql.JdbcProductRepository
import com.example.demo.product.command.infrastructure.mysql.JpaProductRepository
import com.example.demo.product.command.infrastructure.redis.RedisStockRepository
import com.example.demo.product.exception.ProductDuplicateCodeException
import com.example.demo.product.exception.ProductNotFoundException
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository
import java.util.*

private val logger = KotlinLogging.logger { }

@Repository
class ProductRepositoryImpl(
    private val jpaProductRepository: JpaProductRepository,
    private val jdbcProductRepository: JdbcProductRepository,
    private val redisStockRepository: RedisStockRepository,
    private val applicationScope: CoroutineScope
) : ProductRepository {
    override fun save(product: Product): Product {
        val product = try {
            jpaProductRepository.save(product)
        } catch (e: DataIntegrityViolationException) {
            logger.error { "error message : ${e.message}" }
            throw ProductDuplicateCodeException(product.code)
        }

        redisStockRepository.setStockIfAbsent(productId = product.id, quantity = product.stock)

        return product
    }

    override fun bulkInsert(products: List<Product>): BatchResult<Product> {
        val result = jdbcProductRepository.bulkInsert(products)

        applicationScope.launch {
            result.succeeded.chunked(redisStockRepository.maxSize).forEach { chunked ->
                val changeProducts = chunked.map { StockChange(it.id, it.stock) }
                val requestKey = UUID.randomUUID().toString()
                redisStockRepository.increaseStock(requestKey, *changeProducts.toTypedArray())
            }
        }

        return result
    }

    override fun findById(productId: Long): Product {
        return jpaProductRepository.findById(productId).orElseThrow { ProductNotFoundException() }
    }
}