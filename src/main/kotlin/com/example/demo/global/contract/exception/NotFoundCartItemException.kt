package com.example.demo.global.contract.exception

import com.example.demo.global.contract.BusinessException
import com.example.demo.global.contract.ErrorCode

class NotFoundCartItemException : BusinessException(ErrorCode.CART_ITEM_NOT_FOUND) {}