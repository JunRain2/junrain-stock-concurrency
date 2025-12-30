package com.junrain.stock.cart.exception

import com.junrain.stock.contract.exception.BusinessException
import com.junrain.stock.contract.exception.ErrorCode

class CartItemNotFoundException : BusinessException(ErrorCode.CART_ITEM_NOT_FOUND) {}