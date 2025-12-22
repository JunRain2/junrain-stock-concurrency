package com.example.demo.contract.lock

interface LockRepository {
    fun <T> executeWithLock(vararg keys: String, action: () -> T): T
}