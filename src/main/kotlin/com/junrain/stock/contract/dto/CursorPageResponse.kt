package com.junrain.stock.contract.dto

data class CursorPageResponse<T>(
    val data: List<T>,
    val size: Int,
    val hasNext: Boolean,
    val nextCursor: Map<String, Any>
)