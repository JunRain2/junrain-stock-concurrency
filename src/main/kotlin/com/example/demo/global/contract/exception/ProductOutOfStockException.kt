package com.example.demo.global.contract.exception

import com.example.demo.global.contract.BusinessException
import com.example.demo.global.contract.ErrorCode

class ProductOutOfStockException : BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK) {
}