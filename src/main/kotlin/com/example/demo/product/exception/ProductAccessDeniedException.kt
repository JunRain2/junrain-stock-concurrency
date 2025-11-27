package com.example.demo.product.exception

import com.example.demo.global.contract.ErrorCode
import com.example.demo.global.contract.BusinessException

class ProductAccessDeniedException: BusinessException(ErrorCode.PRODUCT_ACCESS_DENIED) {
}