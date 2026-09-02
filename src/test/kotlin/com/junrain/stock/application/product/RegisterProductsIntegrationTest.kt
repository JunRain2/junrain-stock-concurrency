package com.junrain.stock.application.product

import com.junrain.stock.application.product.RegisterProducts.Command
import com.junrain.stock.application.product.RegisterProducts.Command.RegisterProduct
import com.junrain.stock.config.StockProbe
import com.junrain.stock.domain.common.Money
import com.junrain.stock.domain.member.Member
import com.junrain.stock.domain.member.MemberRepository
import com.junrain.stock.domain.member.MemberType
import com.junrain.stock.domain.product.Product
import com.junrain.stock.domain.product.ProductRepository
import com.junrain.stock.domain.product.vo.ProductCode
import com.junrain.stock.infra.product.mysql.JpaProductRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class RegisterProductsIntegrationTest {
    @Autowired
    private lateinit var registerProducts: RegisterProducts

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var jpaProductRepository: JpaProductRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var stockProbe: StockProbe

    @Autowired
    private lateinit var redissonClient: RedissonClient

    private var sellerId: Long = 0

    @BeforeEach
    fun setUp() {
        redissonClient.keys.flushall()
        sellerId = memberRepository.save(Member(memberType = MemberType.SELLER, name = "Test Seller")).id
    }

    @AfterEach
    fun tearDown() {
        jpaProductRepository.deleteAll()
        memberRepository.deleteAll()
    }

    @Test
    fun `전부 새 코드면 모두 성공하고 실패 목록이 비어 있다`() {
        val result = registerProducts(commandOf((1..10).map { row("REG-$it") }))

        result.successCount shouldBe 10
        result.failureCount shouldBe 0
        result.failedProducts shouldBe emptyList()
        jpaProductRepository.count() shouldBe 10

        withClue("예약 경로가 읽는 저장소는 Redis뿐이라 초기 재고가 거기 심겨야 팔 수 있는 상품이 된다") {
            jpaProductRepository.findAll().forEach { stockProbe.stockOf(it.id) shouldBe STOCK }
        }
    }

    @Test
    fun `이미 존재하는 코드가 섞이면 그 행만 실패하고 나머지는 커밋된다`() {
        saveProduct("DUP-A")
        saveProduct("DUP-B")

        val result =
            registerProducts(
                commandOf(listOf(row("NEW-1"), row("DUP-A"), row("NEW-2"), row("DUP-B"), row("NEW-3"))),
            )

        result.successCount shouldBe 3
        withClue("중복은 요청의 1, 3번 자리에 있었다. 이 위치가 어긋나면 사용자는 멀쩡한 행을 고치게 된다") {
            result.failedProducts.map { it.index } shouldBe listOf(1, 3)
        }
        withClue("실패한 행 때문에 성공한 행까지 되돌아가면 부분 성공이 아니다") {
            jpaProductRepository.count() shouldBe 5
        }
    }

    @Test
    fun `요청 안에서 코드가 겹치면 겹친 행이 모두 실패한다`() {
        val result =
            registerProducts(
                commandOf(listOf(row("SAME"), row("OK-1"), row("SAME"), row("OK-2"))),
            )

        withClue("어느 쪽이 진짜인지 요청만 보고는 알 수 없으므로 둘 다 실패시킨다") {
            result.failedProducts.map { it.index } shouldBe listOf(0, 2)
        }
        result.successCount shouldBe 2
        jpaProductRepository.count() shouldBe 2
    }

    @Test
    fun `상품명이 규칙에 어긋나면 그 행만 실패하고 DB에는 안 들어간다`() {
        val result =
            registerProducts(
                commandOf(listOf(row("VAL-1"), row("VAL-2").copy(name = "이름!!"), row("VAL-3"))),
            )

        result.failedProducts.map { it.index } shouldBe listOf(1)
        withClue("DB까지 가기 전에 걸러진 행도 같은 index 체계로 보고돼야 한다") {
            result.successCount shouldBe 2
            jpaProductRepository.count() shouldBe 2
        }
    }

    @Test
    fun `청크 경계를 넘겨도 실패한 행의 위치가 어긋나지 않는다`() {
        val total = CHUNK_SIZE + 50
        val duplicatedIndexes = listOf(500, CHUNK_SIZE + 40)
        duplicatedIndexes.forEach { saveProduct("BIG-$it") }

        val result = registerProducts(commandOf((0 until total).map { row("BIG-$it") }))

        withClue("청크마다 결과를 제자리에 꽂지 않으면 두 번째 청크의 위치가 통째로 밀린다") {
            result.failedProducts.map { it.index } shouldBe duplicatedIndexes
        }
        result.successCount shouldBe total - duplicatedIndexes.size
        jpaProductRepository.count() shouldBe total.toLong()
    }

    @Test
    fun `한 청크가 전부 중복이어도 다음 청크는 들어간다`() {
        val first = (0 until CHUNK_SIZE).map { row("CHUNK-$it") }
        registerProducts(commandOf(first)).successCount shouldBe CHUNK_SIZE

        val result = registerProducts(commandOf(first + (0 until 50).map { row("CHUNK-NEW-$it") }))

        withClue("첫 청크는 생성키가 0건이라 역조회를 건너뛴다. 그 경로가 다음 청크의 판별을 흔들면 안 된다") {
            result.successCount shouldBe 50
            result.failedProducts.map { it.index } shouldBe (0 until CHUNK_SIZE).toList()
        }
        jpaProductRepository.count() shouldBe (CHUNK_SIZE + 50).toLong()
    }

    @Test
    fun `요청 건수가 상한을 넘으면 요청 전체가 실패한다`() {
        shouldThrow<IllegalArgumentException> {
            registerProducts(commandOf((0..MAX_SIZE).map { row("OVER-$it") }))
        }

        withClue("건수는 행 단위로 나누어 보고할 수 없는 조건이라 한 행도 들어가면 안 된다") {
            jpaProductRepository.count() shouldBe 0
        }
    }

    private fun commandOf(products: List<RegisterProduct>) = Command(ownerId = sellerId, products = products)

    /** 이름은 상품코드와 달리 특수문자를 못 쓴다. 코드를 이름에 섞으면 하이픈 때문에 검증에서 먼저 걸린다 */
    private fun row(code: String) = RegisterProduct(name = "상품", price = 1000, stock = STOCK, code = code)

    private fun saveProduct(code: String) =
        productRepository.save(
            Product(ownerId = sellerId, code = ProductCode(code), stock = STOCK, price = Money.of(1000L), name = "기존상품"),
        )

    companion object {
        private const val STOCK = 100L

        /** application.yml의 bulk-insert.chunk-size와 같아야 경계 테스트가 의미를 갖는다 */
        private const val CHUNK_SIZE = 1000

        /** application.yml의 bulk-insert.max-size */
        private const val MAX_SIZE = 5000
    }
}
