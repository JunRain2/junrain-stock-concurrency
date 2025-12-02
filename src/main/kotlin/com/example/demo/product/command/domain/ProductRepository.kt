package com.example.demo.product.command.domain

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductRepository : JpaRepository<Product, Long>, ProductBulkInsertRepository {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product AS p WHERE p.id IN :ids ORDER BY p.id")
    fun findAllByIdsWithLock(@Param("ids") ids: List<Long>): List<Product>
}