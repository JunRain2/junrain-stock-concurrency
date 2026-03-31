package com.junrain.stock.product.domain.exception

import com.junrain.stock.common.domain.BusinessException
import com.junrain.stock.common.domain.ErrorCode

class ProductOutOfStockException : BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK)
