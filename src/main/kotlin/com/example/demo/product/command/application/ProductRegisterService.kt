package com.example.demo.product.command.application

import com.example.demo.global.contract.exception.DuplicateProductCodeException
import com.example.demo.global.contract.vo.Money
import com.example.demo.product.command.application.dto.ProductRegisterCommand
import com.example.demo.product.command.application.dto.RegisterProductResult
import com.example.demo.product.command.domain.Product
import com.example.demo.product.command.domain.ProductRepository
import com.example.demo.product.command.domain.SellerVerificationService
import com.example.demo.product.command.domain.vo.ProductCode
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

@Service
class ProductRegisterService(
    private val productRepository: ProductRepository,
    private val sellerVerificationService: SellerVerificationService
) {
    fun registerProduct(command: ProductRegisterCommand): RegisterProductResult {
        sellerVerificationService.validateMemberIsSeller(command.ownerId)

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

        return RegisterProductResult(
            productId = product.id
        )
    }
}