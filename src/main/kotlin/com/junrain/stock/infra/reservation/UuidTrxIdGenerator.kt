package com.junrain.stock.infra.reservation

import com.junrain.stock.domain.reservation.TrxIdGenerator
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UuidTrxIdGenerator : TrxIdGenerator {
    override fun generate(): String = UUID.randomUUID().toString()
}
