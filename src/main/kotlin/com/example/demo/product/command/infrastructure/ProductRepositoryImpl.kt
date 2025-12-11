package com.example.demo.product.command.infrastructure

import com.example.demo.global.contract.BatchResult
import com.example.demo.product.command.domain.Product
import com.example.demo.product.command.domain.ProductRepository
import com.example.demo.product.command.infrastructure.mysql.JdbcProductRepository
import com.example.demo.product.command.infrastructure.mysql.JpaProductRepository
import com.example.demo.product.command.infrastructure.redis.RedisStockRepository
import com.example.demo.product.exception.ProductDuplicateCodeException
import com.example.demo.product.exception.ProductNotFoundException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository

private val logger = KotlinLogging.logger { }

@Repository
class ProductRepositoryImpl(
    private val jpaProductRepository: JpaProductRepository,
    private val jdbcProductRepository: JdbcProductRepository,
    private val redisStockRepository: RedisStockRepository,
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
        // TODO 레디스에 값을 추가하는 로직 필요
        return jdbcProductRepository.bulkInsert(products)
    }

    override fun findById(productId: Long): Product {
        return jpaProductRepository.findById(productId).orElseThrow { ProductNotFoundException() }
    }
}