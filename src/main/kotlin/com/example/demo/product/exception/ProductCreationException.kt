package com.example.demo.product.exception

import com.example.demo.contract.exception.BusinessException
import com.example.demo.contract.exception.ErrorCode
import com.example.demo.product.command.domain.vo.ProductCode

class ProductCreationException(val code: ProductCode) :
    BusinessException(
        ErrorCode.PRODUCT_CREATION_ERROR,
        "$code 삽입 도중에 문제가 발생 했습니다. product_code가 중복됐는지 확인해보세요"
    ) {
}