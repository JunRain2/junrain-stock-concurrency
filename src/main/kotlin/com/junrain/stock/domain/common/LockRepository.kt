package com.junrain.stock.domain.common

interface LockRepository {
    fun <T> executeWithLock(
        vararg keys: String,
        action: () -> T,
    ): T
}
