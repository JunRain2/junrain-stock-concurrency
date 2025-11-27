package com.example.demo.product.command.infrastructure

import com.example.demo.product.exception.ProductAccessDeniedException
import com.example.demo.member.command.application.MemberRoleVerificationService
import com.example.demo.product.command.domain.OwnerValidationService
import org.springframework.stereotype.Service

@Service
class OwnerValidationServiceImpl(
    private val memberRoleVerificationService: MemberRoleVerificationService
) : OwnerValidationService {
    override fun validateMemberIsSeller(memberId: Long) {
        if (!memberRoleVerificationService.isMemberSeller(memberId)) {
            throw ProductAccessDeniedException()
        }
    }
}