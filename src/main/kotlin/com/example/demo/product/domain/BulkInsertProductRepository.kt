package com.example.demo.product.domain


interface BulkInsertProductRepository {
    fun saveAllAndReturnFailed(products: List<Product>): List<Product>
}