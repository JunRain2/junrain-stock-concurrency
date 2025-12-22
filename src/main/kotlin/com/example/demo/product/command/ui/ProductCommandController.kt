package com.example.demo.product.command.ui

import com.example.demo.global.contract.ApiResponse
import com.example.demo.product.command.application.ProductOrderService
import com.example.demo.product.command.application.ProductRegisterService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import com.example.demo.product.command.application.dto.ProductPurchaseDto as AppProductPurchaseDto
import com.example.demo.product.command.application.dto.ProductRegisterDto as AppProductRegisterDto
import com.example.demo.product.command.ui.dto.ProductPurchaseDto as UiProductPurchaseDto
import com.example.demo.product.command.ui.dto.ProductRegisterDto as UiProductRegisterDto

@RestController
@RequestMapping("/api/v1/products")
class ProductCommandController(
    private val productRegisterService: ProductRegisterService,
    private val productOrderService: ProductOrderService
) {
    @PostMapping("/bulk")
    fun registerProducts(
        @Valid @RequestBody request: UiProductRegisterDto.Request.BulkRegister,
        @RequestParam ownerId: Long
    ): ApiResponse<UiProductRegisterDto.Response.BulkRegister> {
        val command = AppProductRegisterDto.Command.BulkRegister(
            ownerId = ownerId,
            products = request.products.map {
                AppProductRegisterDto.Command.BulkRegister.RegisterProduct(
                    name = it.name,
                    price = it.price,
                    stock = it.stock,
                    code = it.code
                )
            }
        )
        val result = productRegisterService.registerProducts(command)
        val response = UiProductRegisterDto.Response.BulkRegister.from(result)

        return ApiResponse.ok(response)
    }

    @PostMapping("/reserve")
    fun reserveProducts(
        @Valid @RequestBody request: UiProductPurchaseDto.Request.BulkPurchase
    ): ApiResponse<UiProductPurchaseDto.Response.BulkPurchase> {
        val commands = request.items.map {
            AppProductPurchaseDto.Command.Purchase(
                productId = it.productId,
                amount = it.quantity
            )
        }

        val results = productOrderService.reserveProducts(commands)
        val response = UiProductPurchaseDto.Response.BulkPurchase.from(results)

        return ApiResponse.ok(response)
    }

    @PostMapping("/reserve/cancel")
    fun cancelReservationProducts(
        @Valid @RequestBody request: UiProductPurchaseDto.Request.BulkPurchase
    ): ApiResponse<UiProductPurchaseDto.Response.BulkPurchase> {
        val commands = request.items.map {
            AppProductPurchaseDto.Command.Purchase(
                productId = it.productId,
                amount = it.quantity
            )
        }

        val results = productOrderService.cancelReservationProducts(commands)
        val response = UiProductPurchaseDto.Response.BulkPurchase.from(results)

        return ApiResponse.ok(response)
    }

    @PostMapping("/order")
    fun orderProducts(
        @Valid @RequestBody request: UiProductPurchaseDto.Request.BulkPurchase
    ): ApiResponse<UiProductPurchaseDto.Response.BulkPurchase> {
        val commands = request.items.map {
            AppProductPurchaseDto.Command.Purchase(
                productId = it.productId,
                amount = it.quantity
            )
        }

        val results = productOrderService.orderProducts(commands)
        val response = UiProductPurchaseDto.Response.BulkPurchase.from(results)

        return ApiResponse.ok(response)
    }

    @PostMapping("/order/cancel")
    fun cancelOrderProducts(
        @Valid @RequestBody request: UiProductPurchaseDto.Request.BulkPurchase
    ): ApiResponse<UiProductPurchaseDto.Response.BulkPurchase> {
        val commands = request.items.map {
            AppProductPurchaseDto.Command.Purchase(
                productId = it.productId,
                amount = it.quantity
            )
        }

        val results = productOrderService.cancelOrderProducts(commands)
        val response = UiProductPurchaseDto.Response.BulkPurchase.from(results)

        return ApiResponse.ok(response)
    }
}