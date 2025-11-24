package com.example.demo.product.infrastructure.jdbc

import com.example.demo.member.domain.Member
import com.example.demo.member.domain.MemberRepository
import com.example.demo.member.domain.MemberType
import com.example.demo.product.command.domain.Product
import com.example.demo.product.command.domain.ProductRepository
import com.example.demo.product.command.domain.vo.Money
import com.example.demo.product.command.domain.vo.ProductCode
import com.example.demo.product.command.infrastructure.jdbc.JdbcBulkInsertProductRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals

@SpringBootTest
class JdbcBulkInsertProductRepositoryTest {

    @Autowired
    private lateinit var repository: JdbcBulkInsertProductRepository

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    private lateinit var testMember: Member

    @BeforeEach
    fun setup() {
        testMember = memberRepository.save(Member(memberType = MemberType.SELLER, name = "Test Seller"))
    }

    @AfterEach
    fun cleanup() {
        productRepository.deleteAll()
        memberRepository.deleteAll()
    }

    @Test
    fun `모든 제품이 성공적으로 저장되면 빈 리스트를 반환해야 한다`() {
        // given
        val products = listOf(
            createProduct("Product 1", "CODE001", 10000),
            createProduct("Product 2", "CODE002", 20000),
            createProduct("Product 3", "CODE003", 30000)
        )

        // when
        val failedProducts = repository.saveAllAndReturnFailed(products)

        // then
        assertEquals(0, failedProducts.size, "모든 제품이 성공적으로 저장되어야 합니다")

        // 데이터베이스에 저장되었는지 확인
        val count = productRepository.count()
        assertEquals(3, count)
    }

    @Test
    fun `중복된 코드로 인해 INSERT가 실패하면 실패한 제품 리스트를 반환해야 한다`() {
        // given
        val existingProduct = createProduct("Existing Product", "DUPLICATE", 10000)
        productRepository.save(existingProduct)

        val products = listOf(
            createProduct("New Product 1", "NEW001", 10000),
            createProduct("Duplicate Product", "DUPLICATE", 20000), // 중복
            createProduct("New Product 2", "NEW002", 30000)
        )

        // when
        val failedProducts = repository.saveAllAndReturnFailed(products)

        // then
        assertEquals(1, failedProducts.size, "중복된 제품 1개만 실패해야 합니다")

        val failed = failedProducts.first()
        assertEquals("Duplicate Product", failed.name)
        assertEquals(Money.of(20000L), failed.price)
        assertEquals(100L, failed.stock)
        assertEquals(ProductCode("DUPLICATE"), failed.code)

        // 성공한 2개는 DB에 저장되었는지 확인
        val savedCount = productRepository.count()
        assertEquals(3, savedCount) // 기존 1개 + 새로 저장된 2개
    }

    @Test
    fun `모든 제품이 중복되어 INSERT가 실패하면 모든 제품 리스트를 반환해야 한다`() {
        // given
        val existingProducts = listOf(
            createProduct("Existing 1", "CODE001", 10000),
            createProduct("Existing 2", "CODE002", 20000),
            createProduct("Existing 3", "CODE003", 30000)
        )
        existingProducts.forEach { productRepository.save(it) }

        val duplicateProducts = listOf(
            createProduct("Duplicate 1", "CODE001", 15000),
            createProduct("Duplicate 2", "CODE002", 25000),
            createProduct("Duplicate 3", "CODE003", 35000)
        )

        // when
        val failedProducts = repository.saveAllAndReturnFailed(duplicateProducts)

        // then
        assertEquals(3, failedProducts.size, "모든 제품이 실패해야 합니다")
        assertEquals("Duplicate 1", failedProducts[0].name)
        assertEquals("Duplicate 2", failedProducts[1].name)
        assertEquals("Duplicate 3", failedProducts[2].name)

        // DB에는 기존 3개만 있어야 함
        assertEquals(3, productRepository.count())
    }

    @Test
    fun `빈 리스트가 입력되면 빈 리스트를 반환해야 한다`() {
        // given
        val products = emptyList<Product>()

        // when
        val failedProducts = repository.saveAllAndReturnFailed(products)

        // then
        assertEquals(0, failedProducts.size)
    }

    @Test
    fun `단일 제품이 성공적으로 저장되면 빈 리스트를 반환해야 한다`() {
        // given
        val product = createProduct("Single Product", "SINGLE001", 50000)

        // when
        val failedProducts = repository.saveAllAndReturnFailed(listOf(product))

        // then
        assertEquals(0, failedProducts.size)

        val count = productRepository.count()
        assertEquals(1, count)
    }

    @Test
    fun `단일 제품이 중복되어 실패하면 해당 제품을 반환해야 한다`() {
        // given
        val existingProduct = createProduct("Existing", "SINGLE002", 10000)
        productRepository.save(existingProduct)

        val duplicateProduct = createProduct("Duplicate", "SINGLE002", 20000)

        // when
        val failedProducts = repository.saveAllAndReturnFailed(listOf(duplicateProduct))

        // then
        assertEquals(1, failedProducts.size, "중복 제품이 실패해야 합니다")

        val failed = failedProducts.first()
        assertEquals("Duplicate", failed.name)
        assertEquals(Money.of(20000L), failed.price)
        assertEquals(ProductCode("SINGLE002"), failed.code)

        // DB에는 기존 1개만 있어야 함
        assertEquals(1, productRepository.count())
    }

    private fun createProduct(name: String, code: String, price: Long, stock: Long = 100): Product {
        return Product(
            ownerId = testMember.id,
            name = name,
            code = ProductCode(code),
            price = Money.of(price),
            stock = stock
        )
    }
}
