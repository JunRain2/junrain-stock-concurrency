package com.example.demo.member.domain

import com.example.demo.global.contract.BaseEntity
import jakarta.persistence.*

@Entity
@Table(name = "members")
class Member(
    @Column(name = "member_type")
    @Enumerated(EnumType.STRING)
    val memberType: MemberType,
    @Column(name = "member_name")
    val name: String
) : BaseEntity() {
    fun isSeller(): Boolean {
        return memberType == MemberType.SELLER
    }
}