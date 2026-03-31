package com.junrain.stock.product.domain.exception

import com.junrain.stock.common.domain.BusinessException
import com.junrain.stock.common.domain.ErrorCode
import com.junrain.stock.product.domain.vo.ProductCode

class ProductDuplicateCodeException(
    val code: ProductCode,
) : BusinessException(ErrorCode.PRODUCT_CODE_DUPLICATED)
