package com.example.demo.product.command.infrastructure.mysql

import com.example.demo.product.command.domain.Product
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface JpaProductRepository : JpaRepository<Product, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product AS p WHERE p.id IN :ids ORDER BY p.id")
    fun findAllByIdsWithLock(@Param("ids") ids: List<Long>): List<Product>

    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock + :quantity WHERE p.id = :productId AND p.stock + :quantity >= 0")
    fun updateProductStock(@Param("productId") productId: Long, @Param("quantity") quantity: Long)
}