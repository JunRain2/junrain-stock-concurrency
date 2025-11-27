package com.example.demo.product.exception

import com.example.demo.global.contract.ErrorCode
import com.example.demo.global.contract.BusinessException

class ProductNotFoundException : BusinessException(ErrorCode.PRODUCT_NOT_FOUND) {}