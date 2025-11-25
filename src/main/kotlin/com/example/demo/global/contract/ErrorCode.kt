package com.example.demo.global.contract

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
 * - ORD: Order (주문)
 *
 */
enum class ErrorCode(
    val code: String,
    val message: String,
    val status: HttpStatus
) {
    // Common
    COMMON_INVALID_INPUT("COM001", "잘못된 입력값입니다", HttpStatus.BAD_REQUEST),
    COMMON_INTERNAL_ERROR("COM002", "내부 서버 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),

    // Product
    PRODUCT_NOT_FOUND("PRO001", "상품을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    PRODUCT_CODE_DUPLICATED("PRO002", "이미 존재하는 상품 코드입니다", HttpStatus.CONFLICT),
    PRODUCT_OUT_OF_STOCK("PRO003", "재고가 부족합니다", HttpStatus.BAD_REQUEST),
    PRODUCT_ACCESS_DENIED("PRO04", "상품에 대한 권한이 없습니다.", HttpStatus.FORBIDDEN),

    // Member
    MEMBER_NOT_FOUND("MEM001", "사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

    // cart
    CART_ITEM_NOT_FOUND("CAR001", "장바구니에서 해당 제품을 찾을 수 없습니다.", HttpStatus.NOT_FOUND)
}