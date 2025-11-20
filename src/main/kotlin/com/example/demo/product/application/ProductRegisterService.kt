package com.example.demo.product.application

import com.example.demo.member.domain.MemberRepository
import com.example.demo.member.exception.NotFoundMemberException
import com.example.demo.product.application.dto.command.ProductRegisterCommand
import com.example.demo.product.domain.product.Product
import com.example.demo.product.domain.product.ProductRepository
import com.example.demo.product.domain.product.vo.Money
import com.example.demo.product.domain.product.vo.ProductCode
import com.example.demo.product.exception.DuplicateProductCodeException
import com.example.demo.product.exception.ProductAccessDeniedException
import com.example.demo.product.ui.dto.response.RegisterProductResponse
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

@Service
class ProductRegisterService(
    private val productRepository: ProductRepository,
    private val memberRepository: MemberRepository
) {
    fun registerProduct(command: ProductRegisterCommand): RegisterProductResponse {
        val owner = memberRepository.findById(command.ownerId)
            .orElseThrow { NotFoundMemberException() }
        if (!owner.isSeller()) {
            throw ProductAccessDeniedException()
        }

        val product = Product(
            ownerId = command.ownerId,
            name = command.name,
            code = ProductCode(command.code),
            price = Money.of(command.price),
            stock = command.stock
        ).let {
            try {
                productRepository.save(it)
            } catch (e: DataIntegrityViolationException) {
                throw DuplicateProductCodeException()
            }
        }

        return RegisterProductResponse(
            productid = product.id
        )
    }
}