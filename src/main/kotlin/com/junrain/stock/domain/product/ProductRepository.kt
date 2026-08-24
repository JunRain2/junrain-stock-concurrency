package com.junrain.stock.domain.product

interface ProductRepository {
    fun save(product: Product): Product

    fun saveAll(products: List<Product>): List<Result<Product>>

    fun findById(productId: Long): Product

    fun findAllByIds(productIds: List<Long>): List<Product>
}
