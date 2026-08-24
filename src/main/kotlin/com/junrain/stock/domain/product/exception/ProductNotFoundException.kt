package com.junrain.stock.domain.product.exception

import com.junrain.stock.domain.common.BusinessException
import com.junrain.stock.domain.common.ErrorCode

class ProductNotFoundException : BusinessException(ErrorCode.PRODUCT_NOT_FOUND)
