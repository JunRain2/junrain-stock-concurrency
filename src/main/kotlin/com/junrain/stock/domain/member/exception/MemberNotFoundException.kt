package com.junrain.stock.domain.member.exception

import com.junrain.stock.domain.common.BusinessException
import com.junrain.stock.domain.common.ErrorCode

class MemberNotFoundException : BusinessException(ErrorCode.MEMBER_NOT_FOUND)
