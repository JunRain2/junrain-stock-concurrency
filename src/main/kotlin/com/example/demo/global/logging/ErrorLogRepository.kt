package com.example.demo.global.logging

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

private val logger = KotlinLogging.logger { }

@Repository
class ErrorLogRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper
) {
    fun saveErrorLog(requestKey: String, reason: ErrorLogType, content: Any) {
        val sql = """
            INSERT INTO exception_logs (request_id, request_content, reason) VALUES (?, ?, ?)
        """.trimIndent()

        val jsonContent = objectMapper.writeValueAsString(content).toString()

        try {
            jdbcTemplate.update(
                sql,
                requestKey, jsonContent, reason.toString()
            )
        } catch (e: Exception) {
            logger.error(e) { "로그 삽입 실패 : $requestKey : $content" }
        }
    }
}

enum class ErrorLogType {
    STOCK_CHANGE,
}