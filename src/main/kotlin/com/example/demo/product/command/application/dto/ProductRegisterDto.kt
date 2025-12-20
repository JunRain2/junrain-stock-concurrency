package com.example.demo.product.command.application.dto

class ProductRegisterDto {
    class Command {
        data class Register(
            val ownerId: Long,
            val name: String,
            val price: Long,
            val stock: Long,
            val code: String
        )

        data class BulkRegister(
            val ownerId: Long,
            val products: List<RegisterProduct>
        ) {
            companion object {
                const val CHUNK_MAX_SIZE = 5000
            }

            init {
                require(products.size in (1..CHUNK_MAX_SIZE)) { "데이터는 하나 이상 5000개 이하여야 합니다." }
            }

            data class RegisterProduct(
                val name: String,
                val price: Long,
                val stock: Long,
                val code: String
            )
        }
    }

    class Result {
        data class Register(
            val productId: Long
        )

        data class BulkRegister(
            val successCount: Int,
            val failureCount: Int,
            val failedProducts: List<FailedRegisterProduct>
        ) {
            data class FailedRegisterProduct(
                val index: Int,
                val cause: String
            )
        }
    }
}
