package com.example.demo.order.command.domain.vo

import com.example.demo.global.contract.vo.Address
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded

@Embeddable
data class Orderer(
    val ordererId: Long,
    @Embedded
    val address: Address
)

