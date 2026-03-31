package com.junrain.stock.member.domain.exception

import com.junrain.stock.common.domain.BusinessException
import com.junrain.stock.common.domain.ErrorCode

class MemberNotFoundException : BusinessException(ErrorCode.MEMBER_NOT_FOUND)
