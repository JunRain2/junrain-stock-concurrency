package com.junrain.stock.product.exception

import com.junrain.stock.contract.exception.ErrorCode
import com.junrain.stock.contract.exception.BusinessException

class ProductAccessDeniedException: BusinessException(ErrorCode.PRODUCT_ACCESS_DENIED) {
}