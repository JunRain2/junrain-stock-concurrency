package com.example.demo.domain.product

import com.example.demo.domain.product.vo.ProductCode
import org.springframework.data.jpa.repository.JpaRepository

interface ProductRepository : JpaRepository<Product, Long>, BulkInsertProductRepository {
    fun existsByCode(code: ProductCode): Boolean
}