package com.example.demo.product.domain.product

import com.example.demo.product.ui.dto.response.BulkRegisterProductResponse


interface BulkInsertProductRepository {
    fun saveAllAndReturnFailed(products: List<Product>): List<BulkRegisterProductResponse.FailedRegisterProduct>
}