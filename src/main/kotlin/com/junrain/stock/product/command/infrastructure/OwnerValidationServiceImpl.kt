package com.junrain.stock.product.command.infrastructure

import com.junrain.stock.member.command.application.MemberRoleVerificationService
import com.junrain.stock.product.command.domain.OwnerValidationService
import com.junrain.stock.product.exception.ProductAccessDeniedException
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