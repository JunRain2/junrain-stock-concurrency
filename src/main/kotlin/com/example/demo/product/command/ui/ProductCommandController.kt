package com.example.demo.product.command.ui

import com.example.demo.global.contract.ApiResponse
import com.example.demo.product.command.application.ProductOrderService
import com.example.demo.product.command.application.ProductRegisterService
import com.example.demo.product.command.application.dto.ProductOrderDto as AppProductOrderDto
import com.example.demo.product.command.application.dto.ProductPurchaseDto as AppProductPurchaseDto
import com.example.demo.product.command.application.dto.ProductRegisterDto as AppProductRegisterDto
import com.example.demo.product.command.ui.dto.ProductOrderDto as UiProductOrderDto
import com.example.demo.product.command.ui.dto.ProductPurchaseDto as UiProductPurchaseDto
import com.example.demo.product.command.ui.dto.ProductRegisterDto as UiProductRegisterDto
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

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

    @PostMapping("/purchase")
    fun purchaseProducts(
        @Valid @RequestBody request: UiProductPurchaseDto.Request.BulkPurchase
    ): ApiResponse<UiProductPurchaseDto.Response.BulkPurchase> {
        val commands = request.items.map { item ->
            AppProductPurchaseDto.Command.Purchase(
                productId = item.productId,
                amount = item.quantity
            )
        }

        val results = productOrderService.reserveProductStock(commands)
        val response = UiProductPurchaseDto.Response.BulkPurchase.from(results)

        return ApiResponse.ok(response)
    }

    @PostMapping("/stock/reserve")
    fun reserveStock(
        @Valid @RequestBody request: UiProductOrderDto.Request.ReserveStock
    ): ApiResponse<UiProductOrderDto.Response.ReserveStock> {
        val command = AppProductOrderDto.Command.ReserveStockCommand(
            productId = request.productId,
            quantity = request.quantity
        )

        val result = productOrderService.reserveStock(command)
        val response = UiProductOrderDto.Response.ReserveStock.from(result)

        return ApiResponse.ok(response)
    }

    @PostMapping("/stock/reserve/cancel")
    fun cancelReservation(
        @Valid @RequestBody request: UiProductOrderDto.Request.CancelReservation
    ): ApiResponse<UiProductOrderDto.Response.CancelReservation> {
        val command = AppProductOrderDto.Command.CancelReservation(
            productId = request.productId,
            quantity = request.quantity
        )

        val result = productOrderService.cancelReservation(command)
        val response = UiProductOrderDto.Response.CancelReservation.from(result)

        return ApiResponse.ok(response)
    }

    @PostMapping("/order")
    fun orderProducts(
        @Valid @RequestBody request: UiProductOrderDto.Request.OrderProducts
    ): ApiResponse<UiProductOrderDto.Response.OrderProducts> {
        val command = AppProductOrderDto.Command.OrderProducts(
            productId = request.productId,
            quantity = request.quantity
        )

        val result = productOrderService.order(command)
        val response = UiProductOrderDto.Response.OrderProducts.from(result)

        return ApiResponse.ok(response)
    }

    @PostMapping("/order/cancel")
    fun cancelOrder(
        @Valid @RequestBody request: UiProductOrderDto.Request.CancelOrder
    ): ApiResponse<UiProductOrderDto.Response.CancelOrder> {
        val command = AppProductOrderDto.Command.CancelOrder(
            productId = request.productId,
            quantity = request.quantity
        )

        val result = productOrderService.cancelOrder(command)
        val response = UiProductOrderDto.Response.CancelOrder.from(result)

        return ApiResponse.ok(response)
    }
}