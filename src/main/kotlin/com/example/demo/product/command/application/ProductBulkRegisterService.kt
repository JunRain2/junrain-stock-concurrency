package com.example.demo.product.command.application


import com.example.demo.member.domain.MemberRepository
import com.example.demo.member.exception.NotFoundMemberException
import com.example.demo.product.command.application.dto.request.ProductBulkRegisterCommand
import com.example.demo.product.command.application.dto.result.BulkRegisterProductResult
import com.example.demo.product.command.domain.BulkInsertProductRepository
import com.example.demo.product.command.domain.Product
import com.example.demo.product.command.domain.vo.Money
import com.example.demo.product.command.domain.vo.ProductCode
import com.example.demo.product.exception.ProductAccessDeniedException
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.TransientDataAccessException
import org.springframework.stereotype.Service
import java.lang.Thread.sleep

@Service
class ProductBulkRegisterService(
    private val productRepository: BulkInsertProductRepository,
    private val memberRepository: MemberRepository,

    @param:Value("\${bulk-insert.max-size}") private val maxSize: Int,
    @param:Value("\${bulk-insert.chunk-size}") private val chunkSize: Int,
    @param:Value("\${bulk-insert.retry-milliseconds}") private val retryMilliseconds: List<Long>
) {
    /**
     * 요구사항 : 대량의 Products를 삽입
     *
     * 배치 chucked 사이즈는 어떻게 할 것인가?
     *     - 한 트랜잭션에 걸리는 시간을 고려
     *     - 메모리 고려
     * 도중에 실패하면 어떡할 것인가?
     *     - 재시도 전략은 어떡할 것인가? -> N회? 그 이유는?
     *     - 모두 실패할 경우?
     * 재시도 시나리오
     *     - 사용자에 의한 실패 -> 비즈니스 로직에 맞지 않은 입력값(중복)
     *         - 1차 애플리케이션, 2차 DB(All or Noting이 아니라 실패한 row를 무시할 수 있기 때문)
     *     - 인프라 문제로 인한 실패 -> DB에 연결이 안됨 (일시적 or Down)
     *         - 커넥션 풀이 일시적으로 고갈됐을 경우
     *         - DB가 다운됐을 경우
     *  위 모든 것을 고려한 이후, 다중환경에서는 어떡할 것인가? -> DB의 HotSpot 문제
     *
     *  데이터를 병렬 처리해도 문제가 크게 안생길 것 같음
     */
    fun registerProducts(command: ProductBulkRegisterCommand): BulkRegisterProductResult {
        val products = command.products
        require(products.size <= maxSize) { "상품의 수가 ${maxSize}를 초과했습니다." }

        val owner = memberRepository.findById(command.ownerId)
            .orElseThrow { NotFoundMemberException() }
        if (!owner.isSeller()) {
            throw ProductAccessDeniedException()
        }

        val failed = mutableListOf<BulkRegisterProductResult.FailedRegisterProduct>()
        var retry = mutableListOf<Product>()

        products.chunked(chunkSize).forEach { chunk ->
            // 입력값 검증, 실패한 값을 추출
            val validProducts = chunk.mapNotNull { product ->
                runCatching {
                    Product(
                        name = product.name,
                        code = ProductCode(product.code),
                        price = Money.of(product.price),
                        stock = product.stock,
                        ownerId = owner.id
                    )
                }.onFailure { e ->
                    if (e is IllegalArgumentException) {
                        failed.add(
                            generateFailedRegisterProduct(
                                product,
                                e.message ?: "유효하지 않은 입력값입니다."
                            )
                        )
                    }
                }.getOrNull()
            }

            // 저장
            try {
                failed += productRepository.saveAllAndReturnFailed(validProducts)
                    .map {
                        generateFailedRegisterProduct(
                            it,
                            "중복된 상품 코드입니다. (코드: ${it.code.code})"
                        )
                    }
            } catch (e: TransientDataAccessException) { // DB에서 발생한 일시적인 장애
                retry += validProducts
            }
        }

        // 재시도
        for (millis in retryMilliseconds) {
            if (retry.isEmpty()) {
                break
            }
            sleep(millis)


            retry = retry.chunked(chunkSize).flatMap { chunk ->
                runCatching {
                    failed += productRepository.saveAllAndReturnFailed(chunk)
                        .map {
                            generateFailedRegisterProduct(
                                it,
                                "중복된 상품 코드입니다. (코드: ${it.code.code})"
                            )
                        }
                    emptyList<Product>() // 성공 시 빈 리스트
                }.recover { e ->
                    if (e is TransientDataAccessException) {
                        chunk
                    } else {
                        failed += chunk.map {
                            generateFailedRegisterProduct(it, e.message ?: "예상치 못한 오류가 발생했습니다.")
                        }
                        emptyList()
                    }
                }.getOrThrow()
            }.toMutableList()
        }

        // 재시도 실패한 데이터를 삽입
        failed += retry.map {
            generateFailedRegisterProduct(it, "서버 장애로인해 데이터를 저장하지 못했습니다.")
        }

        return BulkRegisterProductResult(
            successCount = products.size - failed.size,
            failureCount = failed.size,
            failedProducts = failed
        )
    }

    private fun generateFailedRegisterProduct(
        product: ProductBulkRegisterCommand.RegisterProduct,
        message: String
    ): BulkRegisterProductResult.FailedRegisterProduct {
        return BulkRegisterProductResult.FailedRegisterProduct(
            name = product.name,
            price = product.price,
            stock = product.stock,
            message = message
        )
    }

    private fun generateFailedRegisterProduct(
        product: Product,
        message: String
    ): BulkRegisterProductResult.FailedRegisterProduct {
        return BulkRegisterProductResult.FailedRegisterProduct(
            name = product.name,
            price = product.price.amount.toLong(),
            stock = product.stock,
            message = message
        )
    }
}