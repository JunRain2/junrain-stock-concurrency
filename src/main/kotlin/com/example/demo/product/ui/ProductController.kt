package com.example.demo.product.ui

import com.example.demo.product.application.BulkRegisterProductService
import com.example.demo.product.application.GetProductService
import com.example.demo.product.application.PurchaseProductService
import com.example.demo.product.application.RegisterProductService
import com.example.demo.product.ui.dto.request.BulkRegisterProductRequest
import com.example.demo.product.ui.dto.request.PurchaseProductRequest
import com.example.demo.product.ui.dto.request.RegisterProductRequest
import com.example.demo.product.ui.dto.response.BulkRegisterProductResponse
import com.example.demo.product.ui.dto.response.GetProductResponse
import com.example.demo.product.ui.dto.response.PurchaseProductResponse
import com.example.demo.product.ui.dto.response.RegisterProductResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/products")
class ProductController(
    private val bulkRegisterProductService: BulkRegisterProductService,
    private val registerProductService: RegisterProductService,
    private val getProductService: GetProductService,
    private val purchaseProductService: PurchaseProductService
) {
    @GetMapping("/{productId}")
    fun getProduct(
        @PathVariable productId: Long
    ): ResponseEntity<GetProductResponse> {
        val response = getProductService.getProduct(productId)

        return ResponseEntity.ok(response)
    }

    @PostMapping
    fun registerProduct(
        @Valid @RequestBody request: RegisterProductRequest
    ): ResponseEntity<RegisterProductResponse> {
        val response = registerProductService.registerProduct(request)

        return ResponseEntity.ok(response)
    }

    @PostMapping("/bulk")
    fun registerProducts(
        @Valid @RequestBody request: BulkRegisterProductRequest
    ): ResponseEntity<BulkRegisterProductResponse> {

        val response = bulkRegisterProductService.registerProducts(request)

        return ResponseEntity.ok(response)
    }

    @PostMapping("/{productId}/purchase")
    fun purchaseProduct(
        @PathVariable productId: Long,
        @Valid @RequestBody request: PurchaseProductRequest
    ): ResponseEntity<PurchaseProductResponse?> {
        val response = purchaseProductService.decreaseStock(productId, request)

        return ResponseEntity.ok(response)
    }
}