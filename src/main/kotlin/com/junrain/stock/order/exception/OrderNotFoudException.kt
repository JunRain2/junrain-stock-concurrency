package com.junrain.stock.order.exception

import com.junrain.stock.contract.exception.BusinessException
import com.junrain.stock.contract.exception.ErrorCode

class OrderNotFoudException() : BusinessException(ErrorCode.ORDER_NOT_FOUND) {
}