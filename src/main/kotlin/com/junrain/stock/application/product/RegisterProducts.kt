package com.junrain.stock.application.product

import com.junrain.stock.domain.common.Money
import com.junrain.stock.domain.product.OwnerValidationService
import com.junrain.stock.domain.product.Product
import com.junrain.stock.domain.product.ProductCodeUniqueness
import com.junrain.stock.domain.product.ProductRepository
import com.junrain.stock.domain.product.exception.ProductCreationException
import com.junrain.stock.domain.product.exception.ProductDuplicateCodeException
import com.junrain.stock.domain.product.vo.ProductCode
import org.springframework.stereotype.Service

@Service
class RegisterProducts(
    private val productRepository: ProductRepository,
    private val ownerValidationService: OwnerValidationService,
) {
    operator fun invoke(command: Command): Result {
        ownerValidationService.validateMemberIsSeller(command.ownerId)

        return command.products
            .map { runCatching { createProduct(command.ownerId, it) } }
            .let { ProductCodeUniqueness.ensure(it) }
            .let { validatedProducts ->
                val validProducts = validatedProducts.mapNotNull { it.getOrNull() }

                productRepository.saveAll(validProducts).let { saveResults ->
                    val successResultsByCode =
                        saveResults
                            .mapNotNull { it.getOrNull() }
                            .associateBy({ it.code }, { kotlin.Result.success(it) })

                    val failureResultsByCode =
                        saveResults
                            .filter { it.isFailure }
                            .mapNotNull { result ->
                                when (val exception = result.exceptionOrNull()) {
                                    is ProductCreationException -> {
                                        exception.code to result
                                    }

                                    is ProductDuplicateCodeException -> {
                                        exception.code to result
                                    }

                                    else -> {
                                        null
                                    }
                                }
                            }.toMap()

                    val resultsByCode = failureResultsByCode + successResultsByCode

                    validatedProducts.withIndex().associate { (index, validationResult) ->
                        index to
                            when {
                                validationResult.isFailure -> {
                                    validationResult
                                }

                                else -> {
                                    val product = validationResult.getOrThrow()
                                    resultsByCode[product.code]
                                        ?: kotlin.Result.failure(IllegalStateException("DB에서 예외가 발생했습니다: ${product.code}"))
                                }
                            }
                    }
                }
            }.let { buildResult(it) }
    }

    private fun createProduct(
        ownerId: Long,
        product: Command.RegisterProduct,
    ) = Product(
        ownerId = ownerId,
        code = ProductCode(product.code),
        stock = product.stock,
        price = Money.of(product.price),
        name = product.name,
    )

    private fun buildResult(results: Map<Int, kotlin.Result<Product>>) =
        Result(
            successCount = results.count { it.value.isSuccess },
            failureCount = results.count { it.value.isFailure },
            failedProducts =
                results
                    .filter { it.value.isFailure }
                    .map { (index, result) ->
                        Result.FailedRegisterProduct(
                            index = index,
                            cause = result.exceptionOrNull()?.message ?: "Unknown error",
                        )
                    }.sortedBy { it.index }
                    .toList(),
        )

    data class Command(
        val ownerId: Long,
        val products: List<RegisterProduct>,
    ) {
        init {
            require(products.size in (1..CHUNK_MAX_SIZE)) { "데이터는 하나 이상 5000개 이하여야 합니다." }
        }

        data class RegisterProduct(
            val name: String,
            val price: Long,
            val stock: Long,
            val code: String,
        )
    }

    data class Result(
        val successCount: Int,
        val failureCount: Int,
        val failedProducts: List<FailedRegisterProduct>,
    ) {
        data class FailedRegisterProduct(
            val index: Int,
            val cause: String,
        )
    }

    companion object {
        const val CHUNK_MAX_SIZE = 5000
    }
}
