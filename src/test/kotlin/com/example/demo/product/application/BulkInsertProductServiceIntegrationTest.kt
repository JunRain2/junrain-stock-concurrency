package com.example.demo.product.application

import com.example.demo.member.command.domain.Member
import com.example.demo.member.command.domain.MemberRepository
import com.example.demo.member.command.domain.MemberType
import com.example.demo.product.command.application.ProductBulkRegisterService
import com.example.demo.product.command.application.dto.ProductBulkRegisterCommand
import com.example.demo.product.command.domain.ProductRepository
import com.example.demo.product.command.ui.dto.BulkRegisterProductRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
class BulkInsertProductServiceIntegrationTest {

    @Autowired
    private lateinit var bulkInsertProductService: ProductBulkRegisterService

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    private lateinit var testMember: Member

    @BeforeEach
    fun setUp() {
        productRepository.deleteAll()
        memberRepository.deleteAll()
        testMember = memberRepository.save(Member(memberType = MemberType.SELLER, name = "Test Seller"))
    }

    @AfterEach
    fun tearDown() {
        productRepository.deleteAll()
        memberRepository.deleteAll()
    }

    @Test
    fun `should register all products successfully with real database`() {
        // given
        val products = createValidProducts(15)
        val request = BulkRegisterProductRequest(products)
        val command = ProductBulkRegisterCommand.of(testMember.id, request)

        // when
        val result = bulkInsertProductService.registerProducts(command)

        // then
        assertEquals(15, result.successCount)
        assertEquals(0, result.failureCount)
        assertTrue(result.failedProducts.isEmpty())

        // DB 확인
        val savedProducts = productRepository.findAll()
        assertEquals(15, savedProducts.size)
    }

    @Test
    fun `should handle duplicate product codes with INSERT IGNORE`() {
        // given: 먼저 5개 상품 등록
        val firstBatch = createValidProducts(5)
        bulkInsertProductService.registerProducts(ProductBulkRegisterCommand.of(testMember.id, BulkRegisterProductRequest(firstBatch)))

        // when: 동일한 코드를 포함한 10개 상품 재등록 시도 (처음 5개는 중복)
        val secondBatch = createValidProducts(10)
        val result = bulkInsertProductService.registerProducts(ProductBulkRegisterCommand.of(testMember.id, BulkRegisterProductRequest(secondBatch)))

        // then
        assertEquals(5, result.successCount) // 6~10번만 성공
        assertEquals(5, result.failureCount) // 1~5번은 중복으로 실패
        assertEquals(5, result.failedProducts.size)

        // DB에는 총 10개만 있어야 함
        val savedProducts = productRepository.findAll()
        assertEquals(10, savedProducts.size)
    }

    @Test
    fun `should not save products that fail business validation`() {
        // given
        val products = listOf(
            BulkRegisterProductRequest.RegisterProduct(
                name = "",
                price = 1000L,
                stock = 10L,
                code = "P001"
            ),
            BulkRegisterProductRequest.RegisterProduct(
                name = "상품명이너무길어서스무자를초과합니다매우길죠",
                price = 2000L,
                stock = 20L,
                code = "P002"
            ),
            BulkRegisterProductRequest.RegisterProduct(
                name = "정상상품",
                price = 3000L,
                stock = 30L,
                code = "P003"
            )
        )

        // when
        val result = bulkInsertProductService.registerProducts(ProductBulkRegisterCommand.of(testMember.id, BulkRegisterProductRequest(products)))

        // then
        assertEquals(1, result.successCount)
        assertEquals(2, result.failureCount)
        assertTrue(result.failedProducts.any { failedProduct -> failedProduct.message == "상품명은 필수입니다" })
        assertTrue(result.failedProducts.any { failedProduct -> failedProduct.message == "상품명은 20자 이하여야 합니다" })

        // DB에는 1개만 저장
        val savedProducts = productRepository.findAll()
        assertEquals(1, savedProducts.size)
        assertEquals("정상상품", savedProducts.first().name)
    }

    @Test
    fun `should process products in chunks correctly`() {
        // given: 25개 상품 (3개 청크: 10 + 10 + 5)
        val products = createValidProducts(25)

        // when
        val result = bulkInsertProductService.registerProducts(ProductBulkRegisterCommand.of(testMember.id, BulkRegisterProductRequest(products)))

        // then
        assertEquals(25, result.successCount)
        assertEquals(0, result.failureCount)

        // DB 확인
        val savedProducts = productRepository.findAll()
        assertEquals(25, savedProducts.size)
    }

