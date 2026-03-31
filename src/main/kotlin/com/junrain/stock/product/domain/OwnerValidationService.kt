package com.junrain.stock.product.domain

interface OwnerValidationService {
    fun validateMemberIsSeller(memberId: Long)
}
