package com.junrain.stock.domain.product

interface ProductRepository {
    fun save(product: Product): Product

    /**
     * 여러 상품을 한 번에 삽입한다. 부분 성공을 허용한다 - 일부가 실패해도 나머지는 커밋된다.
     *
     * @return 입력과 같은 길이, 같은 순서. i번째 원소가 i번째 입력 행의 결과다.
     *   성공이면 생성된 상품 id, 실패면 원인 예외
     */
    fun saveAll(products: List<Product>): List<Result<Long>>

    fun findById(productId: Long): Product

    fun findAllByIds(productIds: List<Long>): List<Product>
}
