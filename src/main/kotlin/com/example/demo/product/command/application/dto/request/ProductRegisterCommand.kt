package com.example.demo.product.command.application.dto.request

import com.example.demo.product.command.ui.dto.request.RegisterProductRequest

data class ProductRegisterCommand(
    val ownerId: Long,
    val name: String,
    val price: Long,
    val stock: Long,
    val code: String
) {
    companion object {
        fun of(ownerId: Long, request: RegisterProductRequest): ProductRegisterCommand {
            return ProductRegisterCommand(
                ownerId = ownerId,
                name = request.name,
                price = request.price,
                stock = request.stock,
                code = request.code
            )
        }
    }
}