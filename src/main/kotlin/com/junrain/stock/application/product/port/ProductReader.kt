package com.junrain.stock.application.product

import com.junrain.stock.application.product.query.ProductSorter

interface ProductReader {
    fun findById(productId: Long): GetProductDetail.Result?

    fun findProductPage(
        ownerId: Long?,
        size: Int,
        productName: String,
        sortRequest: ProductSorter,
    ): List<GetProductPage.Result>
}
