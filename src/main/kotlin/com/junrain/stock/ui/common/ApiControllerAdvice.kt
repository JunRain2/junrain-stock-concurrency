package com.junrain.stock.ui.common

import com.junrain.stock.domain.common.BusinessException
import com.junrain.stock.domain.common.ErrorCode
import com.junrain.stock.ui.common.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.dao.TransientDataAccessException
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.CannotGetJdbcConnectionException
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiControllerAdvice {
    private val log = LoggerFactory.getLogger(ApiControllerAdvice::class.java)

    @ExceptionHandler
    fun handleBusinessException(e: BusinessException): ResponseEntity<ApiResponse<*>> {
        log.warn("BusinessException {} : {}", e.errorCode.code, e.message)
        return ResponseEntity
            .status(e.errorCode.status)
            .body(ApiResponse.fail(e.errorCode, e.message))
    }

    @ExceptionHandler
    fun handleIllegalArgumentException(e: IllegalArgumentException): ResponseEntity<ApiResponse<*>> {
        log.error("IllegalArgumentException : {}", e.message, e)
        return ResponseEntity
            .status(ErrorCode.COMMON_INVALID_INPUT.status)
            .body(ApiResponse.fail(ErrorCode.COMMON_INVALID_INPUT, e.message))
    }

    @ExceptionHandler
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<*>> {
        log.error("MethodArgumentNotValidException : {}", e.message, e)
        return ResponseEntity
            .status(ErrorCode.COMMON_INVALID_INPUT.status)
            .body(
                ApiResponse.fail(
                    ErrorCode.COMMON_INVALID_INPUT,
                    ErrorCode.COMMON_INVALID_INPUT.message,
                    e.bindingResult,
                ),
            )
    }

    /**
     * 커넥션 풀 고갈처럼 잠시 후 그대로 다시 보내면 되는 실패를 409로 내린다.
     *
     * 같은 고갈이라도 JPA는 [CannotCreateTransactionException], JDBC는
     * [CannotGetJdbcConnectionException]으로 나온다. 앞의 것은 TransactionException 계열이라
     * DataAccessException 하나로 묶으면 빠진다.
     */
    @ExceptionHandler(
        CannotCreateTransactionException::class,
        CannotGetJdbcConnectionException::class,
        TransientDataAccessException::class,
    )
    fun handleTransientInfraException(e: Exception): ResponseEntity<ApiResponse<*>> {
        log.error("일시 장애: {}", e.message, e)
        return ResponseEntity
            .status(ErrorCode.COMMON_UNAVAILABLE.status)
            .body(ApiResponse.fail(ErrorCode.COMMON_UNAVAILABLE, ErrorCode.COMMON_UNAVAILABLE.message))
    }

    @ExceptionHandler
    fun handleException(e: Exception): ResponseEntity<ApiResponse<*>> {
        log.error("Exception: {}", e.message, e)
        // e.message를 실으면 내부 구현이 그대로 사용자에게 나간다
        return ResponseEntity
            .status(ErrorCode.COMMON_INTERNAL_ERROR.status)
            .body(ApiResponse.fail(ErrorCode.COMMON_INTERNAL_ERROR, ErrorCode.COMMON_INTERNAL_ERROR.message))
    }
}
