package com.example.demo.global.lock

interface LockRepository {
    fun <T> executeWithLock(vararg keys: String, action: () -> T): T
}