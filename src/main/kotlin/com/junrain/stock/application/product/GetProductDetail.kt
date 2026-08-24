package com.junrain.stock.application.product

import com.junrain.stock.application.product.port.ProductReader
import com.junrain.stock.domain.product.exception.ProductNotFoundException
import com.querydsl.core.annotations.QueryProjection
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class GetProductDetail(
    private val productReader: ProductReader,
) {
    operator fun invoke(productId: Long): Result = productReader.findById(productId) ?: throw ProductNotFoundException()

    data class Result
        @QueryProjection
        constructor(
            val productId: Long,
            val name: String,
            val code: String,
            val price: BigDecimal,
            val stock: Long,
            val owner: Owner,
        ) {
            data class Owner
                @QueryProjection
                constructor(
                    val id: Long,
                    val name: String,
                )
        }
}
