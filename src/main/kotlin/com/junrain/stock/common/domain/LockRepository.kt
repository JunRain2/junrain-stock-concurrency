package com.junrain.stock.common.domain

interface LockRepository {
    fun <T> executeWithLock(
        vararg keys: String,
        action: () -> T,
    ): T
}
