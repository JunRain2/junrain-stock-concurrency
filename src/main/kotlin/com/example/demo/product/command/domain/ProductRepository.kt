package com.example.demo.product.command.domain


interface ProductRepository {
    fun save(product: Product): Product

    fun saveAll(products: List<Product>): List<Result<Product>>

    fun findById(productId: Long): Product
}