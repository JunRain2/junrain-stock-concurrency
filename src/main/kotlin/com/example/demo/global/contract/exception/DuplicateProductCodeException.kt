package com.example.demo.global.contract.exception

import com.example.demo.global.contract.BusinessException
import com.example.demo.global.contract.ErrorCode

class DuplicateProductCodeException() : BusinessException(ErrorCode.PRODUCT_CODE_DUPLICATED)
