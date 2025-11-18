package com.example.demo.domain.product

import com.example.demo.domain.product.vo.Money
import com.example.demo.domain.product.vo.ProductCode
import org.springframework.stereotype.Service

@Service
class ProductDomainService(
    private val productRepository: ProductRepository
) {
    fun registerNewProduct(
        code: ProductCode,
        name: String,
        price: Money,
        stock: Long
    ): Product {
        require(!productRepository.existsByCode(code)) { "이미 존재하는 코드입니다." }

        return Product(
            name = name,
            code = code,
            price = price,
            stock = stock
        )
    }
}