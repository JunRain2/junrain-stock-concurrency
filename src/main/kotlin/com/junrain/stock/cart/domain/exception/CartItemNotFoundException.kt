package com.junrain.stock.cart.domain.exception

import com.junrain.stock.common.domain.BusinessException
import com.junrain.stock.common.domain.ErrorCode

class CartItemNotFoundException : BusinessException(ErrorCode.CART_ITEM_NOT_FOUND)
