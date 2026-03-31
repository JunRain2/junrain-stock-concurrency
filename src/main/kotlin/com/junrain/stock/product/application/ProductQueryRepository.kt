package com.junrain.stock.product.application

import com.junrain.stock.product.application.query.ProductDetailResult
import com.junrain.stock.product.application.query.ProductPageResult
import com.junrain.stock.product.application.query.ProductSorter

interface ProductQueryRepository {
    fun findById(productId: Long): ProductDetailResult?

    fun findProductPage(
        ownerId: Long?,
        size: Int,
        productName: String,
        sortRequest: ProductSorter,
    ): List<ProductPageResult>
}
