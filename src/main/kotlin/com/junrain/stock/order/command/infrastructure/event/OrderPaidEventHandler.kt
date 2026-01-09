package com.junrain.stock.order.command.infrastructure.event

import com.junrain.stock.order.command.domain.OrderPaidEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class OrderPaidEventHandler(
    private val applicationSCope: CoroutineScope
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handler(event: OrderPaidEvent) = applicationSCope.launch {
        TODO()
    }
}