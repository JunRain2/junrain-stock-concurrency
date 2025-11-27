package com.example.demo.member.exception

import com.example.demo.global.contract.ErrorCode
import com.example.demo.global.contract.BusinessException

class MemberNotFoundException : BusinessException(ErrorCode.MEMBER_NOT_FOUND) {
}