    @Test
    fun `should handle mixed scenario with validation failures, duplicates, and successes`() {
        // given: 먼저 일부 상품 등록
        val existingProducts = listOf(
            BulkRegisterProductRequest.RegisterProduct(
                name = "기존상품1",
                price = 1000L,
                stock = 10L,
                code = "P001"
            ),
            BulkRegisterProductRequest.RegisterProduct(
                name = "기존상품2",
                price = 2000L,
                stock = 20L,
                code = "P002"
            )
        )
        bulkInsertProductService.registerProducts(ProductBulkRegisterCommand.of(testMember.id, BulkRegisterProductRequest(existingProducts)))

        // when: 검증 실패 + 중복 + 성공 포함
        val newProducts = listOf(
            // 검증 실패
            BulkRegisterProductRequest.RegisterProduct("", 1000L, 10L, "P010"),
            // 중복
            BulkRegisterProductRequest.RegisterProduct("중복상품", 1000L, 10L, "P001"),
            // 성공
            BulkRegisterProductRequest.RegisterProduct("신규상품1", 3000L, 30L, "P003"),
            BulkRegisterProductRequest.RegisterProduct("신규상품2", 4000L, 40L, "P004"),
            BulkRegisterProductRequest.RegisterProduct("신규상품3", 5000L, 50L, "P005")
        )

        val result = bulkInsertProductService.registerProducts(ProductBulkRegisterCommand.of(testMember.id, BulkRegisterProductRequest(newProducts)))

        // then
        assertEquals(3, result.successCount) // P003, P004, P005
        assertEquals(2, result.failureCount) // 검증 실패 1개 + 중복 1개
        assertTrue(result.failedProducts.any { failedProduct -> failedProduct.message == "상품명은 필수입니다" })
        assertTrue(result.failedProducts.any { failedProduct -> failedProduct.message.contains("중복된 상품 코드입니다") })

        // DB에는 총 5개 (기존 2개 + 신규 3개)
        val savedProducts = productRepository.findAll()
        assertEquals(5, savedProducts.size)
    }

    @Test
    fun `should handle empty product list`() {
        // given
        val products = emptyList<BulkRegisterProductRequest.RegisterProduct>()

        // when
        val result = bulkInsertProductService.registerProducts(ProductBulkRegisterCommand.of(testMember.id, BulkRegisterProductRequest(products)))

        // then
        assertEquals(0, result.successCount)
        assertEquals(0, result.failureCount)
        assertTrue(result.failedProducts.isEmpty())

        // DB 확인
        val savedProducts = productRepository.findAll()
        assertEquals(0, savedProducts.size)
    }

    @Test
    fun `should handle bulk insert of 100 products`() {
        // given
        val products = createValidProducts(100)

        // when
        val result = bulkInsertProductService.registerProducts(ProductBulkRegisterCommand.of(testMember.id, BulkRegisterProductRequest(products)))

        // then
        assertEquals(100, result.successCount)
        assertEquals(0, result.failureCount)

        // DB 확인
        val savedProducts = productRepository.findAll()
        assertEquals(100, savedProducts.size)
    }

    @Test
    fun `should fail when price is negative`() {
        // given
        val products = listOf(
            BulkRegisterProductRequest.RegisterProduct(
                name = "상품1",
                price = -1000L,
                stock = 10L,
                code = "P001"
            )
        )

        // when
        val result = bulkInsertProductService.registerProducts(ProductBulkRegisterCommand.of(testMember.id, BulkRegisterProductRequest(products)))

        // then
        assertEquals(0, result.successCount)
        assertEquals(1, result.failureCount)
        assertEquals("Money value cannot be negative", result.failedProducts[0].message)

        // DB에 저장되지 않음
        val savedProducts = productRepository.findAll()
        assertEquals(0, savedProducts.size)
    }

    @Test
    fun `should fail when stock is negative`() {
        // given
        val products = listOf(
            BulkRegisterProductRequest.RegisterProduct(
                name = "상품1",
                price = 1000L,
                stock = -10L,
                code = "P001"
            )
        )

        // when
        val result = bulkInsertProductService.registerProducts(ProductBulkRegisterCommand.of(testMember.id, BulkRegisterProductRequest(products)))

        // then
        assertEquals(0, result.successCount)
        assertEquals(1, result.failureCount)
        assertEquals("상품재고는 0개 이상이어야 합니다", result.failedProducts[0].message)

        // DB에 저장되지 않음
        val savedProducts = productRepository.findAll()
        assertEquals(0, savedProducts.size)
    }

    @Test
    fun `should fail when product name contains special characters`() {
        // given
        val products = listOf(
            BulkRegisterProductRequest.RegisterProduct(
                name = "상품@#$",
                price = 1000L,
                stock = 10L,
                code = "P001"
            )
        )

        // when
        val result = bulkInsertProductService.registerProducts(ProductBulkRegisterCommand.of(testMember.id, BulkRegisterProductRequest(products)))

        // then
        assertEquals(0, result.successCount)
        assertEquals(1, result.failureCount)
        assertEquals("상품명은 특수문자를 포함할 수 없습니다", result.failedProducts[0].message)

        // DB에 저장되지 않음
        val savedProducts = productRepository.findAll()
        assertEquals(0, savedProducts.size)
    }

    // 헬퍼 메서드
    private fun createValidProducts(count: Int): List<BulkRegisterProductRequest.RegisterProduct> {
        return (1..count).map {
            BulkRegisterProductRequest.RegisterProduct(
                name = "상품$it",
                price = (it * 1000).toLong(),
                stock = (it * 10).toLong(),
                code = "P${String.format("%03d", it)}"
            )
        }
    }
}
