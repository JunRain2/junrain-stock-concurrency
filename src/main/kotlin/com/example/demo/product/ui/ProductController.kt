package com.example.demo.product.ui

import com.example.demo.product.application.ProductBulkRegisterService
import com.example.demo.product.application.ProductQueryService
import com.example.demo.product.application.ProductPurchaseService
import com.example.demo.product.application.ProductRegisterService
import com.example.demo.product.application.dto.command.ProductBulkRegisterCommand
import com.example.demo.product.application.dto.command.ProductRegisterCommand
import com.example.demo.product.application.dto.command.PurchaseProductCommand
import com.example.demo.product.ui.dto.request.BulkRegisterProductRequest
import com.example.demo.product.ui.dto.request.PurchaseProductRequest
import com.example.demo.product.ui.dto.request.RegisterProductRequest
import com.example.demo.product.ui.dto.response.BulkRegisterProductResponse
import com.example.demo.product.ui.dto.response.ProductDetailResponse
import com.example.demo.product.ui.dto.response.PurchaseProductResponse
import com.example.demo.product.ui.dto.response.RegisterProductResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/products")
class ProductController(
    private val productBulkRegisterService: ProductBulkRegisterService,
    private val productRegisterService: ProductRegisterService,
    private val productQueryService: ProductQueryService,
    private val productPurchaseService: ProductPurchaseService
) {
    @GetMapping("/{productId}")
    fun getProduct(
        @PathVariable productId: Long
    ): ResponseEntity<ProductDetailResponse> {
        val response = productQueryService.getProductDetail(productId)

        return ResponseEntity.ok(response)
    }

    @PostMapping
    fun registerProduct(
        @Valid @RequestBody request: RegisterProductRequest,
        @RequestParam ownerId: Long
    ): ResponseEntity<RegisterProductResponse> {
        val command = ProductRegisterCommand.of(ownerId, request)
        val response = productRegisterService.registerProduct(command)

        return ResponseEntity.ok(response)
    }

    @PostMapping("/bulk")
    fun registerProducts(
        @Valid @RequestBody request: BulkRegisterProductRequest,
        @RequestParam ownerId: Long
    ): ResponseEntity<BulkRegisterProductResponse> {
        val command = ProductBulkRegisterCommand.of(ownerId, request)
        val response = productBulkRegisterService.registerProducts(command)

        return ResponseEntity.ok(response)
    }

    @PostMapping("/{productId}/purchase")
    fun purchaseProduct(
        @PathVariable productId: Long,
        @Valid @RequestBody request: PurchaseProductRequest
    ): ResponseEntity<PurchaseProductResponse?> {
        val command = PurchaseProductCommand.of(productId, request)
        val response = productPurchaseService.decreaseStock(command)

        return ResponseEntity.ok(response)
    }
}