package com.junrain.stock.infra.product.mysql

import com.junrain.stock.domain.product.Product
import com.junrain.stock.domain.product.vo.ProductCode
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface JpaProductRepository : JpaRepository<Product, Long> {
    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock + :quantity WHERE p.id = :productId AND p.stock + :quantity >= 0")
    fun updateProductStock(
        @Param("productId") productId: Long,
        @Param("quantity") quantity: Long,
    )

    fun findByCreatedAtAndCodeIn(
        createdAt: LocalDateTime,
        codes: List<ProductCode>,
    ): List<Product>
}
