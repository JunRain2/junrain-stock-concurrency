package com.example.demo.product.command.ui.dto

import com.example.demo.product.command.application.dto.ProductRegisterDto as AppProductRegisterDto
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty

class ProductRegisterDto {
    class Request {
        data class Register(
            @field:NotBlank(message = "상품명은 필수입니다")
            val name: String,

            @field:Min(1, message = "가격은 반드시 양수여야 합니다.")
            val price: Long,

            @field:Min(1, message = "수량은 반드시 양수여야 합니다.")
            val stock: Long,

            @field:NotBlank(message = "상품코드는 필수입니다.")
            val code: String,
        )

        /**
         * 요구사항에 부분 성공이 존재
         * @Valid를 통해서 엄격하게 검사를 할 경우 전체 요청이 들어오지 않아 전체 실패
         * 따라서, 빈 값, 데이터 형식만 검사
         */
        data class BulkRegister(
            @field:Valid
            @field:NotEmpty(message = "상품은 최소 1개 이상 등록해야 합니다")
            val products: List<RegisterProduct>
        ) {
            data class RegisterProduct(
                @field:NotBlank(message = "상품명은 필수입니다")
                val name: String,

                val price: Long,

                val stock: Long,

                @field:NotBlank(message = "상품코드는 필수입니다.")
                val code: String,
            )
        }
    }

    class Response {
        data class Register(
            val productId: Long
        ) {
            companion object {
                fun from(result: AppProductRegisterDto.Result.Register): Register {
                    return Register(productId = result.productId)
                }
            }
        }

        data class BulkRegister(
            val successCount: Int,
            val failureCount: Int,
            val failedProducts: List<FailedRegisterProduct>
        ) {
            data class FailedRegisterProduct(
                val code: String,
                val message: String
            )

            companion object {
                fun from(result: AppProductRegisterDto.Result.BulkRegister): BulkRegister {
                    return BulkRegister(
                        successCount = result.successCount,
                        failureCount = result.failureCount,
                        failedProducts = result.failedProducts.map {
                            FailedRegisterProduct(
                                code = it.code,
                                message = it.cause ?: "UNKNOWN"
                            )
                        }
                    )
                }
            }
        }
    }
}
