package com.example.demo.product.domain.product.vo

import jakarta.persistence.Embeddable


@Embeddable
data class ProductCode(
    val code: String
)