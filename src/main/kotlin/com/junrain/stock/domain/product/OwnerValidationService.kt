package com.junrain.stock.domain.product

interface OwnerValidationService {
    fun validateMemberIsSeller(memberId: Long)
}
