package com.junrain.stock.domain.product

import com.junrain.stock.domain.common.BaseEntity
import com.junrain.stock.domain.common.Money
import com.junrain.stock.domain.product.vo.ProductCode
import jakarta.persistence.AttributeOverride
import jakarta.persistence.AttributeOverrides
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "products",
    // 상품 목록 조회가 owner_id로 거른다(QueryDslProductReader.findProductPage).
    // 없으면 products 풀스캔이라 등록이 쌓일수록 조회가 같이 느려진다.
    indexes = [Index(name = "idx_products_owner_id", columnList = "owner_id")],
)
class Product(
    @Column(name = "owner_id") val ownerId: Long,
    @Embedded @AttributeOverride(
        name = "code",
        column = Column(name = "product_code", unique = true),
    ) val code: ProductCode,
    @Column(name = "stock") val stock: Long,
    price: Money,
    name: String,
) : BaseEntity() {
    @Embedded
    @AttributeOverrides(
        AttributeOverride(
            name = "amount",
            column = Column(name = "product_price", precision = 19, scale = 2),
        ),
        AttributeOverride(
            name = "currencyCode",
            column = Column(name = "product_currency_code"),
        ),
    )
    var price: Money = price
        private set

    @Column(name = "name", length = 20)
    var name: String = name
        private set

    init {
        validateName(name)
        validateStock(stock)
    }

    private fun validateName(name: String) {
        require(name.isNotBlank()) { "상품명은 필수입니다" }
        require(name.length <= 20) { "상품명은 20자 이하여야 합니다" }
        require(name.matches(Regex("^[가-힣a-zA-Z0-9\\s]+$"))) { "상품명은 특수문자를 포함할 수 없습니다" }
    }

    private fun validateStock(stock: Long) {
        require(stock >= 0) { "상품재고는 0개 이상이어야 합니다" }
    }

    fun hasEnoughStock(quantity: Long): Boolean = this.stock >= quantity
}
