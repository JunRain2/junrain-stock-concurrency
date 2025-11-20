package com.example.demo.global.contract

import org.springframework.http.HttpStatus

/**
 * 비즈니스 에러 코드
 *
 * 에러코드 규칙:
 * - 형식: [도메인]-[카테고리]
 * - 하이픈(-)으로 구분
 *
 * 도메인:
 * - COMMON: 공통
 * - PRODUCT: 상품
 * - MEMBER: 회원
 * - ORDER: 주문
 * - COMPANY: 회사
 *
 * 카테고리:
 * - INVALID_INPUT: 유효성 검증 실패
 * - DUPLICATED: 중복 오류
 * - NOT_FOUND: 존재하지 않음
 * - FORBIDDEN: 권한 오류
 * - INVALID_STATE: 상태 오류
 * - OUT_OF_STOCK: 재고 부족
 *
 * 예시:
 * - COMMON-INVALID_INPUT: 공통 유효성 검증 오류
 * - PRODUCT-NOT_FOUND: 상품을 찾을 수 없음
 * - PRODUCT-OUT_OF_STOCK: 재고 부족
 */
enum class ErrorCode(
    val code: String,
    val message: String,
    val status: HttpStatus
) {
    // Common
    COMMON_INVALID_INPUT("COMMON-INVALID_INPUT", "잘못된 입력값입니다", HttpStatus.BAD_REQUEST),
    COMMON_INTERNAL_ERROR("COMMON-INTERNAL_ERROR", "내부 서버 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),

    // Product
    PRODUCT_NOT_FOUND("PRODUCT-NOT_FOUND", "상품을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    PRODUCT_CODE_DUPLICATED("PRODUCT-DUPLICATED", "이미 존재하는 상품 코드입니다", HttpStatus.CONFLICT),
    PRODUCT_OUT_OF_STOCK("PRODUCT-OUT_OF_STOCK", "재고가 부족합니다", HttpStatus.BAD_REQUEST),
    PRODUCT_INVALID_NAME("PRODUCT-INVALID_NAME", "상품명이 유효하지 않습니다", HttpStatus.BAD_REQUEST),
    PRODUCT_INVALID_PRICE("PRODUCT-INVALID_PRICE", "상품 가격이 유효하지 않습니다", HttpStatus.BAD_REQUEST),
    PRODUCT_INVALID_STOCK("PRODUCT-INVALID_STOCK", "재고 수량이 유효하지 않습니다", HttpStatus.BAD_REQUEST),
}