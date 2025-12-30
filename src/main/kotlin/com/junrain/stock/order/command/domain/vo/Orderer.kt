package com.junrain.stock.order.command.domain.vo

import com.junrain.stock.contract.vo.Address
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded

@Embeddable
data class Orderer(
    val ordererId: Long,
    @Embedded
    val address: Address
)

