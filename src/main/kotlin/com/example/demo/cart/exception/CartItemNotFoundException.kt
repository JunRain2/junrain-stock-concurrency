package com.example.demo.cart.exception

import com.example.demo.contract.exception.BusinessException
import com.example.demo.contract.exception.ErrorCode

class CartItemNotFoundException : BusinessException(ErrorCode.CART_ITEM_NOT_FOUND) {}