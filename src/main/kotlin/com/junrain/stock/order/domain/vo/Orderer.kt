package com.junrain.stock.order.domain.vo

import com.junrain.stock.common.domain.Address
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded

@Embeddable
data class Orderer(
    val ordererId: Long,
    @Embedded
    val address: Address,
)
