package com.example.demo.domain.product

import com.example.demo.domain.product.vo.Money
import com.example.demo.domain.product.vo.ProductCode
import jakarta.persistence.*

@Entity
class Product(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    val name: String,
    @Embedded
    @AttributeOverride(
        name = "code",
        column = Column(name = "code", unique = true, nullable = false)
    )
    val code: ProductCode,
    @Embedded
    val price: Money,
    var stock: Long,
) {
    init {
        validateName(name)
        require(stock >= 0) { "재고는 0개 이상이어야 합니다" }
    }

    private fun validateName(name: String) {
        require(name.isNotBlank()) { "상품명은 필수입니다" }
        require(name.length <= 20) { "상품명은 20자 이하여야 합니다" }
        require(name.matches(Regex("^[가-힣a-zA-Z0-9\\s]+$"))) { "상품명은 특수문자를 포함할 수 없습니다" }
    }

    fun decrease(quantity: Long) {
        require(this.stock - quantity >= 0) { "재고가 없습니다." }

        this.stock -= quantity
    }
}