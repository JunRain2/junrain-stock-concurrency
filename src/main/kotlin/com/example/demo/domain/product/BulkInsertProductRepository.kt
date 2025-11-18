package com.example.demo.domain.product

import com.example.demo.ui.dto.response.RegisterProductResponse


interface BulkInsertProductRepository {
    fun saveAllAndReturnFailed(products: List<Product>): List<RegisterProductResponse.FailedRegisterProduct>
}