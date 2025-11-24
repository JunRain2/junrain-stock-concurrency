package com.example.demo.product.command.domain


interface BulkInsertProductRepository {
    fun saveAllAndReturnFailed(products: List<Product>): List<Product>
}