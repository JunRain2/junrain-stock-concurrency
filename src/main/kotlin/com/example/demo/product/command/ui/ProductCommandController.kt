package com.example.demo.product.command.ui

import com.example.demo.global.contract.ApiResponse
import com.example.demo.product.command.application.ProductBulkRegisterService
import com.example.demo.product.command.application.ProductPurchaseService
import com.example.demo.product.command.application.ProductRegisterService
import com.example.demo.product.command.application.dto.request.ProductBulkRegisterCommand
import com.example.demo.product.command.application.dto.request.ProductRegisterCommand
import com.example.demo.product.command.application.dto.request.PurchaseProductCommand
import com.example.demo.product.command.ui.dto.request.BulkRegisterProductRequest
import com.example.demo.product.command.ui.dto.request.PurchaseProductRequest
import com.example.demo.product.command.ui.dto.request.RegisterProductRequest
import com.example.demo.product.command.ui.dto.response.BulkRegisterProductResponse
import com.example.demo.product.command.ui.dto.response.PurchaseProductResponse
import com.example.demo.product.command.ui.dto.response.RegisterProductResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/products")
class ProductCommandController(
    private val productBulkRegisterService: ProductBulkRegisterService,
    private val productRegisterService: ProductRegisterService,
    private val productPurchaseService: ProductPurchaseService
) {
    @PostMapping
    fun registerProduct(
        @Valid @RequestBody request: RegisterProductRequest,
        @RequestParam ownerId: Long
    ): ApiResponse<RegisterProductResponse> {
        val command = ProductRegisterCommand.of(ownerId, request)
        val response = productRegisterService.registerProduct(command)

        return ApiResponse.ok(response)
    }

    @PostMapping("/bulk")
    fun registerProducts(
        @Valid @RequestBody request: BulkRegisterProductRequest,
        @RequestParam ownerId: Long
    ): ApiResponse<BulkRegisterProductResponse> {
        val command = ProductBulkRegisterCommand.of(ownerId, request)
        val response = productBulkRegisterService.registerProducts(command)

        return ApiResponse.ok(response)
    }

    @PostMapping("/{productId}/purchase")
    fun purchaseProduct(
        @PathVariable productId: Long,
        @Valid @RequestBody request: PurchaseProductRequest
    ): ApiResponse<PurchaseProductResponse?> {
        val command = PurchaseProductCommand.of(productId, request)
        val response = productPurchaseService.decreaseStock(command)

        return ApiResponse.ok(response)
    }
}