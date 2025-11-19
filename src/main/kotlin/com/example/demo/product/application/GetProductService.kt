package com.example.demo.product.application

import com.example.demo.product.application.exception.NotFoundProductException
import com.example.demo.product.domain.product.ProductRepository
import com.example.demo.product.ui.dto.response.GetProductResponse
import org.springframework.stereotype.Service

@Service
class GetProductService(
    private val productRepository: ProductRepository
) {
    fun getProduct(productId: Long): GetProductResponse {
        val product =
            productRepository.findById(productId).orElseThrow { throw NotFoundProductException() }

        return GetProductResponse(
            productId = product.id,
            name = product.name,
            code = product.code.code,
            price = product.price.amount,
            stock = product.stock
        )
    }
}