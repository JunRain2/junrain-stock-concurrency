package com.junrain.stock.domain.member

import com.junrain.stock.domain.common.BaseEntity
import jakarta.persistence.*

@Entity
@Table(name = "members")
class Member(
    @Column(name = "member_type")
    @Enumerated(EnumType.STRING)
    val memberType: MemberType,
    @Column(name = "member_name")
    val name: String,
) : BaseEntity() {
    fun isSeller(): Boolean = memberType == MemberType.SELLER
}
