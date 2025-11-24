package com.example.demo.product.query.ui.dto.request

import com.example.demo.product.query.application.dto.ProductSorter
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.format.annotation.DateTimeFormat
import java.math.BigDecimal
import java.time.LocalDateTime

data class ProductPageRequest(
    val lastProductId: Long?,
    @param:DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSSSSS")
    val createdAt: LocalDateTime?,
    val price: BigDecimal?,
    val ownerId: Long?,
    @field:NotBlank(message = "상품명은 필수입니다")
    @field:Size(max = 30, message = "상품명은 30자 미만이어야 합니다")
    val productName: String,
    val size: Int = 10,
    val sorter: ProductPageSorter = ProductPageSorter.LATEST,
)

enum class ProductPageSorter {
    LATEST {
        override fun generateProductSorterRequest(request: ProductPageRequest): ProductSorter {
            return ProductSorter.LatestSorter(
                lastProductId = request.lastProductId,
                createdAt = request.createdAt
            )
        }
    },
    SALE_PRICE_ASC {
        override fun generateProductSorterRequest(request: ProductPageRequest): ProductSorter {
            return ProductSorter.SalePriceAsc(
                lastProductId = request.lastProductId,
                price = request.price
            )
        }
    },
    SALE_PRICE_DESC {
        override fun generateProductSorterRequest(request: ProductPageRequest): ProductSorter {
            return ProductSorter.SalePriceDesc(
                lastProductId = request.lastProductId,
                price = request.price
            )
        }
    };

    abstract fun generateProductSorterRequest(request: ProductPageRequest): ProductSorter
}