package com.example.demo.product.command.ui

import com.example.demo.global.contract.ApiResponse
import com.example.demo.product.command.application.ProductBulkRegisterService
import com.example.demo.product.command.application.ProductPurchaseService
import com.example.demo.product.command.application.ProductRegisterService
import com.example.demo.product.command.application.dto.ProductBulkRegisterCommand
import com.example.demo.product.command.application.dto.ProductRegisterCommand
import com.example.demo.product.command.application.dto.PurchaseProductCommand
import com.example.demo.product.command.ui.dto.BulkRegisterProductRequest
import com.example.demo.product.command.ui.dto.PurchaseProductRequest
import com.example.demo.product.command.ui.dto.RegisterProductRequest
import com.example.demo.product.command.ui.dto.BulkRegisterProductResponse
import com.example.demo.product.command.ui.dto.PurchaseProductResponse
import com.example.demo.product.command.ui.dto.ProductRegisterResponse
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
    ): ApiResponse<ProductRegisterResponse> {
        val command = ProductRegisterCommand.of(ownerId, request)
        val result = productRegisterService.registerProduct(command)
        val response = ProductRegisterResponse.from(result)

        return ApiResponse.ok(response)
    }

    @PostMapping("/bulk")
    fun registerProducts(
        @Valid @RequestBody request: BulkRegisterProductRequest,
        @RequestParam ownerId: Long
    ): ApiResponse<BulkRegisterProductResponse> {
        val command = ProductBulkRegisterCommand.of(ownerId, request)
        val result = productBulkRegisterService.registerProducts(command)
        val response = BulkRegisterProductResponse.from(result)

        return ApiResponse.ok(response)
    }

    @PostMapping("/{productId}/purchase")
    fun purchaseProduct(
        @PathVariable productId: Long,
        @Valid @RequestBody request: PurchaseProductRequest
    ): ApiResponse<PurchaseProductResponse> {
        val command = PurchaseProductCommand.of(productId, request)
        val result = productPurchaseService.decreaseStock(command)
        val response = PurchaseProductResponse.from(result)

        return ApiResponse.ok(response)
    }
}