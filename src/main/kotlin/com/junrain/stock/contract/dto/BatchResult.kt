package com.junrain.stock.contract.dto

data class BatchResult<T>(
    val succeeded: List<T>,
    val failed: List<FailedItem<T>>
)

data class FailedItem<T>(
    val item: T,
    val reason: Throwable
)
