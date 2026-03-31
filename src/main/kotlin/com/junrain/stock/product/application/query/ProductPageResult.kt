package com.junrain.stock.product.application.query

import com.junrain.stock.common.domain.Money
import com.querydsl.core.annotations.QueryProjection
import java.time.LocalDateTime

data class ProductPageResult
    @QueryProjection
    constructor(
        val productId: Long,
        val name: String,
        val price: Money,
        val owner: OwnerResponse,
        val createdAt: LocalDateTime,
    ) {
        data class OwnerResponse
            @QueryProjection
            constructor(
                val ownerId: Long,
                val name: String,
            )
    }
