package com.junrain.stock.infra.product.querydsl

import com.junrain.stock.application.product.GetProductDetail
import com.junrain.stock.application.product.GetProductPage
import com.junrain.stock.application.product.QGetProductDetail_Result
import com.junrain.stock.application.product.QGetProductDetail_Result_Owner
import com.junrain.stock.application.product.QGetProductPage_Result
import com.junrain.stock.application.product.QGetProductPage_Result_Owner
import com.junrain.stock.application.product.port.ProductReader
import com.junrain.stock.application.product.query.ProductSorter
import com.junrain.stock.domain.member.QMember
import com.junrain.stock.domain.product.QProduct
import com.querydsl.core.BooleanBuilder
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

@Repository
class QueryDslProductReader(
    private val jpaQueryFactory: JPAQueryFactory,
) : ProductReader {
    companion object {
        private const val HAS_NEXT_CHECK_SIZE = 1L
    }

    override fun findById(productId: Long): GetProductDetail.Result? {
        val qProduct = QProduct.product
        val qMember = QMember.member

        return jpaQueryFactory
            .select(
                QGetProductDetail_Result(
                    qProduct.id,
                    qProduct.name,
                    qProduct.code.code,
                    qProduct.price.amount,
                    qProduct.stock,
                    QGetProductDetail_Result_Owner(
                        qMember.id,
                        qMember.name,
                    ),
                ),
            ).from(qProduct)
            .join(qMember)
            .on(qProduct.ownerId.eq(qMember.id))
            .where(qProduct.id.eq(productId))
            .fetchOne()
    }

    override fun findProductPage(
        ownerId: Long?,
        size: Int,
        productName: String,
        sortRequest: ProductSorter,
    ): List<GetProductPage.Result> {
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

        val sortOrders =
            buildList {
                addAll(sorter.buildSortOrder(qProduct))
                add(qProduct.id.desc())
            }

        return jpaQueryFactory
            .select(
                QGetProductPage_Result(
                    qProduct.id,
                    qProduct.name,
                    qProduct.price,
                    QGetProductPage_Result_Owner(
                        qMember.id,
                        qMember.name,
                    ),
                    qProduct.createdAt,
                ),
            ).from(qProduct)
            .join(qMember)
            .on(qProduct.ownerId.eq(qMember.id))
            .where(conditions)
            .orderBy(*sortOrders.toTypedArray())
            .limit(size.toLong() + HAS_NEXT_CHECK_SIZE)
            .fetch()
    }
}
