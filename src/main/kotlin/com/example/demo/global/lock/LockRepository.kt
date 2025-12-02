package com.example.demo.global.lock

interface LockRepository {
    fun <T> executeWithLock(key: String, action: () -> T): T

    fun <T> executeWithLock(prefix: String, keys: List<String>, action: () -> T): T

    fun getLock(vararg key: String)

    fun releaseLock(vararg key: String)
}