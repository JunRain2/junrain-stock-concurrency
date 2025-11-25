package com.example.demo.product.command.infrastructure

import com.example.demo.global.contract.exception.ProductAccessDeniedException
import com.example.demo.member.application.MemberRoleVerificationService
import com.example.demo.product.command.domain.SellerVerificationService
import org.springframework.stereotype.Service

@Service
class SellerVerificationServiceImpl(
    private val memberRoleVerificationService: MemberRoleVerificationService
) : SellerVerificationService {
    override fun validateMemberIsSeller(memberId: Long) {
        if (!memberRoleVerificationService.isMemberSeller(memberId)) {
            throw ProductAccessDeniedException()
        }
    }
}