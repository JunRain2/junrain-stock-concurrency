package com.junrain.stock.domain.reservation

/**
 * 구매 트랜잭션 ID 발급. 발급 규칙이 바뀌어도 구현체만 갈아끼운다.
 */
interface TrxIdGenerator {
    fun generate(): String
}
