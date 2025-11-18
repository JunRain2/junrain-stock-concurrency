package com.example.demo.ui

import com.example.demo.application.BulkInsertProductService
import com.example.demo.ui.dto.request.RegisterProductRequest
import com.example.demo.ui.dto.response.RegisterProductResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/products")
class ProductController(
    private val registerProductService: BulkInsertProductService
) {

    @PostMapping
    fun registerProducts(
        @Valid @RequestBody request: RegisterProductRequest
    ): ResponseEntity<RegisterProductResponse?> {
        val response = registerProductService.registerProducts(request)

        return ResponseEntity.ok(response)
    }
}