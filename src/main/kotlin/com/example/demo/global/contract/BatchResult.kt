package com.example.demo.global.contract

data class BatchResult<T>(
    val succeeded: List<T>,
    val failed: List<FailedItem<T>>
)

data class FailedItem<T>(
    val item: T,
    val reason: Throwable
)
