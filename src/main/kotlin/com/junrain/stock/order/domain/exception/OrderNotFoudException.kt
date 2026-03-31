package com.junrain.stock.order.domain.exception

import com.junrain.stock.common.domain.BusinessException
import com.junrain.stock.common.domain.ErrorCode

class OrderNotFoudException : BusinessException(ErrorCode.ORDER_NOT_FOUND)
