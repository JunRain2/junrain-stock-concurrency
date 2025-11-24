package com.example.demo.product.command.application.dto.request

import com.example.demo.product.command.ui.dto.request.BulkRegisterProductRequest

data class ProductBulkRegisterCommand(
    val ownerId: Long,
    val products: List<RegisterProduct>
) {
    data class RegisterProduct(
        val name: String,
        val price: Long,
        val stock: Long,
        val code: String
    )

    companion object {
        fun of(ownerId: Long, request: BulkRegisterProductRequest): ProductBulkRegisterCommand {
            val registerProduct = request.products.map {
                RegisterProduct(
                    name = it.name,
                    price = it.price,
                    stock = it.stock,
                    code = it.code
                )
            }

            return ProductBulkRegisterCommand(
                ownerId = ownerId,
                products = registerProduct
            )
        }
    }
}