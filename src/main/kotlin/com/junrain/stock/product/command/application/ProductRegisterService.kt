package com.junrain.stock.product.command.application

import com.junrain.stock.contract.vo.Money
import com.junrain.stock.product.command.application.dto.ProductRegisterDto
import com.junrain.stock.product.command.domain.OwnerValidationService
import com.junrain.stock.product.command.domain.Product
import com.junrain.stock.product.command.domain.ProductRepository
import com.junrain.stock.product.command.domain.ProductCodeUniquenessService
import com.junrain.stock.product.command.domain.vo.ProductCode
import com.junrain.stock.product.exception.ProductCreationException
import com.junrain.stock.product.exception.ProductDuplicateCodeException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger { }

@Service
class ProductRegisterService(
    private val productRepository: ProductRepository,
    private val productCodeUniquenessService: ProductCodeUniquenessService,
    private val ownerValidationService: OwnerValidationService
) {
    fun registerProducts(command: ProductRegisterDto.Command.BulkRegister): ProductRegisterDto.Result.BulkRegister {
        ownerValidationService.validateMemberIsSeller(command.ownerId)

        return command.products
            .map { runCatching { createProduct(command.ownerId, it) } }
            .let { productCodeUniquenessService.ensureProductCodeUniqueness(it) }
            .let { validatedProducts ->
                val validProducts = validatedProducts.mapNotNull { it.getOrNull() }

                productRepository.saveAll(validProducts).let { saveResults ->
                    val successResultsByCode = saveResults.mapNotNull { it.getOrNull() }
                        .associateBy({ it.code }, { Result.success(it) })

                    val failureResultsByCode = saveResults
                        .filter { it.isFailure }
                        .mapNotNull { result ->
                            when (val exception = result.exceptionOrNull()) {
                                is ProductCreationException ->
                                    exception.code to result

                                is ProductDuplicateCodeException ->
                                    exception.code to result

                                else -> null
                            }
                        }
                        .toMap()

                    val resultsByCode = failureResultsByCode + successResultsByCode

                    validatedProducts.withIndex().associate { (index, validationResult) ->
                        index to when {
                            validationResult.isFailure -> validationResult
                            else -> {
                                val product = validationResult.getOrThrow()
                                resultsByCode[product.code]
                                    ?: Result.failure(IllegalStateException("DB에서 예외가 발생했습니다: ${product.code}"))
                            }
                        }
                    }
                }
            }
            .let { buildResponse(it) }
    }

    private fun createProduct(
        ownerId: Long,
        dto: ProductRegisterDto.Command.BulkRegister.RegisterProduct
    ) =
        Product(
            ownerId = ownerId,
            code = ProductCode(dto.code),
            stock = dto.stock,
            price = Money.of(dto.price),
            name = dto.name
        )

    private fun buildResponse(results: Map<Int, Result<Product>>) =
        ProductRegisterDto.Result.BulkRegister(
            successCount = results.count { it.value.isSuccess },
            failureCount = results.count { it.value.isFailure },
            failedProducts = results
                .filter { it.value.isFailure }
                .map { (index, result) ->
                    ProductRegisterDto.Result.BulkRegister.FailedRegisterProduct(
                        index = index,
                        cause = result.exceptionOrNull()?.message ?: "Unknown error"
                    )
                }
                .sortedBy { it.index }
                .toList()
        )
}
