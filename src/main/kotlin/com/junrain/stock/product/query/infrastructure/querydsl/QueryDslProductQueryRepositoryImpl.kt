package com.junrain.stock.product.query.infrastructure.querydsl

import com.junrain.stock.member.command.domain.QMember
import com.junrain.stock.product.command.domain.QProduct
import com.junrain.stock.product.query.application.ProductQueryRepository
import com.junrain.stock.product.query.application.dto.*
import com.querydsl.core.BooleanBuilder
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

@Repository
class QueryDslProductQueryRepositoryImpl(
    private val jpaQueryFactory: JPAQueryFactory
) : ProductQueryRepository {
    companion object {
        private const val HAS_NEXT_CHECK_SIZE = 1L
    }

    override fun findById(productId: Long): ProductDetailResult? {
        val qProduct = QProduct.product
        val qMember = QMember.member

        return jpaQueryFactory.select(
            QProductDetailResult(
                qProduct.id,
                qProduct.name,
                qProduct.code.code,
                qProduct.price.amount,
                qProduct.stock,
                QProductDetailResult_OwnerResponse(
                    qMember.id,
                    qMember.name
                )
            )
        ).from(qProduct).join(qMember).on(qProduct.ownerId.eq(qMember.id))
            .where(qProduct.id.eq(productId))
            .fetchOne()
    }

    override fun findProductPage(
        ownerId: Long?, size: Int, productName: String, sortRequest: ProductSorter
    ): List<ProductPageResult> {
        val qProduct = QProduct.product
        val qMember = QMember.member

        val conditions = BooleanBuilder()
        conditions.and(qProduct.name.startsWithIgnoreCase(productName))
        if (ownerId != null) {
            conditions.and(qProduct.ownerId.eq(ownerId))
        }

        val sorter = QueryDslProductSorter.of(sortRequest)
        sorter.buildCursorCondition(qProduct)?.let {
            conditions.and(it)
        }

        val sortOrders = buildList {
            addAll(sorter.buildSortOrder(qProduct))
            add(qProduct.id.desc())
        }

        return jpaQueryFactory.select(
            QProductPageResult(
                qProduct.id,
                qProduct.name,
                qProduct.price,
                QProductPageResult_OwnerResponse(
                    qMember.id, qMember.name
                ),
                qProduct.createdAt
            )
        ).from(qProduct).join(qMember).on(qProduct.ownerId.eq(qMember.id))
            .where(conditions)
            .orderBy(*sortOrders.toTypedArray())
            .limit(size.toLong() + HAS_NEXT_CHECK_SIZE)
            .fetch()
    }
}