package com.example.demo.product.exception

import com.example.demo.global.contract.BusinessException
import com.example.demo.global.contract.ErrorCode

class ProductAccessDeniedException: BusinessException(ErrorCode.PRODUCT_ACCESS_DENIED) {
}