package com.example.demo.member.exception

import com.example.demo.global.contract.BusinessException
import com.example.demo.global.contract.ErrorCode

class NotFoundMemberException : BusinessException(ErrorCode.MEMBER_NOT_FOUND) {
}