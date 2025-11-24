package com.example.demo.product.query.ui

import com.example.demo.global.contract.ApiResponse
import com.example.demo.global.contract.CursorPageResponse
import com.example.demo.product.query.application.ProductQueryService
import com.example.demo.product.query.application.dto.ProductDetailResult
import com.example.demo.product.query.application.dto.ProductPageQuery
import com.example.demo.product.query.application.dto.ProductPageResult
import com.example.demo.product.query.ui.dto.request.ProductPageRequest
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
    ): ApiResponse<ProductDetailResult> {
        val response = productQueryService.getProductDetail(productId)

        return ApiResponse.ok(response)
    }

    @GetMapping
    fun getProductPage(
        @Valid @ModelAttribute request: ProductPageRequest
    ): ApiResponse<CursorPageResponse<ProductPageResult>> {
        val query = ProductPageQuery(
            ownerId = request.ownerId,
            productName = request.productName,
            productSorter = request.sorter.generateProductSorterRequest(request),
            size = request.size
        )
        val response = productQueryService.getProductPage(query)

        return ApiResponse.ok(response)
    }
}