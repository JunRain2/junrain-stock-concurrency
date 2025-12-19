package com.example.demo.product.command.infrastructure

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
import kotlinx.coroutines.runBlocking
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

    override fun saveAll(products: List<Product>): List<Result<Product>> {
        val productResults = runBlocking {
            val results = jdbcProductRepository.bulkInsert(products)
            val (ids, exceptions) = results.partition { it.isSuccess }

            buildList<Result<Product>> {
                ids.mapNotNull { it.getOrNull() }.chunked(1000) { chunk ->
                    jpaProductRepository.findByIdIn(chunk).forEach {
                        add(Result.success(it))
                    }
                }
                exceptions.forEach { e ->
                    e.exceptionOrNull()?.let { exception ->
                        add(Result.failure<Product>(exception))
                    }
                }
            }
        }

        insertRedis(productResults)

        return productResults
    }

    private fun insertRedis(productResults: List<Result<Product>>) {
        productResults.mapNotNull { it.getOrNull() }
            .chunked(redisStockRepository.maxSize) { chunk ->
                applicationScope.launch {
                    val stockChanges = chunk.map {
                        StockChange(
                            productId = it.id, quantity = it.stock
                        )
                    }
                    val requestKey = UUID.randomUUID().toString()
                    redisStockRepository.increaseStock(requestKey, *stockChanges.toTypedArray())
                }
            }
    }

    override fun findById(productId: Long): Product {
        return jpaProductRepository.findById(productId).orElseThrow { ProductNotFoundException() }
    }
}