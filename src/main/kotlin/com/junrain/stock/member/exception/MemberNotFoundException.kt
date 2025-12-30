package com.junrain.stock.member.exception

import com.junrain.stock.contract.exception.ErrorCode
import com.junrain.stock.contract.exception.BusinessException

class MemberNotFoundException : BusinessException(ErrorCode.MEMBER_NOT_FOUND) {
}