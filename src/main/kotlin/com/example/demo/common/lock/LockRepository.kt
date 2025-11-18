package com.example.demo.common.lock

interface LockRepository {
    fun <T> executeWithLock(key: String, action: () -> T): T
}