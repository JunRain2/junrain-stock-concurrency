package com.example.demo.product.command.application

import com.example.demo.global.contract.vo.Money
import com.example.demo.product.command.application.dto.BulkRegisterProductResult
import com.example.demo.product.command.application.dto.ProductBulkRegisterCommand
import com.example.demo.product.command.application.dto.ProductRegisterCommand
import com.example.demo.product.command.application.dto.RegisterProductResult
import com.example.demo.product.command.domain.OwnerValidationService
import com.example.demo.product.command.domain.Product
import com.example.demo.product.command.domain.ProductRepository
import com.example.demo.product.command.domain.vo.ProductCode
import com.example.demo.product.exception.ProductDuplicateCodeException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger { }

@Service
class ProductRegisterService(
    private val productRepository: ProductRepository,
    private val ownerValidationService: OwnerValidationService
) {
    fun registerProduct(command: ProductRegisterCommand): RegisterProductResult {
        ownerValidationService.validateMemberIsSeller(command.ownerId)

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
                throw ProductDuplicateCodeException(it.code)
            }
        }

        return RegisterProductResult(
            productId = product.id
        )
    }

    fun registerProducts(command: ProductBulkRegisterCommand): BulkRegisterProductResult {
        ownerValidationService.validateMemberIsSeller(command.ownerId)

        val failure = mutableListOf<BulkRegisterProductResult.FailedRegisterProduct>()

        val validProducts = command.products.mapNotNull { product ->
            runCatching {
                Product(
                    name = product.name,
                    code = ProductCode(product.code),
                    price = Money.of(product.price),
                    stock = product.stock,
                    ownerId = command.ownerId
                )
            }.onFailure { e ->
                if (e is IllegalArgumentException) {
                    failure.add(
                        BulkRegisterProductResult.FailedRegisterProduct(
                            product.code.takeIf { it.isNotBlank() } ?: "INVALID_CODE",
                            e.message ?: "유효하지 않은 입력값입니다."
                        )
                    )
                }
            }.getOrNull()
        }

        val bulkInsert = productRepository.bulkInsert(validProducts)
        failure.addAll(
            bulkInsert.failed.map {
                when (it.reason) {
                    is DataIntegrityViolationException -> {
                        BulkRegisterProductResult.FailedRegisterProduct(
                            it.item.code.code,
                            "중복된 코드입니다."
                        )
                    }

                    else -> {
                        BulkRegisterProductResult.FailedRegisterProduct(
                            it.item.code.code,
                            "서버에서 문제가 발생했습니다."
                        )
                    }
                }
            }
        )

        return BulkRegisterProductResult(
            successCount = bulkInsert.succeeded.size,
            failureCount = failure.size,
            failedProducts = failure
        )
    }
}
