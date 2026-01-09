package com.junrain.stock.order.command.domain

import org.springframework.data.jpa.repository.JpaRepository

interface OrderItemRepository : JpaRepository<OrderItem, Long> {
    fun findAllByOrder(order: Order): List<OrderItem>
}