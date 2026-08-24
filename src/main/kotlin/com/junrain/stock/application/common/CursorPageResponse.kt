package com.junrain.stock.application.common

data class CursorPageResponse<T>(
    val data: List<T>,
    val size: Int,
    val hasNext: Boolean,
    val nextCursor: Map<String, Any>,
)
