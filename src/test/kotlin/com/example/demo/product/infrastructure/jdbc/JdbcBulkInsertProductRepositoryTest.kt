package com.example.demo.product.infrastructure.jdbc

import com.example.demo.global.contract.vo.Money
import com.example.demo.member.command.domain.Member
import com.example.demo.member.command.domain.MemberRepository
import com.example.demo.member.command.domain.MemberType
import com.example.demo.product.command.domain.Product
import com.example.demo.product.command.domain.vo.ProductCode
import com.example.demo.product.command.infrastructure.mysql.JdbcProductRepository
import com.example.demo.product.command.infrastructure.mysql.JpaProductRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
class JdbcBulkInsertProductRepositoryTest {

    @Autowired
    private lateinit var repository: JdbcProductRepository

    @Autowired
    private lateinit var productRepository: JpaProductRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Value("\${bulk-insert.chunk-size}")
    private var chunkSize: Int = 0

    private lateinit var testMember: Member

    @BeforeEach
    fun setup() {
        testMember =
            memberRepository.save(Member(memberType = MemberType.SELLER, name = "Test Seller"))
    }

    @AfterEach
    fun cleanup() {
        productRepository.deleteAll()
        memberRepository.deleteAll()
    }

    @Test
    fun `모든 제품이 성공적으로 저장되면 빈 리스트를 반환해야 한다`() = runBlocking {
        // given
        val products = listOf(
            createProduct("Product 1", "CODE001", 10000),
            createProduct("Product 2", "CODE002", 20000),
            createProduct("Product 3", "CODE003", 30000)
        )

        // when
        val result = repository.bulkInsert(products)

        // then
        assertEquals(3, result.count { it.isSuccess }, "모든 제품이 성공적으로 저장되어야 합니다")
        assertEquals(0, result.count { it.isFailure }, "실패한 제품이 없어야 합니다")

        // 데이터베이스에 저장되었는지 확인
        val count = productRepository.count()
        assertEquals(3, count)
    }

    @Test
    fun `중복된 코드로 인해 INSERT가 실패하면 실패한 제품 리스트를 반환해야 한다`() = runBlocking {
        // given
        val existingProduct = createProduct("Existing Product", "DUPLICATE", 10000)
        productRepository.save(existingProduct)

        val products = listOf(
            createProduct("New Product 1", "NEW001", 10000),
            createProduct("Duplicate Product", "DUPLICATE", 20000), // 중복
            createProduct("New Product 2", "NEW002", 30000)
        )

        // when
        val result = repository.bulkInsert(products)

        // then
        assertEquals(2, result.count { it.isSuccess }, "2개의 제품이 성공해야 합니다")
        assertEquals(1, result.count { it.isFailure }, "중복된 제품 1개만 실패해야 합니다")

        // 성공한 2개는 DB에 저장되었는지 확인
        val savedCount = productRepository.count()
        assertEquals(3, savedCount) // 기존 1개 + 새로 저장된 2개
    }

    @Test
    fun `모든 제품이 중복되어 INSERT가 실패하면 모든 제품 리스트를 반환해야 한다`() = runBlocking {
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
        val result = repository.bulkInsert(duplicateProducts)

        // then
        assertEquals(0, result.count { it.isSuccess }, "성공한 제품이 없어야 합니다")
        assertEquals(3, result.count { it.isFailure }, "모든 제품이 실패해야 합니다")

        // DB에는 기존 3개만 있어야 함
        assertEquals(3, productRepository.count())
    }

    @Test
    fun `빈 리스트가 입력되면 빈 리스트를 반환해야 한다`() = runBlocking {
        // given
        val products = emptyList<Product>()

        // when
        val result = repository.bulkInsert(products)

        // then
        assertEquals(0, result.count { it.isSuccess })
        assertEquals(0, result.count { it.isFailure })
    }

    @Test
    fun `단일 제품이 성공적으로 저장되면 빈 리스트를 반환해야 한다`() = runBlocking {
        // given
        val product = createProduct("Single Product", "SINGLE001", 50000)

        // when
        val result = repository.bulkInsert(listOf(product))

        // then
        assertEquals(1, result.count { it.isSuccess })
        assertEquals(0, result.count { it.isFailure })

        val count = productRepository.count()
        assertEquals(1, count)
    }

    @Test
    fun `단일 제품이 중복되어 실패하면 해당 제품을 반환해야 한다`() = runBlocking {
        // given
        val existingProduct = createProduct("Existing", "SINGLE002", 10000)
        productRepository.save(existingProduct)

        val duplicateProduct = createProduct("Duplicate", "SINGLE002", 20000)

        // when
        val result = repository.bulkInsert(listOf(duplicateProduct))

        // then
        assertEquals(0, result.count { it.isSuccess }, "성공한 제품이 없어야 합니다")
        assertEquals(1, result.count { it.isFailure }, "중복 제품이 실패해야 합니다")

        // DB에는 기존 1개만 있어야 함
        assertEquals(1, productRepository.count())
    }

    @Test
    fun `청크 크기를 초과하는 제품 리스트를 저장할 수 있어야 한다`() = runBlocking {
        // given
        val largeProductList = (1..chunkSize + 10).map { i ->
            createProduct("Product $i", "CODE${i.toString().padStart(5, '0')}", 10000)
        }

        // when
        val result = repository.bulkInsert(largeProductList)

        // then
        assertEquals(chunkSize + 10, result.count { it.isSuccess }, "모든 제품이 성공적으로 저장되어야 합니다")
        assertEquals(0, result.count { it.isFailure }, "실패한 제품이 없어야 합니다")

        val count = productRepository.count()
        assertEquals(chunkSize + 10L, count)
    }

    @Test
    fun `청크 크기를 정확히 맞추는 제품 리스트를 저장할 수 있어야 한다`() = runBlocking {
        // given
        val products = (1..chunkSize).map { i ->
            createProduct("Product $i", "CODE${i.toString().padStart(5, '0')}", 10000)
        }

        // when
        val result = repository.bulkInsert(products)

        // then
        assertEquals(chunkSize, result.count { it.isSuccess }, "모든 제품이 성공적으로 저장되어야 합니다")
        assertEquals(0, result.count { it.isFailure }, "실패한 제품이 없어야 합니다")

        val count = productRepository.count()
        assertEquals(chunkSize.toLong(), count)
    }

    @Test
    fun `대량의 제품 중 일부가 중복되어도 나머지는 저장되어야 한다`() = runBlocking {
        // given
        val existingProducts = (1..5).map { i ->
            createProduct("Existing $i", "EXIST${i.toString().padStart(3, '0')}", 10000)
        }
        existingProducts.forEach { productRepository.save(it) }

        val mixedProducts = (1..20).map { i ->
            if (i <= 5) {
                createProduct("Duplicate $i", "EXIST${i.toString().padStart(3, '0')}", 20000) // 중복
            } else {
                createProduct("New Product $i", "NEW${i.toString().padStart(3, '0')}", 10000)
            }
        }

        // when
        val result = repository.bulkInsert(mixedProducts)

        // then
        assertEquals(15, result.count { it.isSuccess }, "15개의 새로운 제품이 성공해야 합니다")
        assertEquals(5, result.count { it.isFailure }, "5개의 중복 제품이 실패해야 합니다")

        val count = productRepository.count()
        assertEquals(20L, count) // 기존 5개 + 새로 저장된 15개
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
