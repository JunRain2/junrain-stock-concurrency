package com.junrain.stock.product.command.domain

import com.junrain.stock.contract.entity.BaseEntity
import com.junrain.stock.contract.vo.Money
import com.junrain.stock.product.command.domain.vo.ProductCode
import jakarta.persistence.*

@Entity
@Table(name = "products")
class Product(
    @Column(name = "owner_id")
    val ownerId: Long,
    @Embedded @AttributeOverride(
        name = "code", column = Column(name = "product_code", unique = true)
    ) val code: ProductCode,
    @Column(name = "stock")
    val stock: Long,
    price: Money,
    name: String
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

    fun hasEnoughStock(quantity: Long): Boolean {
        return this.stock >= quantity
    }
}