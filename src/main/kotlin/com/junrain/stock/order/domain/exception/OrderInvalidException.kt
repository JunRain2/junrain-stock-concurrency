package com.junrain.stock.order.domain.exception

import com.junrain.stock.common.domain.BusinessException
import com.junrain.stock.common.domain.ErrorCode

class OrderInvalidException : BusinessException(ErrorCode.ORDER_INVALID)
