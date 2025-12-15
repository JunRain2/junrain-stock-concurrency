package com.example.demo.product.command.ui.dto

import com.example.demo.product.command.application.dto.ProductPurchaseDto as AppProductPurchaseDto
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty

class ProductPurchaseDto {
    class Request {
        data class BulkPurchase(
            @field:NotEmpty(message = "구매할 상품 목록은 비어있을 수 없습니다.")
            @field:Valid
            val items: List<PurchaseItem>
        ) {
            data class PurchaseItem(
                @field:Min(1, message = "상품 ID는 1 이상이어야 합니다.")
                val productId: Long,

                @field:Min(1, message = "구매 수량은 1개 이상이어야 합니다.")
                val quantity: Long
            )
        }
    }

    class Response {
        data class BulkPurchase(
            val purchasedProducts: List<PurchasedProduct>,
            val totalCount: Int
        ) {
            data class PurchasedProduct(
                val productId: Long
            )

            companion object {
                fun from(results: List<AppProductPurchaseDto.Result.Purchase>): BulkPurchase {
                    return BulkPurchase(
                        purchasedProducts = results.map { PurchasedProduct(it.productId) },
                        totalCount = results.size
                    )
                }
            }
        }
    }
}
