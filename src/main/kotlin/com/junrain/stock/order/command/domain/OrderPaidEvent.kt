package com.junrain.stock.order.command.domain

import com.junrain.stock.order.command.domain.vo.OrderCode

data class OrderPaidEvent(
    val orderCode: OrderCode,
)