package com.example.demo.product.command.application

import com.example.demo.global.contract.vo.Money
import com.example.demo.product.command.application.dto.ProductRegisterDto
import com.example.demo.product.command.application.dto.ProductRegisterDto.Result.BulkRegister.FailedRegisterProduct
import com.example.demo.product.command.domain.OwnerValidationService
import com.example.demo.product.command.domain.Product
import com.example.demo.product.command.domain.ProductRepository
import com.example.demo.product.command.domain.ProductValidationService
import com.example.demo.product.command.domain.vo.ProductCode
import com.example.demo.product.exception.ProductCreationException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger { }

@Service
class ProductRegisterService(
    private val productRepository: ProductRepository,
    private val productValidationService: ProductValidationService,
    private val ownerValidationService: OwnerValidationService
) {
    fun registerProducts(command: ProductRegisterDto.Command.BulkRegister): ProductRegisterDto.Result.BulkRegister {
        ownerValidationService.validateMemberIsSeller(command.ownerId)

        val results = buildList {
            val creationResults = command.products.map { productData ->
                runCatching {
                    Product(
                        name = productData.name,
                        code = ProductCode(productData.code),
                        price = Money.of(productData.price),
                        stock = productData.stock,
                        ownerId = command.ownerId
                    )
                }.recoverCatching { e ->
                    throw ProductCreationException(productData.code, e)
                }.also { add(it) }
            }

            val successProducts = creationResults.mapNotNull { it.getOrNull() }

            val validationResults = productValidationService.checkProducts(successProducts)
            addAll(validationResults)  // 검증 결과 추가

            val validatedProducts = validationResults.mapNotNull { it.getOrNull() }
            addAll(productRepository.saveAll(validatedProducts))
        }

        return ProductRegisterDto.Result.BulkRegister(
            successCount = results.count { it.isSuccess },
            failureCount = results.count { it.isFailure },
            failedProducts = results.mapNotNull { it.exceptionOrNull() }
                .filterIsInstance<ProductCreationException>().map {
                    FailedRegisterProduct(
                        code = it.productCode,
                        cause = it.cause?.message,
                    )
                })
    }
}