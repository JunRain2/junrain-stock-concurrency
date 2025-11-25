package com.example.demo.member.application

import com.example.demo.global.contract.exception.NotFoundMemberException
import com.example.demo.member.domain.MemberRepository
import org.springframework.stereotype.Service

@Service
class MemberRoleVerificationService(
    private val memberRepository: MemberRepository
) {
    fun isMemberSeller(memberId: Long): Boolean {
        val member = memberRepository.findById(memberId).orElseThrow {
            NotFoundMemberException()
        }

        return member.isSeller()
    }
}