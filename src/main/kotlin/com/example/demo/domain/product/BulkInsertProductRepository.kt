package com.example.demo.domain.product

import com.example.demo.ui.dto.response.BulkRegisterProductResponse


interface BulkInsertProductRepository {
    fun saveAllAndReturnFailed(products: List<Product>): List<BulkRegisterProductResponse.FailedRegisterProduct>
}