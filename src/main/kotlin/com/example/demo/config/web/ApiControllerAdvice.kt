package com.example.demo.config.web

import com.example.demo.contract.dto.ApiResponse
import com.example.demo.contract.exception.BusinessException
import com.example.demo.contract.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiControllerAdvice {
    private val log = LoggerFactory.getLogger(ApiControllerAdvice::class.java)

    @ExceptionHandler
    fun handleBusinessException(e: BusinessException): ResponseEntity<ApiResponse<*>> {
        log.error("BusinessException : {}", e.message, e)
        return ResponseEntity.status(e.errorCode.status)
            .body(ApiResponse.fail(e.errorCode, e.message))
    }

    @ExceptionHandler
    fun handleIllegalArgumentException(e: IllegalArgumentException): ResponseEntity<ApiResponse<*>> {
        log.error("IllegalArgumentException : {}", e.message, e)
        return ResponseEntity.status(ErrorCode.COMMON_INVALID_INPUT.status)
            .body(ApiResponse.fail(ErrorCode.COMMON_INVALID_INPUT, e.message))
    }

    @ExceptionHandler
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<*>> {
        log.error("MethodArgumentNotValidException : {}", e.message, e)
        return ResponseEntity.status(ErrorCode.COMMON_INVALID_INPUT.status)
            .body(
                ApiResponse.fail(
                    ErrorCode.COMMON_INVALID_INPUT,
                    ErrorCode.COMMON_INVALID_INPUT.message,
                    e.bindingResult
                )
            )
    }

    @ExceptionHandler
    fun handleException(e: Exception): ResponseEntity<ApiResponse<*>> {
        log.error("Exception: {}", e.message, e)
        return ResponseEntity.status(ErrorCode.COMMON_INTERNAL_ERROR.status)
            .body(ApiResponse.fail(ErrorCode.COMMON_INTERNAL_ERROR, e.message))
    }
}