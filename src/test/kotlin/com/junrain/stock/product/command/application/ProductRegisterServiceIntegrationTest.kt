package com.junrain.stock.product.command.application

import com.junrain.stock.member.command.domain.Member
import com.junrain.stock.member.command.domain.MemberRepository
import com.junrain.stock.member.command.domain.MemberType
import com.junrain.stock.product.command.application.dto.ProductRegisterDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger { }

@SpringBootTest
@Transactional
class ProductRegisterServiceIntegrationTest {

    @Autowired
    private lateinit var productRegisterService: ProductRegisterService

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var seller: Member

    @BeforeEach
    fun setUp() {
        // Seller 생성
        seller = memberRepository.save(
            Member(
                memberType = MemberType.SELLER,
                name = "Test Seller"
            )
        )
    }

    @AfterEach
    fun tearDown() {
        jdbcTemplate.execute("DELETE FROM products")
        jdbcTemplate.execute("DELETE FROM members")
    }

    @Test
    fun `모든 상품이 정상적으로 등록되는 경우`() {
        // given
        val command = ProductRegisterDto.Command.BulkRegister(
            ownerId = seller.id,
            products = listOf(
                createProduct("CODE001", "상품1", 10000, 100),
                createProduct("CODE002", "상품2", 20000, 200),
                createProduct("CODE003", "상품3", 30000, 300)
            )
        )

        // when
        val result = productRegisterService.registerProducts(command)

        // then
        assertEquals(3, result.successCount)
        assertEquals(0, result.failureCount)
        assertTrue(result.failedProducts.isEmpty())
    }

    @Test
    fun `검증 실패가 발생한 경우 - 순서 보장 확인`() {
        // given
        val command = ProductRegisterDto.Command.BulkRegister(
            ownerId = seller.id,
            products = listOf(
                createProduct("CODE001", "상품1", 10000, 100),  // 0: 성공
                createProduct("CODE002", "", 20000, 200),       // 1: 실패 (이름 없음)
                createProduct("CODE003", "상품3", 30000, 300),  // 2: 성공
                createProduct(
                    "CODE004",
                    "이 상품명은 20자를 초과하는 매우 긴 이름입니다",
                    40000,
                    400
                ), // 3: 실패 (이름 길이 초과)
                createProduct("CODE005", "상품5", 50000, 500)   // 4: 성공
            )
        )

        // when
        val result = productRegisterService.registerProducts(command)

        // then
        assertEquals(3, result.successCount)
        assertEquals(2, result.failureCount)
        assertEquals(2, result.failedProducts.size)

        // 실패한 인덱스 확인 (순서 보장)
        val failedIndices = result.failedProducts.map { it.index }
        assertEquals(listOf(1, 3), failedIndices)

        // 실패 원인 확인
        assertTrue(result.failedProducts[0].cause.contains("상품명"))
        assertTrue(result.failedProducts[1].cause.contains("20자"))
    }

    @Test
    fun `중복 ProductCode 검증 - 순서 보장 확인`() {
        // given
        val command = ProductRegisterDto.Command.BulkRegister(
            ownerId = seller.id,
            products = listOf(
                createProduct("CODE001", "상품1", 10000, 100),  // 0: 실패 (중복)
                createProduct("CODE002", "상품2", 20000, 200),  // 1: 실패 (중복)
                createProduct("CODE001", "상품3", 30000, 300),  // 2: 실패 (중복)
                createProduct("CODE003", "상품4", 40000, 400),  // 3: 성공
                createProduct("CODE002", "상품5", 50000, 500)   // 4: 실패 (중복)
            )
        )

        // when
        val result = productRegisterService.registerProducts(command)

        // then
        assertEquals(1, result.successCount)  // CODE003만 성공
        assertEquals(4, result.failureCount)  // 나머지 모두 실패

        // 중복으로 실패한 인덱스 확인
        val failedIndices = result.failedProducts.map { it.index }
        assertEquals(listOf(0, 1, 2, 4), failedIndices)

        // 실패 원인 확인 - ProductDuplicateCodeException이 발생했는지 확인
        result.failedProducts.forEach { failed ->
            assertTrue(failed.cause.isNotEmpty())
        }
    }

