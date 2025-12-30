package com.junrain.stock.product.exception

import com.junrain.stock.contract.exception.ErrorCode
import com.junrain.stock.contract.exception.BusinessException

class ProductOutOfStockException : BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK) {
}