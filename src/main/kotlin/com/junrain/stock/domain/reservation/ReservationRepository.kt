package com.junrain.stock.domain.reservation

import org.springframework.data.jpa.repository.JpaRepository

interface ReservationRepository : JpaRepository<Reservation, Long> {
    fun findByTrxId(trxId: String): Reservation?
}
