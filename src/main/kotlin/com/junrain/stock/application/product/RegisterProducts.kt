package com.junrain.stock.application.product

import com.junrain.stock.domain.common.ErrorCode
import com.junrain.stock.domain.common.Money
import com.junrain.stock.domain.product.OwnerValidationService
import com.junrain.stock.domain.product.Product
import com.junrain.stock.domain.product.ProductRepository
import com.junrain.stock.domain.product.exception.ProductDuplicateCodeException
import com.junrain.stock.domain.product.vo.ProductCode
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class RegisterProducts(
    private val productRepository: ProductRepository,
    private val ownerValidationService: OwnerValidationService,
    @param:Value("\${bulk-insert.max-size}") private val maxSize: Int,
) {
    operator fun invoke(command: Command): Result {
        require(command.products.size in 1..maxSize) { "상품은 1개 이상 ${maxSize}개 이하여야 합니다" }
        ownerValidationService.validateMemberIsSeller(command.ownerId)

        val validated = command.products.map { runCatching { createProduct(command.ownerId, it) } }.let { failDuplicatedCodes(it) }

        // 삽입 대상만 추리되 원래 위치를 같이 들고 간다. saveAll이 입력과 같은 순서로 돌려주므로 zip이면 복원이 끝난다
        val insertable = validated.withIndex().filter { it.value.isSuccess }
        val saved = productRepository.saveAll(insertable.map { it.value.getOrThrow() })

        val failures =
            validated.withIndex().mapNotNull { (index, result) ->
                result.exceptionOrNull()?.let { index to it }
            } +
                insertable.zip(saved).mapNotNull { (indexed, result) ->
                    result.exceptionOrNull()?.let { indexed.index to it }
                }

        return buildResult(total = command.products.size, failures = failures)
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

    /** 요청 안에서 겹친 코드는 어느 쪽이 사용자 의도인지 알 수 없어 겹친 행을 전부 실패시킨다 */
    private fun failDuplicatedCodes(products: List<kotlin.Result<Product>>): List<kotlin.Result<Product>> {
        val duplicated =
            products
                .mapNotNull { it.getOrNull() }
                .groupingBy { it.code }
                .eachCount()
                .filterValues { it > 1 }
                .keys

        return products.map { result ->
            result.mapCatching { product ->
                if (product.code in duplicated) throw ProductDuplicateCodeException(product.code)
                product
            }
        }
    }

    private fun buildResult(
        total: Int,
        failures: List<Pair<Int, Throwable>>,
    ) = Result(
        successCount = total - failures.size,
        failureCount = failures.size,
        failedProducts =
            failures.sortedBy { (index, _) -> index }.map { (index, cause) ->
                Result.FailedRegisterProduct(
                    index = index,
                    cause = cause.message ?: ErrorCode.COMMON_INTERNAL_ERROR.message,
                )
            },
    )

    data class Command(
        val ownerId: Long,
        val products: List<RegisterProduct>,
    ) {
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
}
