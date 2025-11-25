package com.example.demo.global.contract.exception

import com.example.demo.global.contract.BusinessException
import com.example.demo.global.contract.ErrorCode

class NotFoundMemberException : BusinessException(ErrorCode.MEMBER_NOT_FOUND) {
}