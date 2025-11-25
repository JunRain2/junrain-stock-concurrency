package com.example.demo.product.command.domain

interface SellerVerificationService {
    fun validateMemberIsSeller(memberId: Long)
}