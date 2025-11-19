package com.example.demo.application

import com.example.demo.application.exception.NotFoundProductException
import com.example.demo.common.lock.LockRepository
import com.example.demo.domain.product.Product
import com.example.demo.domain.product.ProductRepository
import com.example.demo.domain.product.vo.Money
import com.example.demo.domain.product.vo.ProductCode
import com.example.demo.ui.dto.request.PurchaseProductRequest
import com.example.demo.ui.dto.request.RegisterProductRequest
import com.example.demo.ui.dto.response.GetProductResponse
import com.example.demo.ui.dto.response.PurchaseProductResponse
import com.example.demo.ui.dto.response.RegisterProductResponse
import org.springframework.stereotype.Service

@Service
class ProductService(
    private val productRepository: ProductRepository,
    private val lockRepository: LockRepository,
) {
    fun decreaseStock(productId: Long, request: PurchaseProductRequest): PurchaseProductResponse {
        lockRepository.executeWithLock("product:${productId}") {
            val product = productRepository.findById(productId).orElseThrow { throw NotFoundProductException() }

            lockRepository.executeWithLock(product.id.toString()) { product.decrease(request.amount) }

            productRepository.save(product)
        }

        return PurchaseProductResponse(productId)
    }

    fun getProduct(productId: Long): GetProductResponse {
        val product = productRepository.findById(productId).orElseThrow { throw NotFoundProductException() }

        return GetProductResponse(
            productId = product.id,
            name = product.name,
            code = product.code.code,
            price = product.price.amount,
            stock = product.stock
        )
    }

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