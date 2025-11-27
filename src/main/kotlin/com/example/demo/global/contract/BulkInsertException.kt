package com.example.demo.global.contract

class BulkInsertException(
    val identifier: String,
    val customMessage: String? = null
) : RuntimeException(customMessage) {
}