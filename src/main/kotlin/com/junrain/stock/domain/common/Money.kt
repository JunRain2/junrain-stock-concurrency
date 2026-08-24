package com.junrain.stock.domain.common

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.math.BigDecimal
import java.util.*

@Embeddable
data class Money(
    @Column(precision = 19, scale = 2)
    val amount: BigDecimal,
    @Enumerated(EnumType.STRING)
    val currencyCode: CurrencyCode,
) {
    init {
        require(amount >= BigDecimal.ZERO) { "Money value cannot be negative" }
    }

    companion object {
        fun of(
            value: BigDecimal,
            currencyCode: CurrencyCode = CurrencyCode.KOR,
        ): Money = Money(value, currencyCode)

        fun of(
            value: Long,
            currencyCode: CurrencyCode = CurrencyCode.KOR,
        ): Money = Money(BigDecimal(value), currencyCode)

        fun of(
            value: Int,
            currencyCode: CurrencyCode = CurrencyCode.KOR,
        ): Money = Money(BigDecimal(value), currencyCode)
    }

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(amount + other.amount, currencyCode)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(amount - other.amount, currencyCode)
    }

    operator fun times(multiplier: BigDecimal): Money = Money(amount * multiplier, currencyCode)

    operator fun times(multiplier: Long): Money = Money(amount * multiplier.toBigDecimal(), currencyCode)

    operator fun div(divisor: BigDecimal): Money {
        require(divisor != BigDecimal.ZERO) { "Cannot divide by zero" }
        return Money(amount / divisor, currencyCode)
    }

    operator fun div(divisor: Long): Money {
        require(divisor != 0L) { "Cannot divide by zero" }
        return Money(amount / divisor.toBigDecimal(), currencyCode)
    }

    operator fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return amount.compareTo(other.amount)
    }

    private fun requireSameCurrency(other: Money) {
        require(currencyCode == other.currencyCode) {
            "Cannot operate on different currencies: $currencyCode and ${other.currencyCode}"
        }
    }
}

enum class CurrencyCode(
    val currency: Currency,
) {
    KOR(Currency.getInstance("KRW")),
}
