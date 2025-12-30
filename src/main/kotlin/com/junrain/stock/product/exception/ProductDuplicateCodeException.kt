package com.junrain.stock.product.exception

import com.junrain.stock.contract.exception.BusinessException
import com.junrain.stock.contract.exception.ErrorCode
import com.junrain.stock.product.command.domain.vo.ProductCode

class ProductDuplicateCodeException(val code: ProductCode) :
    BusinessException(ErrorCode.PRODUCT_CODE_DUPLICATED)