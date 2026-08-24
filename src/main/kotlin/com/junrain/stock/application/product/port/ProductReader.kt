package com.junrain.stock.application.product.port

import com.junrain.stock.application.product.GetProductDetail
import com.junrain.stock.application.product.GetProductPage
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
