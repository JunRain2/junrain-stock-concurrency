package com.example.demo.global.contract

/**
 * 비즈니스 예외 기본 클래스
 *
 * 모든 비즈니스 예외는 이 클래스를 상속받아 구현합니다.
 * ErrorCode를 통해 에러 코드, 메시지, HTTP 상태 코드를 관리합니다.
 */
abstract class BusinessException(
    val errorCode: ErrorCode,
    customMessage: String?
) : RuntimeException(customMessage)