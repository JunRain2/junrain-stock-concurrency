package com.junrain.stock.infra.product

import com.junrain.stock.application.member.VerifyMemberIsSeller
import com.junrain.stock.domain.product.OwnerValidationService
import com.junrain.stock.domain.product.exception.ProductAccessDeniedException
import org.springframework.stereotype.Service

@Service
class OwnerValidationServiceImpl(
    private val verifyMemberIsSeller: VerifyMemberIsSeller,
) : OwnerValidationService {
    override fun validateMemberIsSeller(memberId: Long) {
        if (!verifyMemberIsSeller(memberId)) {
            throw ProductAccessDeniedException()
        }
    }
}
