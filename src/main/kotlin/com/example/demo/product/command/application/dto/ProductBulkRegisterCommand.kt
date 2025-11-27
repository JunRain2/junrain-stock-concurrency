package com.example.demo.product.command.application.dto

import com.example.demo.product.command.ui.dto.BulkRegisterProductRequest

data class ProductBulkRegisterCommand(
    val ownerId: Long, val products: List<RegisterProduct>
) {
    companion object {
        const val CHUNK_MAX_SIZE = 5000

        fun of(ownerId: Long, request: BulkRegisterProductRequest): ProductBulkRegisterCommand {
            val registerProduct = request.products.map {
                RegisterProduct(
                    name = it.name, price = it.price, stock = it.stock, code = it.code
                )
            }

            return ProductBulkRegisterCommand(
                ownerId = ownerId, products = registerProduct
            )
        }
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