package com.junrain.stock.domain.product.exception

import com.junrain.stock.domain.common.BusinessException
import com.junrain.stock.domain.common.ErrorCode
import com.junrain.stock.domain.product.vo.ProductCode

class ProductCreationException(
    val code: ProductCode,
) : BusinessException(ErrorCode.PRODUCT_CREATION_ERROR)
