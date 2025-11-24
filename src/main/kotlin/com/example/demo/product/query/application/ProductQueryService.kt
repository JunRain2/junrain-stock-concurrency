package com.example.demo.product.query.application

import com.example.demo.global.contract.CursorPageResponse
import com.example.demo.product.exception.NotFoundProductException
import com.example.demo.product.query.application.dto.ProductDetailResult
import com.example.demo.product.query.application.dto.ProductPageQuery
import com.example.demo.product.query.application.dto.ProductPageResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ProductQueryService(
    private val productQueryRepository: ProductQueryRepository,
) {
    private val logger = LoggerFactory.getLogger(ProductQueryService::class.java)

    fun getProductDetail(productId: Long): ProductDetailResult {
        return productQueryRepository.findById(productId) ?: throw NotFoundProductException()
    }

    /**
     * cursor 방식으로 구현 -> 쿠팡의 검색을 벤치마킹
     * - sort: 최신순[default], 높은 가격 순, 낮은 가격 순
     * - 이름 검색 가능 -> 전위 검색
     * - ownerId를 통한 조회
     */
    fun getProductPage(query: ProductPageQuery): CursorPageResponse<ProductPageResult> {
        val products = productQueryRepository.findProductPage(
            ownerId = query.ownerId,
            size = query.size,
            productName = query.productName,
            sortRequest = query.productSorter,
        )

        val hasNext = products.size > query.size
        val data = products.take(query.size)
        val nextCursor =
            data.lastOrNull()?.let { query.productSorter.getNextCursor(it) }.orEmpty()

        return CursorPageResponse(
            data = data, size = data.size, hasNext = hasNext, nextCursor = nextCursor
        )
    }
}