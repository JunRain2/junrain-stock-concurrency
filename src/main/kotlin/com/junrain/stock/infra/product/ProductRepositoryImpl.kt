package com.junrain.stock.infra.product

import com.junrain.stock.domain.product.Product
import com.junrain.stock.domain.product.ProductRepository
import com.junrain.stock.domain.product.exception.ProductDuplicateCodeException
import com.junrain.stock.domain.product.exception.ProductNotFoundException
import com.junrain.stock.infra.product.mysql.JdbcProductRepository
import com.junrain.stock.infra.product.mysql.JpaProductRepository
import com.junrain.stock.infra.product.redis.RedisStockSeeder
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.concurrent.Executor

private val logger = KotlinLogging.logger { }

@Repository
class ProductRepositoryImpl(
    private val jpaProductRepository: JpaProductRepository,
    private val jdbcProductRepository: JdbcProductRepository,
    private val redisStockSeeder: RedisStockSeeder,
    private val asyncExecutor: Executor,
) : ProductRepository {
    override fun save(product: Product): Product {
        val product =
            try {
                jpaProductRepository.save(product)
            } catch (e: DataIntegrityViolationException) {
                logger.error { "error message : ${e.message}" }
                throw ProductDuplicateCodeException(product.code)
            }

        seedStock(productId = product.id, quantity = product.stock)

        return product
    }

    /**
     * 예약이 읽는 저장소는 Redis뿐이므로 초기 재고도 거기에만 심는다.
     *
     * products.stock은 위 INSERT가 이미 넣었지만 예약 경로가 읽지 않는다 - 등록 시점의 원본일 뿐이다.
     */
    private fun seedStock(
        productId: Long,
        quantity: Long,
    ) = redisStockSeeder.seed(productId = productId, quantity = quantity)

    override fun saveAll(products: List<Product>): List<Result<Product>> {
        val createdAt = LocalDateTime.now()
        val results = jdbcProductRepository.bulkInsert(products, createdAt)
        val (ids, exceptions) = results.partition { it.isSuccess }

        val productResults =
            buildList<Result<Product>> {
                ids.mapNotNull { it.getOrNull() }.chunked(1000).forEach { chunk ->
                    val foundProducts =
                        jpaProductRepository
                            .findByCreatedAtAndCodeIn(createdAt, chunk)
                    val foundCodes = foundProducts.map { it.code }.toSet()

                    foundProducts.forEach { product ->
                        add(Result.success(product))
                    }

                    chunk.filterNot { code -> foundCodes.contains(code) }.forEach { missingCode ->
                        add(Result.failure(ProductDuplicateCodeException(missingCode)))
                    }
                }
                exceptions.forEach { e ->
                    e.exceptionOrNull()?.let { exception ->
                        add(Result.failure(exception))
                    }
                }
            }

        insertRedis(productResults)

        return productResults
    }

    private fun insertRedis(productResults: List<Result<Product>>) {
        productResults
            .mapNotNull { it.getOrNull() }
            .chunked(redisStockSeeder.maxSize) { chunk ->
                asyncExecutor.execute {
                    redisStockSeeder.seedAll(chunk.associate { it.id to it.stock })
                }
            }
    }

    override fun findById(productId: Long): Product = jpaProductRepository.findById(productId).orElseThrow { ProductNotFoundException() }

    override fun findAllByIds(productIds: List<Long>): List<Product> = jpaProductRepository.findAllById(productIds)
}
