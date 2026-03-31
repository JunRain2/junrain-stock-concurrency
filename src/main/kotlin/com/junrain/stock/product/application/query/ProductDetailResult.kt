package com.junrain.stock.product.application.query

import com.querydsl.core.annotations.QueryProjection
import java.math.BigDecimal

data class ProductDetailResult
    @QueryProjection
    constructor(
        val productId: Long,
        val name: String,
        val code: String,
        val price: BigDecimal,
        val stock: Long,
        val owner: OwnerResponse,
    ) {
        data class OwnerResponse
            @QueryProjection
            constructor(
                val id: Long,
                val name: String,
            )
    }
