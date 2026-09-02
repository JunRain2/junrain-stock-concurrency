package com.junrain.stock.infra.product.mysql

import com.junrain.stock.domain.product.Product
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface JpaProductRepository : JpaRepository<Product, Long> {
    /**
     * 재고를 원자적으로 증감한다. 재고가 모자라면 WHERE 조건에 걸려 0을 반환한다.
     *
     * @return 갱신된 행 수 (0 = 재고 부족). Spring Data JPA는 void/int/long만 허용한다
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Product p SET p.stock = p.stock + :quantity WHERE p.id = :productId AND p.stock + :quantity >= 0")
    fun updateProductStock(
        @Param("productId") productId: Long,
        @Param("quantity") quantity: Long,
    ): Int
}
