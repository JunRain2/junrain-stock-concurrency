package com.example.demo.product.command.application

import com.example.demo.global.contract.vo.Money
import com.example.demo.product.command.application.dto.ProductRegisterDto
import com.example.demo.product.command.domain.OwnerValidationService
import com.example.demo.product.command.domain.Product
import com.example.demo.product.command.domain.ProductRepository
import com.example.demo.product.command.domain.vo.ProductCode
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger { }

@Service
class ProductRegisterService(
    private val productRepository: ProductRepository,
    private val ownerValidationService: OwnerValidationService
) {
    fun registerProduct(command: ProductRegisterDto.Command.Register): ProductRegisterDto.Result.Register {
        ownerValidationService.validateMemberIsSeller(command.ownerId)

        val product = Product(
            ownerId = command.ownerId,
            name = command.name,
            code = ProductCode(command.code),
            price = Money.of(command.price),
            stock = command.stock
        ).let { productRepository.save(it) }

        return ProductRegisterDto.Result.Register(
            productId = product.id
        )
    }

    fun registerProducts(command: ProductRegisterDto.Command.BulkRegister): ProductRegisterDto.Result.BulkRegister {
        ownerValidationService.validateMemberIsSeller(command.ownerId)

        val failure = mutableListOf<ProductRegisterDto.Result.BulkRegister.FailedRegisterProduct>()

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
                        ProductRegisterDto.Result.BulkRegister.FailedRegisterProduct(
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
                        ProductRegisterDto.Result.BulkRegister.FailedRegisterProduct(
                            it.item.code.code,
                            "중복된 코드입니다."
                        )
                    }

                    else -> {
                        ProductRegisterDto.Result.BulkRegister.FailedRegisterProduct(
                            it.item.code.code,
                            "서버에서 문제가 발생했습니다."
                        )
                    }
                }
            }
        )

        return ProductRegisterDto.Result.BulkRegister(
            successCount = bulkInsert.succeeded.size,
            failureCount = failure.size,
            failedProducts = failure
        )
    }
}
