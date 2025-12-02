package com.example.demo.global.lock.infrastructure.redis

import com.example.demo.global.lock.LockRepository
import org.springframework.data.redis.core.RedisTemplate

class RedisLockRepositoryImpl(
    private val redisTemplate: RedisTemplate<String, String>
) : LockRepository {
    override fun <T> executeWithLock(key: String, action: () -> T): T {
        TODO("Not yet implemented")
    }

    override fun <T> executeWithLock(
        prefix: String,
        keys: List<String>,
        action: () -> T
    ): T {
        // 정렬
        val sortedKeys = keys.sorted()
        // 정렬 순으로 획득
        for (key in sortedKeys) {

        }
        // 로직 수행
        val result = action()

        // key를 반납

        // key를 얻는 도중 or 로직 도중에 문제가 발생했을 경우 lock을 반납하는 로직까지 고려해야 함

        return result
    }

    override fun getLock(vararg key: String) {
        TODO("Not yet implemented")
    }

    override fun releaseLock(vararg key: String) {
        TODO("Not yet implemented")
    }

}