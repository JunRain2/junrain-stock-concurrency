package com.junrain.stock.product.command.domain

interface OwnerValidationService {
    fun validateMemberIsSeller(memberId: Long)
}