package com.junrain.stock.order.exception

import com.junrain.stock.contract.exception.BusinessException
import com.junrain.stock.contract.exception.ErrorCode

class OrderInvalidException : BusinessException(ErrorCode.ORDER_INVALID) {
}