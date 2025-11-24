package com.example.demo.order.domain

import com.example.demo.global.contract.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity

@Entity
class Order(
    @Column(name = "orderer_id")
    val ordererId: Long,
): BaseEntity() {
}