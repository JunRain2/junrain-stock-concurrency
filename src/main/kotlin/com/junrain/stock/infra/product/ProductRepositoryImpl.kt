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

    override fun saveAll(products: List<Product>): List<Result<Long>> {
        val results = jdbcProductRepository.bulkInsert(products)

        seedStockAll(products, results)

        return results
    }

    /** 삽입에 성공한 행만 골라 Redis에 초기 재고를 심는다. bulkInsert가 입력과 같은 순서로 돌려준다는 전제 위에 있다. */
    private fun seedStockAll(
        products: List<Product>,
        results: List<Result<Long>>,
    ) {
        products
            .zip(results)
            .mapNotNull { (product, result) -> result.getOrNull()?.let { id -> id to product.stock } }
            .chunked(redisStockSeeder.maxSize)
            .forEach { chunk ->
                // 비동기 람다가 나중에 읽을 때 내용이 바뀌어 있다. 미리 Map으로 떠서 넘긴다.
                val quantityByProductId = chunk.toMap()
                asyncExecutor.execute { redisStockSeeder.seedAll(quantityByProductId) }
            }
    }

    override fun findById(productId: Long): Product = jpaProductRepository.findById(productId).orElseThrow { ProductNotFoundException() }

    override fun findAllByIds(productIds: List<Long>): List<Product> = jpaProductRepository.findAllById(productIds)
}
