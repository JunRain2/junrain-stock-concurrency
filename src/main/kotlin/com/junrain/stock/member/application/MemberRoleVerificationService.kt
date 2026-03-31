package com.junrain.stock.member.application

import com.junrain.stock.member.domain.MemberRepository
import com.junrain.stock.member.domain.exception.MemberNotFoundException
import org.springframework.stereotype.Service

@Service
class MemberRoleVerificationService(
    private val memberRepository: MemberRepository,
) {
    fun isMemberSeller(memberId: Long): Boolean {
        val member =
            memberRepository.findById(memberId).orElseThrow {
                MemberNotFoundException()
            }

        return member.isSeller()
    }
}
