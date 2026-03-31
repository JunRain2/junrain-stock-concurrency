package com.junrain.stock.product.controller

import com.junrain.stock.common.dto.ApiResponse
import com.junrain.stock.common.dto.CursorPageResponse
import com.junrain.stock.product.application.ProductQueryService
import com.junrain.stock.product.application.query.ProductPageQuery
import com.junrain.stock.product.controller.dto.ProductDetailResponse
import com.junrain.stock.product.controller.dto.ProductPageRequest
import com.junrain.stock.product.controller.dto.ProductPageResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/products")
class ProductQueryController(
    private val productQueryService: ProductQueryService,
) {
    @GetMapping("/{productId}")
    fun getProduct(
        @PathVariable productId: Long,
    ): ApiResponse<ProductDetailResponse> {
        val result = productQueryService.getProductDetail(productId)
        val response = ProductDetailResponse.from(result)

        return ApiResponse.ok(response)
    }

    @GetMapping
    fun getProductPage(
        @Valid @ModelAttribute request: ProductPageRequest,
    ): ApiResponse<CursorPageResponse<ProductPageResponse>> {
        val query =
            ProductPageQuery(
                ownerId = request.ownerId,
                productName = request.productName,
                productSorter = request.sorter.generateProductSorterRequest(request),
                size = request.size,
            )
        val pageResult = productQueryService.getProductPage(query)

        val response =
            CursorPageResponse(
                data = pageResult.data.map { ProductPageResponse.from(it) },
                size = pageResult.size,
                hasNext = pageResult.hasNext,
                nextCursor = pageResult.nextCursor,
            )

        return ApiResponse.ok(response)
    }
}
