package com.example.demo.product.query.infrastructure.querydsl

import com.example.demo.product.command.domain.QProduct
import com.example.demo.product.query.application.dto.ProductSorter
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.Predicate
import java.math.BigDecimal
import java.time.LocalDateTime

sealed class QueryDslProductSorter() {
    protected abstract val lastProductId: Long?

    abstract fun buildSortOrder(qProduct: QProduct): List<OrderSpecifier<*>>
    protected abstract fun buildCondition(qProduct: QProduct): Predicate
    protected abstract fun hasCursor(): Boolean

    fun buildCursorCondition(qProduct: QProduct): Predicate? {
        if (!hasCursor()) return null
        return buildCondition(qProduct)
    }

    companion object {
        fun of(request: ProductSorter): QueryDslProductSorter {
            return when (request) {
                is ProductSorter.LatestSorter -> {
                    LatestSorter(
                        lastProductId = request.lastProductId,
                        createdAt = request.createdAt
                    )
                }

                is ProductSorter.SalePriceAsc -> {
                    SalePriceAsc(
                        lastProductId = request.lastProductId,
                        price = request.price
                    )
                }

                is ProductSorter.SalePriceDesc -> {
                    SalePriceDesc(
                        lastProductId = request.lastProductId,
                        price = request.price
                    )
                }
            }
        }
    }

    class LatestSorter(
        override val lastProductId: Long?,
        val createdAt: LocalDateTime?
    ) : QueryDslProductSorter() {

        override fun buildSortOrder(qProduct: QProduct): List<OrderSpecifier<*>> =
            listOf(qProduct.createdAt.desc())

        override fun hasCursor(): Boolean {
            return lastProductId != null && createdAt != null
        }

        override fun buildCondition(qProduct: QProduct): Predicate {
            return qProduct.createdAt.lt(this.createdAt).or(
                qProduct.createdAt.eq(this.createdAt)
                    .and(qProduct.id.lt(this.lastProductId))
            )
        }
    }

    class SalePriceAsc(
        override val lastProductId: Long?,
        val price: BigDecimal?
    ) : QueryDslProductSorter() {
        override fun buildSortOrder(qProduct: QProduct): List<OrderSpecifier<*>> =
            listOf(qProduct.price.amount.asc())

        override fun hasCursor(): Boolean {
            return lastProductId != null && price != null
        }

        override fun buildCondition(qProduct: QProduct): Predicate {
            return qProduct.price.amount.gt(this.price).or(
                (qProduct.price.amount.eq(this.price))
                    .and(qProduct.id.lt(this.lastProductId))
            )
        }
    }

    class SalePriceDesc(
        override val lastProductId: Long?,
        val price: BigDecimal?
    ) : QueryDslProductSorter() {
        override fun buildSortOrder(qProduct: QProduct): List<OrderSpecifier<*>> =
            listOf(qProduct.price.amount.desc())

        override fun hasCursor(): Boolean {
            return lastProductId != null && price != null
        }

        override fun buildCondition(qProduct: QProduct): Predicate {
            return qProduct.price.amount.lt(this.price).or(
                qProduct.price.amount.eq(this.price)
                    .and(qProduct.id.lt(this.lastProductId))
            )
        }
    }
}
