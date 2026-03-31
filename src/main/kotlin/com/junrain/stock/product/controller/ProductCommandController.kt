package com.junrain.stock.product.controller

import com.junrain.stock.common.dto.ApiResponse
import com.junrain.stock.product.application.ProductRegisterService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import com.junrain.stock.product.application.command.ProductRegisterDto as AppProductRegisterDto
import com.junrain.stock.product.controller.dto.ProductRegisterDto as UiProductRegisterDto

@RestController
@RequestMapping("/api/v1/products")
class ProductCommandController(
    private val productRegisterService: ProductRegisterService,
) {
    @PostMapping("/bulk")
    fun registerProducts(
        @Valid @RequestBody request: UiProductRegisterDto.Request.BulkRegister,
        @RequestParam ownerId: Long,
    ): ApiResponse<UiProductRegisterDto.Response.BulkRegister> {
        val command =
            AppProductRegisterDto.Command.BulkRegister(
                ownerId = ownerId,
                products =
                    request.products.map {
                        AppProductRegisterDto.Command.BulkRegister.RegisterProduct(
                            name = it.name,
                            price = it.price,
                            stock = it.stock,
                            code = it.code,
                        )
                    },
            )
        val result = productRegisterService.registerProducts(command)
        val response = UiProductRegisterDto.Response.BulkRegister.from(result)

        return ApiResponse.ok(response)
    }
}
