package com.example.demo.infrastructure.memory

import com.example.demo.common.lock.LockRepository
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

@Primary
@Repository
class InMemoryLockRepository() : LockRepository {
    private val locks = ConcurrentHashMap<String, ReentrantLock>()

    override fun <T> executeWithLock(key: String, action: () -> T): T {
        val lock = locks.computeIfAbsent(key) { ReentrantLock() }


        val acquired = lock.tryLock(3L, TimeUnit.SECONDS)
        check(acquired) { "Failed to acquire lock: $key" }

        try {
            return action()
        } finally {
            lock.unlock()
        }
    }
}


