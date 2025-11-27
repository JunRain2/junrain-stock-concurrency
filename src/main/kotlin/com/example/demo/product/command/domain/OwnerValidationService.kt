package com.example.demo.product.command.domain

interface OwnerValidationService {
    fun validateMemberIsSeller(memberId: Long)
}