package com.example.demo.product.exception

import com.example.demo.contract.exception.BusinessException
import com.example.demo.contract.exception.ErrorCode
import com.example.demo.product.command.domain.vo.ProductCode

class ProductDuplicateCodeException(val code: ProductCode) :
    BusinessException(ErrorCode.PRODUCT_CODE_DUPLICATED)