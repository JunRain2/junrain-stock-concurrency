package com.junrain.stock.product.exception

import com.junrain.stock.contract.exception.ErrorCode
import com.junrain.stock.contract.exception.BusinessException

class ProductNotFoundException : BusinessException(ErrorCode.PRODUCT_NOT_FOUND) {}