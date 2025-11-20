package com.example.demo.product.exception

import com.example.demo.global.contract.BusinessException
import com.example.demo.global.contract.ErrorCode

class DuplicateProductCodeException() : BusinessException(ErrorCode.PRODUCT_CODE_DUPLICATED)
