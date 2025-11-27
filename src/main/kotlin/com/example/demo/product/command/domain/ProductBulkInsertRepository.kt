package com.example.demo.product.command.domain

import com.example.demo.global.contract.BatchResult


interface ProductBulkInsertRepository {
    fun bulkInsert(products: List<Product>): BatchResult<Product>
}