package com.example.demo.member.exception

import com.example.demo.contract.exception.ErrorCode
import com.example.demo.contract.exception.BusinessException

class MemberNotFoundException : BusinessException(ErrorCode.MEMBER_NOT_FOUND) {
}