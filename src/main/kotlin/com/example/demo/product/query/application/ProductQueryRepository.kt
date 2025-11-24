package com.example.demo.product.query.application

import com.example.demo.product.query.application.dto.ProductDetailResult
import com.example.demo.product.query.application.dto.ProductPageResult
import com.example.demo.product.query.application.dto.ProductSorter

interface ProductQueryRepository {
    fun findById(productId: Long): ProductDetailResult?

    fun findProductPage(
        ownerId: Long?, size: Int, productName: String, sortRequest: ProductSorter
    ): List<ProductPageResult>
}