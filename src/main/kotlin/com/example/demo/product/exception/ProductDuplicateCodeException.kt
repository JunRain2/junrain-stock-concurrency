package com.example.demo.product.exception

import com.example.demo.global.contract.BusinessException
import com.example.demo.global.contract.ErrorCode
import com.example.demo.product.command.domain.vo.ProductCode

class ProductDuplicateCodeException(val code: ProductCode) :
    BusinessException(ErrorCode.PRODUCT_CODE_DUPLICATED)