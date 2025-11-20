package com.example.demo.global.contract

import org.springframework.validation.BindingResult

data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val meta: Meta? = null,
) {
    data class Meta(
        val errorCode: ErrorCode,
        val errorMessage: String?,
        val errors: List<FieldError>?
    ) {
        data class FieldError(
            val field: String,
            val value: String?,
            val reason: String?
        )
    }

    companion object {
        fun <T> ok(data: T? = null): ApiResponse<T> {
            return ApiResponse(success = true, data = data)
        }

        fun fail(
            errorCode: ErrorCode,
            errorMessage: String?,
            errors: BindingResult? = null
        ): ApiResponse<Any?> {
            val fieldErrors = errors?.fieldErrors?.map {
                Meta.FieldError(
                    field = it.field,
                    value = it.rejectedValue?.toString(),
                    reason = it.defaultMessage
                )
            }

            return ApiResponse(
                success = false,
                data = null,
                meta = Meta(
                    errorCode = errorCode,
                    errorMessage = errorMessage,
                    errors = fieldErrors
                )
            )
        }
    }
}