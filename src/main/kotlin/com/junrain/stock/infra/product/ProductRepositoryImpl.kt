package com.junrain.stock.infra.product

import com.junrain.stock.domain.product.Product
import com.junrain.stock.domain.product.ProductRepository
import com.junrain.stock.domain.product.exception.ProductDuplicateCodeException
import com.junrain.stock.domain.product.exception.ProductNotFoundException
import com.junrain.stock.infra.product.mysql.JdbcProductRepository
import com.junrain.stock.infra.product.mysql.JdbcStockItemRepository
import com.junrain.stock.infra.product.mysql.JpaProductRepository
import com.junrain.stock.infra.product.redis.RedisStockRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.Executor

private val logger = KotlinLogging.logger { }

@Repository
class ProductRepositoryImpl(
    private val jpaProductRepository: JpaProductRepository,
    private val jdbcProductRepository: JdbcProductRepository,
    private val jdbcStockItemRepository: JdbcStockItemRepository,
    private val redisStockRepository: RedisStockRepository,
    private val asyncExecutor: Executor,
    @param:Value(StockStrategy.PLACEHOLDER) private val stockStrategy: StockStrategy,
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
     * 활성 전략이 실제로 읽는 저장소에만 초기 재고를 심는다.
     *
     * 세 곳을 전부 채우면 쓰지 않는 저장소까지 같이 커지고(stock_items는 재고 1개당 1행이다),
     * 예약이 어느 저장소를 읽고 있는지도 흐려진다. products.stock은 위 INSERT가 이미 넣었으므로
     * [StockStrategy.SINGLE_UPDATE]는 여기서 할 일이 없다.
     */
    private fun seedStock(
        productId: Long,
        quantity: Long,
    ) = when (stockStrategy) {
        StockStrategy.REDIS -> redisStockRepository.setStockIfAbsent(productId = productId, quantity = quantity)
        StockStrategy.SKIP_LOCKED -> jdbcStockItemRepository.insertAvailable(productId = productId, quantity = quantity)
        StockStrategy.SINGLE_UPDATE -> Unit
    }

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

        if (stockStrategy == StockStrategy.REDIS) insertRedis(productResults)

        return productResults
    }

    /**
     * ponytail: 대량 등록은 Redis만 채운다. SKIP_LOCKED에서 대량 등록한 상품은 stock_items가 비어
     * 예약이 전부 재고 없음으로 실패한다 - 대량 경로를 그 전략에서 쓸 일이 생기면 그때 채운다.
     */
    private fun insertRedis(productResults: List<Result<Product>>) {
        productResults
            .mapNotNull { it.getOrNull() }
            .chunked(redisStockRepository.maxSize) { chunk ->
                asyncExecutor.execute {
                    val stockChanges =
                        chunk.map {
                            StockDelta(
                                productId = it.id,
                                quantity = it.stock,
                            )
                        }
                    val requestKey = UUID.randomUUID().toString()
                    redisStockRepository.increaseStock(requestKey, *stockChanges.toTypedArray())
                }
            }
    }

    override fun findById(productId: Long): Product = jpaProductRepository.findById(productId).orElseThrow { ProductNotFoundException() }

    override fun findAllByIds(productIds: List<Long>): List<Product> = jpaProductRepository.findAllById(productIds)
}
