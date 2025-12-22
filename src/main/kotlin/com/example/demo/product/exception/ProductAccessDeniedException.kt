package com.example.demo.product.exception

import com.example.demo.contract.exception.ErrorCode
import com.example.demo.contract.exception.BusinessException

class ProductAccessDeniedException: BusinessException(ErrorCode.PRODUCT_ACCESS_DENIED) {
}