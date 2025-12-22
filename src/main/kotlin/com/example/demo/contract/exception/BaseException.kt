package com.example.demo.contract.exception

/**
 * 비즈니스 예외 기본 클래스
 *
 * 모든 비즈니스 예외는 이 클래스를 상속받아 구현합니다.
 * ErrorCode를 통해 에러 코드, 메시지, HTTP 상태 코드를 관리합니다.
 */
abstract class BusinessException(
    val errorCode: ErrorCode, customMessage: String? = null
) : RuntimeException(customMessage)


/**
 * 인프라 계층에서 예외 처리가 완료되었음을 표시하는 마커 예외
 *
 * 이 예외로 감싸진 경우 상위 계층에서 롤백이 불필요함을 의미합니다.
 */
class InfraException(
    cause: Throwable, customMessage: String? = null
) : RuntimeException(customMessage ?: cause.message, cause)