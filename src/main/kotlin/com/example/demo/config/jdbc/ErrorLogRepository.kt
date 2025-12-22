package com.example.demo.config.jdbc

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

private val logger = KotlinLogging.logger { }

@Repository
class ErrorLogRepository(
    private val jdbcTemplate: JdbcTemplate, private val objectMapper: ObjectMapper
) {
    fun saveErrorLog(requestKey: String, reason: ErrorLogType, content: Any) {
        logger.info { "${reason.name} 에러 로그 저장 시작" }
        val sql = """
            INSERT INTO exception_logs (request_key, request_content, reason) VALUES (?, ?, ?)
        """.trimIndent()

        val jsonContent = objectMapper.writeValueAsString(content).toString()

        try {
            jdbcTemplate.update(
                sql, requestKey, jsonContent, reason.name
            )
        } catch (e: Exception) {
            logger.error(e) { "로그 삽입 실패 : $requestKey : $content" }
        }
    }

    fun <T> findAllErrorLog(
        reason: ErrorLogType, typeRef: TypeReference<T>
    ): List<ErrorLog<T>> {
        val sql = """
            SELECT request_key as request_key, request_content as content
            FROM exception_logs
            WHERE reason = ?
              AND is_executed = false
              AND created_at <= DATE_SUB(NOW(), INTERVAL 1 MINUTE)
            ORDER BY created_at
    """.trimIndent()

        val rowMapper = RowMapper { rs, _ ->
            val requestKey = rs.getString("request_key")
            val json = rs.getString("content")
            val content = objectMapper.readValue(json, typeRef)
            ErrorLog(requestKey, content)
        }

        return jdbcTemplate.query(sql, rowMapper, reason.name)
    }

    fun setExecuted(requestKey: String) {
        val sql = """
            UPDATE exception_logs SET is_executed = true WHERE request_key = ?
        """.trimIndent()

        jdbcTemplate.update(
            sql, requestKey
        )
    }
}

data class ErrorLog<T>(
    val requestKey: String, val content: T
)

enum class ErrorLogType {
    STOCK_CHANGE,
}