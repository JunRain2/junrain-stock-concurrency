package com.example.demo.member.command.application

import com.example.demo.member.exception.MemberNotFoundException
import com.example.demo.member.command.domain.MemberRepository
import org.springframework.stereotype.Service

@Service
class MemberRoleVerificationService(
    private val memberRepository: MemberRepository
) {
    fun isMemberSeller(memberId: Long): Boolean {
        val member = memberRepository.findById(memberId).orElseThrow {
            MemberNotFoundException()
        }

        return member.isSeller()
    }
}