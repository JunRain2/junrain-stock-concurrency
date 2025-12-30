package com.junrain.stock.contract.lock

interface LockRepository {
    fun <T> executeWithLock(vararg keys: String, action: () -> T): T
}