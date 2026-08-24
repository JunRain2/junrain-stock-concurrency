package com.junrain.stock.ui.product.dto

import com.junrain.stock.application.product.ReserveProducts
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive

class ProductReserveDto {
    class Request {
        data class Reserve(
            @field:Valid
            @field:NotEmpty(message = "예약할 상품이 없습니다")
            val items: List<Item>,
        ) {
            data class Item(
                @field:Positive(message = "상품 ID가 올바르지 않습니다")
                val productId: Long,
                @field:Positive(message = "예약 수량은 1 이상이어야 합니다")
                val quantity: Long,
            )
        }
    }

    class Response {
        data class Reserve(
            val trxId: String,
        ) {
            companion object {
                fun from(result: ReserveProducts.Result): Reserve = Reserve(trxId = result.trxId)
            }
        }
    }
}
