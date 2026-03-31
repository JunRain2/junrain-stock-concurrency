package com.junrain.stock.product.domain.exception

import com.junrain.stock.common.domain.BusinessException
import com.junrain.stock.common.domain.ErrorCode

class ProductAccessDeniedException : BusinessException(ErrorCode.PRODUCT_ACCESS_DENIED)
