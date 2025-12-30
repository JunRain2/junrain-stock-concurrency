package com.junrain.stock.product.command.domain.vo

import jakarta.persistence.Column
import jakarta.persistence.Embeddable


@Embeddable
data class ProductCode(
    @Column(name = "code", unique = true)
    val code: String
) {
    init {
        require(code.isNotBlank()) { "코드는 빈 값이면 안됩니다." }
    }
}