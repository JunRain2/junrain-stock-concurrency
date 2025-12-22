package com.example.demo.product.exception

import com.example.demo.contract.exception.ErrorCode
import com.example.demo.contract.exception.BusinessException

class ProductNotFoundException : BusinessException(ErrorCode.PRODUCT_NOT_FOUND) {}