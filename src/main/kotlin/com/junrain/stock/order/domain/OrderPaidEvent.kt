package com.junrain.stock.order.domain

import com.junrain.stock.order.domain.vo.OrderCode

data class OrderPaidEvent(
    val orderCode: OrderCode,
)
