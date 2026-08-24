package com.junrain.stock.ui.product

import com.junrain.stock.application.common.CursorPageResponse
import com.junrain.stock.application.product.GetProductDetail
import com.junrain.stock.application.product.GetProductPage
import com.junrain.stock.ui.common.ApiResponse
import com.junrain.stock.ui.product.dto.ProductDetailResponse
import com.junrain.stock.ui.product.dto.ProductPageRequest
import com.junrain.stock.ui.product.dto.ProductPageResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/products")
class ProductQueryController(
    private val getProductDetail: GetProductDetail,
    private val getProductPage: GetProductPage,
) {
    @GetMapping("/{productId}")
    fun getProduct(
        @PathVariable productId: Long,
    ): ApiResponse<ProductDetailResponse> {
        val result = getProductDetail(productId)
        val response = ProductDetailResponse.from(result)

        return ApiResponse.ok(response)
    }

    @GetMapping
    fun getProductPage(
        @Valid @ModelAttribute request: ProductPageRequest,
    ): ApiResponse<CursorPageResponse<ProductPageResponse>> {
        val query =
            GetProductPage.Query(
                ownerId = request.ownerId,
                productName = request.productName,
                productSorter = request.sorter.generateProductSorterRequest(request),
                size = request.size,
            )
        val pageResult = getProductPage(query)

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
