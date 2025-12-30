package com.junrain.stock.order.command.application

import org.springframework.stereotype.Service

@Service
class OrderPlacementService() {
    fun placeAnOrder(cartItemIds: List<Long>) {
        // cart에서 product 가져오기 -> cart에 존재하는 데이터는 삭제
        // product에 stock 선점하기 -> Lock 활용하기
        // 외부 API를 활용해서 주문 요청하기 -> 인터페이스를 통해서 구현하기
    }
}