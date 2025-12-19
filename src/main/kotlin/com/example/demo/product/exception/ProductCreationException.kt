package com.example.demo.product.exception

import com.example.demo.global.contract.BusinessException
import com.example.demo.global.contract.ErrorCode

class ProductCreationException(
    val productCode: String,
    val exception: Throwable? = null
) : BusinessException(ErrorCode.PRODUCT_CREATION_ERROR) {
}