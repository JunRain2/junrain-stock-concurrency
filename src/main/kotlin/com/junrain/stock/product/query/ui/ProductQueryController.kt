package com.junrain.stock.product.query.ui

import com.junrain.stock.contract.dto.ApiResponse
import com.junrain.stock.contract.dto.CursorPageResponse
import com.junrain.stock.product.query.application.ProductQueryService
import com.junrain.stock.product.query.application.dto.ProductPageQuery
import com.junrain.stock.product.query.ui.dto.request.ProductPageRequest
import com.junrain.stock.product.query.ui.dto.response.ProductDetailResponse
import com.junrain.stock.product.query.ui.dto.response.ProductPageResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/products")
class ProductQueryController(
    private val productQueryService: ProductQueryService
) {
    @GetMapping("/{productId}")
    fun getProduct(
        @PathVariable productId: Long
    ): ApiResponse<ProductDetailResponse> {
        val result = productQueryService.getProductDetail(productId)
        val response = ProductDetailResponse.from(result)

        return ApiResponse.ok(response)
    }

    @GetMapping
    fun getProductPage(
        @Valid @ModelAttribute request: ProductPageRequest
    ): ApiResponse<CursorPageResponse<ProductPageResponse>> {
        val query = ProductPageQuery(
            ownerId = request.ownerId,
            productName = request.productName,
            productSorter = request.sorter.generateProductSorterRequest(request),
            size = request.size
        )
        val pageResult = productQueryService.getProductPage(query)

        val response = CursorPageResponse(
            data = pageResult.data.map { ProductPageResponse.from(it) },
            size = pageResult.size,
            hasNext = pageResult.hasNext,
            nextCursor = pageResult.nextCursor
        )

        return ApiResponse.ok(response)
    }
}