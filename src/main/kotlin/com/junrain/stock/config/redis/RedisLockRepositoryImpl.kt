package com.junrain.stock.config.redis

import com.junrain.stock.contract.lock.LockRepository
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class RedisLockRepositoryImpl(
    private val redissonClient: RedissonClient
) : LockRepository {
    override fun <T> executeWithLock(vararg keys: String, action: () -> T): T {
        val locks = keys.sorted().map { redissonClient.getLock(it) }.toTypedArray()
        val multiLock = redissonClient.getMultiLock(*locks)

        // 3초 대기, 30초마다 Lock을 갱신
        return if (multiLock.tryLock(3, -1, TimeUnit.SECONDS)) {
            try {
                action()
            } finally {
                runCatching { multiLock.unlock() }
            }
        } else {
            throw RuntimeException("Lock 획득 실패")
        }
    }
}