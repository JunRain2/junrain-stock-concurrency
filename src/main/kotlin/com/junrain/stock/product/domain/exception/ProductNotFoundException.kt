package com.junrain.stock.product.domain.exception

import com.junrain.stock.common.domain.BusinessException
import com.junrain.stock.common.domain.ErrorCode

class ProductNotFoundException : BusinessException(ErrorCode.PRODUCT_NOT_FOUND)
