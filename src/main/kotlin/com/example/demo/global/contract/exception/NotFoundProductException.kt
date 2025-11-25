package com.example.demo.global.contract.exception

import com.example.demo.global.contract.BusinessException
import com.example.demo.global.contract.ErrorCode

class NotFoundProductException : BusinessException(ErrorCode.PRODUCT_NOT_FOUND) {}