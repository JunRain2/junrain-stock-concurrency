package com.example.demo.product.exception

import com.example.demo.global.contract.ErrorCode
import com.example.demo.global.contract.BusinessException

class ProductOutOfStockException : BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK) {
}