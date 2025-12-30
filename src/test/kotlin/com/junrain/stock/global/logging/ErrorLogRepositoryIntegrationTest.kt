package com.junrain.stock.global.logging

import com.junrain.stock.config.jdbc.ErrorLogRepository
import com.junrain.stock.config.jdbc.ErrorLogType
import com.junrain.stock.product.command.domain.StockChange
import com.fasterxml.jackson.core.type.TypeReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest
class ErrorLogRepositoryIntegrationTest {

    @Autowired
    private lateinit var errorLogRepository: ErrorLogRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        // DB 초기화
        jdbcTemplate.execute("DELETE FROM exception_logs")
    }

    // ==================== saveErrorLog() 테스트 ====================

    @Test
    fun `saveErrorLog는 에러 로그를 DB에 저장해야 한다`() {
        // given
        val requestKey = "test-request-123"
        val reason = ErrorLogType.STOCK_CHANGE
        val content = listOf(
            StockChange(productId = 1L, quantity = 10L),
            StockChange(productId = 2L, quantity = 5L)
        )

        // when
        errorLogRepository.saveErrorLog(requestKey, reason, content)

        // then
        val savedLogs = jdbcTemplate.queryForList(
            "SELECT * FROM exception_logs WHERE request_key = ?",
            requestKey
        )

        assertEquals(1, savedLogs.size)
        val savedLog = savedLogs[0]
        assertEquals(requestKey, savedLog["request_key"])
        assertEquals(reason.name, savedLog["reason"])
        assertNotNull(savedLog["request_content"])
        assertFalse(savedLog["is_executed"] as Boolean)
    }

    @Test
    fun `saveErrorLog는 단일 StockChange를 저장할 수 있다`() {
        // given
        val requestKey = "single-change-456"
        val reason = ErrorLogType.STOCK_CHANGE
        val content = StockChange(productId = 1L, quantity = 10L)

        // when
        errorLogRepository.saveErrorLog(requestKey, reason, content)

        // then
        val savedLogs = jdbcTemplate.queryForList(
            "SELECT * FROM exception_logs WHERE request_key = ?",
            requestKey
        )

        assertEquals(1, savedLogs.size)
    }

    @Test
    fun `saveErrorLog는 중복된 requestKey로 저장하면 실패해야 한다`() {
        // given
        val requestKey = "duplicate-789"
        val reason = ErrorLogType.STOCK_CHANGE
        val content = StockChange(productId = 1L, quantity = 10L)

        // when - 첫 번째 저장
        errorLogRepository.saveErrorLog(requestKey, reason, content)

        // 두 번째 저장 시도 (중복)
        errorLogRepository.saveErrorLog(requestKey, reason, content)

        // then - 여전히 1개만 존재해야 함 (중복 저장 실패)
        val savedLogs = jdbcTemplate.queryForList(
            "SELECT * FROM exception_logs WHERE request_key = ?",
            requestKey
        )

        assertEquals(1, savedLogs.size, "중복된 request_key는 저장되지 않아야 함")
    }

    @Test
    fun `saveErrorLog는 JSON 형태로 content를 저장해야 한다`() {
        // given
        val requestKey = "json-test-111"
        val reason = ErrorLogType.STOCK_CHANGE
        val content = listOf(
            StockChange(productId = 1L, quantity = 10L),
            StockChange(productId = 2L, quantity = 5L)
        )

        // when
        errorLogRepository.saveErrorLog(requestKey, reason, content)

        // then
        val savedLogs = jdbcTemplate.queryForList(
            "SELECT * FROM exception_logs WHERE request_key = ?",
            requestKey
        )

        val savedContent = savedLogs[0]["request_content"] as String
        assertTrue(savedContent.contains("productId"))
        assertTrue(savedContent.contains("quantity"))
        assertTrue(savedContent.contains("1") && savedContent.contains("10"))
        assertTrue(savedContent.contains("2") && savedContent.contains("5"))
    }

    // ==================== findAllErrorLog() 테스트 ====================

    @Test
    fun `findAllErrorLog는 특정 reason의 에러 로그를 조회할 수 있다`() {
        // given
        val requestKey1 = "find-test-1"
        val requestKey2 = "find-test-2"
        val reason = ErrorLogType.STOCK_CHANGE
        val content1 = listOf(StockChange(productId = 1L, quantity = 10L))
        val content2 = listOf(StockChange(productId = 2L, quantity = 5L))

        errorLogRepository.saveErrorLog(requestKey1, reason, content1)
        errorLogRepository.saveErrorLog(requestKey2, reason, content2)

        // 데이터가 1분 이상 경과하도록 created_at 수정
        jdbcTemplate.update(
            "UPDATE exception_logs SET created_at = DATE_SUB(NOW(), INTERVAL 2 MINUTE) WHERE request_key IN (?, ?)",
            requestKey1, requestKey2
        )

        // when
        val errorLogs = errorLogRepository.findAllErrorLog(
            reason = reason,
            typeRef = object : TypeReference<List<StockChange>>() {}
        )

        // then
        assertTrue(errorLogs.size >= 2, "최소 2개 이상의 에러 로그가 조회되어야 함")
        val requestKeys = errorLogs.map { it.requestKey }
        assertTrue(requestKeys.contains(requestKey1))
        assertTrue(requestKeys.contains(requestKey2))
    }

    @Test
    fun `findAllErrorLog는 is_executed가 false인 것만 조회해야 한다`() {
        // given
        val requestKey1 = "executed-false"
        val requestKey2 = "executed-true"
        val reason = ErrorLogType.STOCK_CHANGE
        val content = listOf(StockChange(productId = 1L, quantity = 10L))

        errorLogRepository.saveErrorLog(requestKey1, reason, content)
        errorLogRepository.saveErrorLog(requestKey2, reason, content)

        // 데이터가 1분 이상 경과하도록 created_at 수정
        jdbcTemplate.update(
            "UPDATE exception_logs SET created_at = DATE_SUB(NOW(), INTERVAL 2 MINUTE) WHERE request_key IN (?, ?)",
            requestKey1, requestKey2
        )

        // requestKey2는 실행됨으로 표시
        jdbcTemplate.update(
            "UPDATE exception_logs SET is_executed = true WHERE request_key = ?",
            requestKey2
        )

        // when
        val errorLogs = errorLogRepository.findAllErrorLog(
            reason = reason,
            typeRef = object : TypeReference<List<StockChange>>() {}
        )

        // then
        val requestKeys = errorLogs.map { it.requestKey }
        assertTrue(requestKeys.contains(requestKey1), "is_executed=false인 requestKey1이 조회되어야 함")
        assertFalse(requestKeys.contains(requestKey2), "is_executed=true인 requestKey2는 조회되지 않아야 함")
    }

    @Test
    fun `findAllErrorLog는 1분 이내 데이터는 조회하지 않아야 한다`() {
        // given
        val requestKey = "recent-data"
        val reason = ErrorLogType.STOCK_CHANGE
        val content = listOf(StockChange(productId = 1L, quantity = 10L))

        errorLogRepository.saveErrorLog(requestKey, reason, content)

        // when - 1분 이내 데이터이므로 조회되지 않음
        val errorLogs = errorLogRepository.findAllErrorLog(
            reason = reason,
            typeRef = object : TypeReference<List<StockChange>>() {}
        )

        // then
        val requestKeys = errorLogs.map { it.requestKey }
        assertFalse(requestKeys.contains(requestKey), "1분 이내의 데이터는 조회되지 않아야 함")
    }

    @Test
    fun `findAllErrorLog는 content를 올바르게 역직렬화해야 한다`() {
        // given
        val requestKey = "deserialize-test"
        val reason = ErrorLogType.STOCK_CHANGE
        val content = listOf(
            StockChange(productId = 1L, quantity = 10L),
            StockChange(productId = 2L, quantity = 5L)
        )

        errorLogRepository.saveErrorLog(requestKey, reason, content)

        // 데이터가 1분 이상 경과하도록 created_at 수정
        jdbcTemplate.update(
            "UPDATE exception_logs SET created_at = DATE_SUB(NOW(), INTERVAL 2 MINUTE) WHERE request_key = ?",
            requestKey
        )

        // when
        val errorLogs = errorLogRepository.findAllErrorLog(
            reason = reason,
            typeRef = object : TypeReference<List<StockChange>>() {}
        )

        // then
        val foundLog = errorLogs.find { it.requestKey == requestKey }
        assertNotNull(foundLog, "저장한 로그가 조회되어야 함")

        val stockChanges = foundLog!!.content
        assertEquals(2, stockChanges.size)
        assertEquals(1L, stockChanges[0].productId)
        assertEquals(10L, stockChanges[0].quantity)
        assertEquals(2L, stockChanges[1].productId)
        assertEquals(5L, stockChanges[1].quantity)
    }

    @Test
    fun `findAllErrorLog는 created_at 오름차순으로 정렬되어야 한다`() {
        // given
        val requestKey1 = "sort-test-1"
        val requestKey2 = "sort-test-2"
        val requestKey3 = "sort-test-3"
        val reason = ErrorLogType.STOCK_CHANGE
        val content = listOf(StockChange(productId = 1L, quantity = 10L))

        errorLogRepository.saveErrorLog(requestKey1, reason, content)
        Thread.sleep(100)
        errorLogRepository.saveErrorLog(requestKey2, reason, content)
        Thread.sleep(100)
        errorLogRepository.saveErrorLog(requestKey3, reason, content)

        // 모든 데이터가 1분 이상 경과하도록 created_at 수정
        jdbcTemplate.update(
            "UPDATE exception_logs SET created_at = DATE_SUB(created_at, INTERVAL 2 MINUTE) WHERE request_key IN (?, ?, ?)",
            requestKey1, requestKey2, requestKey3
        )

        // when
        val errorLogs = errorLogRepository.findAllErrorLog(
            reason = reason,
            typeRef = object : TypeReference<List<StockChange>>() {}
        )

        // then
        val foundLogs =
            errorLogs.filter { it.requestKey in listOf(requestKey1, requestKey2, requestKey3) }
        assertTrue(foundLogs.size >= 3, "최소 3개의 로그가 조회되어야 함")

        // created_at 오름차순으로 정렬되어 있는지 확인
        val requestKeys = foundLogs.map { it.requestKey }
        val indexOfKey1 = requestKeys.indexOf(requestKey1)
        val indexOfKey2 = requestKeys.indexOf(requestKey2)
        val indexOfKey3 = requestKeys.indexOf(requestKey3)

        assertTrue(indexOfKey1 < indexOfKey2, "requestKey1이 requestKey2보다 앞에 있어야 함")
        assertTrue(indexOfKey2 < indexOfKey3, "requestKey2가 requestKey3보다 앞에 있어야 함")
    }

    // ==================== setExecuted() 테스트 ====================

    @Test
    fun `setExecuted는 특정 requestKey의 is_executed를 true로 변경해야 한다`() {
        // given
        val requestKey = "execute-test"
        val reason = ErrorLogType.STOCK_CHANGE
        val content = listOf(StockChange(productId = 1L, quantity = 10L))

        errorLogRepository.saveErrorLog(requestKey, reason, content)

        // 초기 상태 확인
        var savedLogs = jdbcTemplate.queryForList(
            "SELECT * FROM exception_logs WHERE request_key = ?",
            requestKey
        )
        assertFalse(savedLogs[0]["is_executed"] as Boolean, "초기에는 is_executed가 false여야 함")

        // when
        errorLogRepository.setExecuted(requestKey)

        // then
        savedLogs = jdbcTemplate.queryForList(
            "SELECT * FROM exception_logs WHERE request_key = ?",
            requestKey
        )
        assertTrue(savedLogs[0]["is_executed"] as Boolean, "setExecuted 후에는 is_executed가 true여야 함")
    }

    @Test
    fun `setExecuted는 존재하지 않는 requestKey에 대해 아무것도 하지 않아야 한다`() {
        // given
        val nonExistentKey = "non-existent-key"

        // when - 예외가 발생하지 않아야 함
        assertDoesNotThrow {
            errorLogRepository.setExecuted(nonExistentKey)
        }
    }

    // ==================== 통합 시나리오 테스트 ====================

    @Test
    fun `에러 로그 저장, 조회, 실행 표시의 전체 워크플로우가 정상 동작해야 한다`() {
        // given
        val requestKey = "workflow-test"
        val reason = ErrorLogType.STOCK_CHANGE
        val content = listOf(
            StockChange(productId = 1L, quantity = 10L),
            StockChange(productId = 2L, quantity = 5L)
        )

        // when 1 - 에러 로그 저장
        errorLogRepository.saveErrorLog(requestKey, reason, content)

        // 1분 이상 경과하도록 수정
        jdbcTemplate.update(
            "UPDATE exception_logs SET created_at = DATE_SUB(NOW(), INTERVAL 2 MINUTE) WHERE request_key = ?",
            requestKey
        )

        // when 2 - 에러 로그 조회
        val errorLogs = errorLogRepository.findAllErrorLog(
            reason = reason,
            typeRef = object : TypeReference<List<StockChange>>() {}
        )

        // then 1 - 조회됨
        val foundLog = errorLogs.find { it.requestKey == requestKey }
        assertNotNull(foundLog, "저장한 로그가 조회되어야 함")
        assertEquals(2, foundLog!!.content.size)

        // when 3 - 실행 표시
        errorLogRepository.setExecuted(requestKey)

        // when 4 - 다시 조회
        val afterExecutedLogs = errorLogRepository.findAllErrorLog(
            reason = reason,
            typeRef = object : TypeReference<List<StockChange>>() {}
        )

        // then 2 - 실행 표시 후에는 조회되지 않음
        val foundLogAfter = afterExecutedLogs.find { it.requestKey == requestKey }
        assertNull(foundLogAfter, "is_executed=true인 로그는 조회되지 않아야 함")
    }
}
