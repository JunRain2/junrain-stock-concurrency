package com.junrain.stock.order.domain.vo

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

data class OrderCode(
    val code: String = generateCode(),
) {
    companion object {
        private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

        fun generateCode(): String {
            val now = LocalDateTime.now().format(DATE_TIME_FORMATTER)

            return "order-$now-${UUID.randomUUID().toString().take(4)}"
        }
    }
}
