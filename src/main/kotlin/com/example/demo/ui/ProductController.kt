package com.example.demo.ui

import com.example.demo.application.BulkInsertProductService
import com.example.demo.application.ProductService
import com.example.demo.ui.dto.request.BulkRegisterProductRequest
import com.example.demo.ui.dto.request.PurchaseProductRequest
import com.example.demo.ui.dto.request.RegisterProductRequest
import com.example.demo.ui.dto.response.BulkRegisterProductResponse
import com.example.demo.ui.dto.response.GetProductResponse
import com.example.demo.ui.dto.response.PurchaseProductResponse
import com.example.demo.ui.dto.response.RegisterProductResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/products")
class ProductController(
    private val registerProductService: BulkInsertProductService, private val productService: ProductService
) {
    @GetMapping("/{productId}")
    fun getProduct(
        @PathVariable productId: Long
    ): ResponseEntity<GetProductResponse> {
        val response = productService.getProduct(productId)

        return ResponseEntity.ok(response)
    }

    @PostMapping
    fun registerProduct(@Valid @RequestBody request: RegisterProductRequest): ResponseEntity<RegisterProductResponse> {
        val response = productService.registerProduct(request)

        return ResponseEntity.ok(response)
    }

    @PostMapping("/bulk")
    fun registerProducts(
        @Valid @RequestBody request: BulkRegisterProductRequest
    ): ResponseEntity<BulkRegisterProductResponse?> {

        val response = registerProductService.registerProducts(request)

        return ResponseEntity.ok(response)
    }

    @PostMapping("/{productId}/purchase")
    fun purchaseProduct(
        @PathVariable productId: Long,
        request: PurchaseProductRequest
    ): ResponseEntity<PurchaseProductResponse?> {
        val response = productService.decreaseStock(productId, request)

        return ResponseEntity.ok(response)
    }
}