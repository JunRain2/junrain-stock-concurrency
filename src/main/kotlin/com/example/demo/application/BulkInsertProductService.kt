package com.example.demo.application

import com.example.demo.domain.product.Product
import com.example.demo.domain.product.ProductRepository
import com.example.demo.domain.product.vo.Money
import com.example.demo.domain.product.vo.ProductCode
import com.example.demo.ui.dto.request.BulkRegisterProductRequest
import com.example.demo.ui.dto.response.BulkRegisterProductResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.TransientDataAccessException
import org.springframework.stereotype.Service
import java.lang.Thread.sleep

@Service
class BulkInsertProductService(
    private val productRepository: ProductRepository,
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
    fun registerProducts(request: BulkRegisterProductRequest): BulkRegisterProductResponse {
        val products = request.products

        val failedProducts = mutableListOf<BulkRegisterProductResponse.FailedRegisterProduct>()
        var retryProducts = mutableListOf<Product>()

        products.chunked(chunkSize).forEach {
            // 엔티티 생성 과정에서 비즈니스 로직에서 1차 거름
            val validProducts = validateProducts(it, failedProducts)

            // DB에 저장 실패한 값
            try {
                failedProducts.addAll(productRepository.saveAllAndReturnFailed(validProducts))
            } catch (e: TransientDataAccessException) {
                retryProducts.addAll(validProducts)
            }
        }

        // 재시도
        for (millis in retryMilliseconds) {
            if (retryProducts.isEmpty()) {
                break
            }
            sleep(millis)

            val tmp = mutableListOf<Product>()
            retryProducts.chunked(chunkSize).forEach {
                try {
                    failedProducts.addAll(productRepository.saveAllAndReturnFailed(it))
                } catch (e: TransientDataAccessException) {
                    tmp.addAll(it)
                }
            }
            retryProducts = tmp
        }

        // 재시도 실패한 데이터를 삽입
        failedProducts.addAll(retryProducts.map {
            BulkRegisterProductResponse.FailedRegisterProduct(
                name = it.name,
                price = it.price.amount.toLong(),
                stock = it.stock,
                message = "서버 장애로인해 데이터를 저장하지 못했습니다."
            )
        })

        return BulkRegisterProductResponse(
            successCount = products.size - failedProducts.size,
            failureCount = failedProducts.size,
            failedProducts = failedProducts
        )
    }


    private fun validateProducts(
        chunk: List<BulkRegisterProductRequest.RegisterProduct>,
        failureProducts: MutableList<BulkRegisterProductResponse.FailedRegisterProduct>
    ): List<Product> {
        // 전부 Product로 만듦으로써 유효성 검사를 진행
        val products = chunk.mapNotNull {
            try {
                Product(
                    name = it.name,
                    code = ProductCode(it.code),
                    price = Money.of(it.price),
                    stock = it.stock
                )
            } catch (e: IllegalArgumentException) {
                failureProducts.add(
                    BulkRegisterProductResponse.FailedRegisterProduct(
                        name = it.name,
                        price = it.price,
                        stock = it.stock,
                        message = e.message ?: "유효하지 않은 입력 값입니다."
                    )
                )
                null
            }
        }

        return products
    }
}