    @Test
    fun `DB 중복 제약조건 위반 - 순서 보장 확인`() {
        // given - 먼저 하나 저장
        productRegisterService.registerProducts(
            ProductRegisterDto.Command.BulkRegister(
                ownerId = seller.id,
                products = listOf(createProduct("CODE001", "기존상품", 10000, 100))
            )
        )

        // when - 같은 코드로 다시 등록 시도
        val command = ProductRegisterDto.Command.BulkRegister(
            ownerId = seller.id,
            products = listOf(
                createProduct("CODE002", "상품2", 20000, 200),  // 0: 성공
                createProduct("CODE001", "중복상품", 30000, 300), // 1: DB 실패
                createProduct("CODE003", "상품3", 40000, 400)   // 2: 성공
            )
        )

        val result = productRegisterService.registerProducts(command)

        // then
        assertEquals(2, result.successCount)
        assertEquals(1, result.failureCount)
        assertEquals(1, result.failedProducts[0].index) // 1번 인덱스 실패
    }

    @Test
    fun `대량 등록 - 5000건 순서 보장 확인`() {
        // given
        val products = (1..5000).map { i ->
            createProduct("CODE${i.toString().padStart(5, '0')}", "상품$i", 10000L, 100L)
        }

        val command = ProductRegisterDto.Command.BulkRegister(
            ownerId = seller.id,
            products = products
        )

        // when
        val result = productRegisterService.registerProducts(command)

        // then
        assertEquals(5000, result.successCount)
        assertEquals(0, result.failureCount)
    }

    @Test
    fun `대량 등록 중 일부 실패 - 순서 보장 확인`() {
        // given
        val products = (1..100).map { i ->
            when {
                i % 10 == 0 -> createProduct(
                    "CODE${i.toString().padStart(5, '0')}",
                    "",
                    10000L,
                    100L
                ) // 매 10번째 실패
                else -> createProduct("CODE${i.toString().padStart(5, '0')}", "상품$i", 10000L, 100L)
            }
        }

        val command = ProductRegisterDto.Command.BulkRegister(
            ownerId = seller.id,
            products = products
        )

        // when
        val result = productRegisterService.registerProducts(command)

        // then
        assertEquals(90, result.successCount)
        assertEquals(10, result.failureCount)

        // 실패한 인덱스 확인 (10, 20, 30, ..., 100)
        val expectedFailedIndices = (1..10).map { it * 10 - 1 } // 0-based이므로 -1
        val actualFailedIndices = result.failedProducts.map { it.index }
        assertEquals(expectedFailedIndices, actualFailedIndices)
    }

    @Test
    fun `부분 성공 - 다양한 실패 케이스 혼합`() {
        // given
        // 먼저 하나 저장 (DB 중복 테스트용)
        productRegisterService.registerProducts(
            ProductRegisterDto.Command.BulkRegister(
                ownerId = seller.id,
                products = listOf(createProduct("EXISTING", "기존상품", 10000, 100))
            )
        )

        val command = ProductRegisterDto.Command.BulkRegister(
            ownerId = seller.id,
            products = listOf(
                createProduct("CODE001", "상품1", 10000, 100),      // 0: 실패 (요청 내 중복)
                createProduct("CODE002", "", 20000, 200),           // 1: 검증 실패 (빈 이름)
                createProduct("CODE003", "상품3", 30000, 300),      // 2: 성공
                createProduct("CODE001", "중복상품", 40000, 400),   // 3: 실패 (요청 내 중복)
                createProduct("EXISTING", "기존상품중복", 50000, 500), // 4: DB 중복
                createProduct("CODE004", "상품4", 60000, 600),      // 5: 성공
                createProduct("CODE005", "이 상품명은 20자를 초과하는 매우 긴 이름입니다", 70000, 700), // 6: 검증 실패 (긴 이름)
                createProduct("CODE006", "상품6", 80000, 800)       // 7: 성공
            )
        )

        // when
        val result = productRegisterService.registerProducts(command)
        logger.info { result }

        // then
        assertEquals(3, result.successCount)  // CODE003, CODE004, CODE006
        assertEquals(5, result.failureCount)  // 나머지

        // 실패 인덱스: 0, 1, 3, 4, 6
        val failedIndices = result.failedProducts.map { it.index }
        assertEquals(listOf(0, 1, 3, 4, 6), failedIndices)
    }

    private fun createProduct(
        code: String,
        name: String,
        price: Long,
        stock: Long
    ): ProductRegisterDto.Command.BulkRegister.RegisterProduct {
        return ProductRegisterDto.Command.BulkRegister.RegisterProduct(
            name = name,
            price = price,
            stock = stock,
            code = code
        )
    }
}
