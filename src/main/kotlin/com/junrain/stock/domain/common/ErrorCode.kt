package com.junrain.stock.domain.common

import org.springframework.http.HttpStatus

/**
 * 비즈니스 에러 코드
 *
 * 에러코드 규칙:
 * - 형식: [도메인 3글자][숫자 3자리]
 * - 예시: COM001, PRO001, MEM001
 *
 * 도메인 코드:
 * - COM: Common (공통)
 * - PRO: Product (상품)
 * - MEM: Member (회원)
 *
 */
enum class ErrorCode(
    val code: String,
    val message: String,
    val status: HttpStatus,
) {
    // Common
    COMMON_INVALID_INPUT("COM001", "잘못된 입력값입니다", HttpStatus.BAD_REQUEST),
    COMMON_INTERNAL_ERROR("COM002", "내부 서버 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),

    // Product
    PRODUCT_NOT_FOUND("PRO001", "상품을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    PRODUCT_CODE_DUPLICATED("PRO002", "이미 존재하는 상품 코드입니다", HttpStatus.CONFLICT),
    // 재시도 불가 - 요청이 현재 재고 상태와 맞지 않는다
    STOCK_UNAVAILABLE("PRO003", "재고 점유에 실패했습니다", HttpStatus.BAD_REQUEST),
    PRODUCT_ACCESS_DENIED("PRO004", "상품에 대한 권한이 없습니다.", HttpStatus.FORBIDDEN),
    // 재시도 가능 - 값이 아니라 저장이 실패한 것이라 그대로 다시 보내면 된다
    PRODUCT_CREATION_ERROR("PRO005", "일시적인 문제로 저장하지 못했습니다. 잠시 후 그대로 다시 보내주세요", HttpStatus.CONFLICT),
    // 재시도 가능 - 인프라 일시 장애
    STOCK_UNSTABLE("PRO006", "재고 점유에 실패했습니다. 잠시 후 다시 시도해 주세요", HttpStatus.CONFLICT),

    // Member
    MEMBER_NOT_FOUND("MEM001", "사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
}
