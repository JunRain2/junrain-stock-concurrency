package com.junrain.stock.ui.product

import com.junrain.stock.application.product.RegisterProducts
import com.junrain.stock.ui.common.ApiResponse
import com.junrain.stock.ui.product.dto.ProductRegisterDto
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/products")
class ProductCommandController(
    private val registerProducts: RegisterProducts,
) {
    @PostMapping("/bulk")
    fun registerProducts(
        @Valid @RequestBody request: ProductRegisterDto.Request.BulkRegister,
        @RequestParam ownerId: Long,
    ): ApiResponse<ProductRegisterDto.Response.BulkRegister> {
        val command =
            RegisterProducts.Command(
                ownerId = ownerId,
                products =
                    request.products.map {
                        RegisterProducts.Command.RegisterProduct(
                            name = it.name,
                            price = it.price,
                            stock = it.stock,
                            code = it.code,
                        )
                    },
            )
        val result = registerProducts(command)
        val response = ProductRegisterDto.Response.BulkRegister.from(result)

        return ApiResponse.ok(response)
    }
}
