package com.junrain.stock.application.product

import com.junrain.stock.application.common.CursorPageResponse
import com.junrain.stock.application.product.query.ProductSorter
import com.junrain.stock.domain.common.Money
import com.querydsl.core.annotations.QueryProjection
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * cursor 방식으로 구현 -> 쿠팡의 검색을 벤치마킹
 * - sort: 최신순[default], 높은 가격 순, 낮은 가격 순
 * - 이름 검색 가능 -> 전위 검색
 * - ownerId를 통한 조회
 */
@Service
class GetProductPage(
    private val productReader: ProductReader,
) {
    operator fun invoke(query: Query): CursorPageResponse<Result> {
        val products =
            productReader.findProductPage(
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
            data = data,
            size = data.size,
            hasNext = hasNext,
            nextCursor = nextCursor,
        )
    }

    data class Query(
        val ownerId: Long?,
        val productName: String,
        val productSorter: ProductSorter,
        val size: Int = 10,
    ) {
        init {
            require(productName.isNotBlank()) { "상품명은 반드시 입력해야 합니다." }
            require(productName.length < 20) { "상품명은 20자 미만이어야 합니다." }
        }
    }

    data class Result
        @QueryProjection
        constructor(
            val productId: Long,
            val name: String,
            val price: Money,
            val owner: Owner,
            val createdAt: LocalDateTime,
        ) {
            data class Owner
                @QueryProjection
                constructor(
                    val ownerId: Long,
                    val name: String,
                )
        }
}
