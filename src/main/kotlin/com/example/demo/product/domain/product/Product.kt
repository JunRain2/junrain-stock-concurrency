package com.example.demo.product.domain.product

import com.example.demo.global.contract.BaseEntity
import com.example.demo.product.domain.product.vo.Money
import com.example.demo.product.domain.product.vo.ProductCode
import jakarta.persistence.*

@Entity
@Table(name = "products")
class Product(
    @Column(name = "owner_id") val ownerId: Long,

    @Embedded @AttributeOverride(
        name = "code", column = Column(name = "product_code", unique = true)
    ) val code: ProductCode, price: Money, stock: Long, name: String
) : BaseEntity() {
    @Embedded
    @AttributeOverrides(
        AttributeOverride(
            name = "amount", column = Column(name = "product_price", precision = 19, scale = 2)
        ), AttributeOverride(
            name = "currencyCode", column = Column(name = "product_currency_code")
        )
    )
    var price: Money = price
        private set

    @Column(name = "stock")
    var stock: Long = stock
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

    fun decrease(quantity: Long) {
        require(this.stock - quantity >= 0) { "재고가 없습니다." }

        this.stock -= quantity
    }
}