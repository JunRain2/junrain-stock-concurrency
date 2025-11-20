package com.example.demo.global.lock

interface LockRepository {
    fun <T> executeWithLock(key: String, action: () -> T): T
}