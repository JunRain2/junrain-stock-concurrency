package com.junrain.stock.product.infra

import com.junrain.stock.member.application.MemberRoleVerificationService
import com.junrain.stock.product.domain.OwnerValidationService
import com.junrain.stock.product.domain.exception.ProductAccessDeniedException
import org.springframework.stereotype.Service

@Service
class OwnerValidationServiceImpl(
    private val memberRoleVerificationService: MemberRoleVerificationService,
) : OwnerValidationService {
    override fun validateMemberIsSeller(memberId: Long) {
        if (!memberRoleVerificationService.isMemberSeller(memberId)) {
            throw ProductAccessDeniedException()
        }
    }
}
