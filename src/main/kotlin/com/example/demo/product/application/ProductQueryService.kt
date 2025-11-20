package com.example.demo.product.application

import com.example.demo.member.domain.MemberRepository
import com.example.demo.member.exception.NotFoundMemberException
import com.example.demo.product.domain.product.ProductRepository
import com.example.demo.product.exception.NotFoundProductException
import com.example.demo.product.ui.dto.response.ProductDetailResponse
import org.springframework.stereotype.Service

@Service
class ProductQueryService(
    private val productRepository: ProductRepository,
    private val memberRepository: MemberRepository
) {
    fun getProductDetail(productId: Long): ProductDetailResponse {
        val product = productRepository.findById(productId)
            .orElseThrow { NotFoundProductException() }
        val owner = memberRepository.findById(product.ownerId)
            .orElseThrow { NotFoundMemberException() }

        return ProductDetailResponse(
            productId = product.id,
            name = product.name,
            code = product.code.code,
            price = product.price.amount,
            stock = product.stock,
            owner = ProductDetailResponse.OwnerResponse(
                id = owner.id,
                name = owner.name
            )
        )
    }
}