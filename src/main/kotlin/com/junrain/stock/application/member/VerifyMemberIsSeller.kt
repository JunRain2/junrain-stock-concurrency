package com.junrain.stock.application.member

import com.junrain.stock.domain.member.MemberRepository
import com.junrain.stock.domain.member.exception.MemberNotFoundException
import org.springframework.stereotype.Service

@Service
class VerifyMemberIsSeller(
    private val memberRepository: MemberRepository,
) {
    operator fun invoke(memberId: Long): Boolean {
        val member =
            memberRepository.findById(memberId).orElseThrow {
                MemberNotFoundException()
            }

        return member.isSeller()
    }
}
