package com.junrain.stock.domain.product.exception

import com.junrain.stock.domain.common.BusinessException
import com.junrain.stock.domain.common.ErrorCode

class ProductAccessDeniedException : BusinessException(ErrorCode.PRODUCT_ACCESS_DENIED)
