package com.junrain.stock.domain.product.exception

import com.junrain.stock.domain.common.BusinessException
import com.junrain.stock.domain.common.ErrorCode

class ProductOutOfStockException : BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK)
