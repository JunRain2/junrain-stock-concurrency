package com.junrain.stock.contract.vo

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class Address(
    @Column(name = "postal_code", length = 10)
    val postalCode: String,

    @Column(name = "street_address", length = 200)
    val streetAddress: String,

    @Column(name = "detail_address", length = 100)
    val detailAddress: String? = null,
) {
    init {
        require(postalCode.isNotBlank()) { "우편번호는 필수입니다" }
        require(streetAddress.isNotBlank()) { "도로명 주소는 필수입니다" }
    }

    fun fullAddress(): String {
        return if (detailAddress.isNullOrBlank()) {
            "[$postalCode] $streetAddress"
        } else {
            "[$postalCode] $streetAddress, $detailAddress"
        }
    }
}