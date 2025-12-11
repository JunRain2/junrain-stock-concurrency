package com.example.demo.product.command.domain

import com.example.demo.global.contract.BatchResult

interface ProductRepository {
    fun save(product: Product): Product

    fun bulkInsert(products: List<Product>): BatchResult<Product>

    fun findById(productId: Long): Product
}