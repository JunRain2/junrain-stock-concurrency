package com.example.demo.product.command.domain

import com.example.demo.product.exception.ProductCreationException
import com.example.demo.product.exception.ProductDuplicateCodeException
import org.springframework.stereotype.Service

@Service
class ProductValidationService {
    fun checkProducts(products: List<Product>): List<Result<Product>> {
        return products.groupBy { it.code }.flatMap { (productCode, productList) ->
            if (productList.size == 1) {
                listOf(Result.success(productList.first()))
            } else {
                productList.map {
                    Result.failure(
                        ProductCreationException(
                            productCode.code,
                            ProductDuplicateCodeException(productCode)
                        )
                    )
                }
            }
        }
    }
}