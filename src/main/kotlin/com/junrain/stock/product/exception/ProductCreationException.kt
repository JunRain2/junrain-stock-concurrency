package com.junrain.stock.product.exception

import com.junrain.stock.contract.exception.BusinessException
import com.junrain.stock.contract.exception.ErrorCode
import com.junrain.stock.product.command.domain.vo.ProductCode

class ProductCreationException(val code: ProductCode) :
    BusinessException(
        ErrorCode.PRODUCT_CREATION_ERROR,
        "$code 삽입 도중에 문제가 발생 했습니다. 데이터를 다시 한 번 확인해보세요."
    ) {
}