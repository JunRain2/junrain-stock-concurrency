package com.example.demo.product.command.domain

import org.springframework.data.jpa.repository.JpaRepository

interface ProductRepository : JpaRepository<Product, Long>, ProductBulkInsertRepository {
}