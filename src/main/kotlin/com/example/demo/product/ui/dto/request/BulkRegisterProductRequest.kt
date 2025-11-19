package com.example.demo.product.ui.dto.request

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty

/**
 * 요구사항에 부분 성공이 존재
 * @Valid를 통해서 엄격하게 검사를 할 경우 전체 요청이 들어오지 않아 전체 실패
 * 따라서, 빈 값, 데이터 형식만 검사
 */
data class BulkRegisterProductRequest(
    @field:Valid
    @field:NotEmpty(message = "상품은 최소 1개 이상 등록해야 합니다")
    val products: List<RegisterProduct>
){
    data class RegisterProduct(
        @field:NotBlank(message = "상품명은 필수입니다")
        val name: String,

        val price: Long,

        val stock: Long,

        @field:NotBlank(message = "상품코드는 필수입니다.")
        val code: String,
    )
}
