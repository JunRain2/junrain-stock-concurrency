package com.example.demo.cart.exception

import com.example.demo.global.contract.BusinessException
import com.example.demo.global.contract.ErrorCode

class CartItemNotFoundException : BusinessException(ErrorCode.CART_ITEM_NOT_FOUND) {}