package com.example.demo.product.application

import com.example.demo.product.domain.product.Product
import com.example.demo.product.domain.product.ProductRepository
import com.example.demo.product.domain.product.vo.Money
import com.example.demo.product.domain.product.vo.ProductCode
import com.example.demo.product.ui.dto.request.RegisterProductRequest
import com.example.demo.product.ui.dto.response.RegisterProductResponse
import org.springframework.stereotype.Service

@Service
class RegisterProductService(
    private val productRepository: ProductRepository
) {
    fun registerProduct(request: RegisterProductRequest): RegisterProductResponse {
        val productCode = ProductCode(request.code)
        check(productRepository.existsByCode(productCode)) { "이미 존재하는 상품 코드입니다." }

        val product = Product(
            name = request.name,
            code = productCode,
            price = Money.of(request.price),

            stock = request.stock
        ).let { productRepository.save(it) }

        return RegisterProductResponse(
            productid = product.id
        )
    }
}