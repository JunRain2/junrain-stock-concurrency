package com.junrain.stock.member.command.domain

import com.junrain.stock.contract.entity.BaseEntity
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