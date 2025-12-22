package com.example.demo.product.exception

import com.example.demo.contract.exception.ErrorCode
import com.example.demo.contract.exception.BusinessException

class ProductOutOfStockException : BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK) {
}