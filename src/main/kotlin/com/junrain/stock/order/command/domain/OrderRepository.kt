package com.junrain.stock.order.command.domain

import com.junrain.stock.order.command.domain.vo.OrderCode
import org.springframework.data.jpa.repository.JpaRepository

interface OrderRepository : JpaRepository<Order, Long> {
    fun findByCode(code: OrderCode): Order?
}