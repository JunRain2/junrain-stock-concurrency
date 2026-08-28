package com.junrain.stock.domain.product.exception

import com.junrain.stock.domain.common.BusinessException
import com.junrain.stock.domain.common.ErrorCode

/**
 * 재고 점유 실패 - 재시도해도 결과가 같다. 요청이 현재 재고 상태와 맞지 않는다.
 *
 * 상품 없음 / 재고 부족을 구분하지 않는다. 사용자에게는 실패 사실만 나가고,
 * [reason]은 throw 지점의 로그로만 남는다. 응답 본문에는 [ErrorCode.STOCK_UNAVAILABLE]의 메시지가 실린다.
 */
class StockUnavailableException(
    val reason: String,
) : BusinessException(ErrorCode.STOCK_UNAVAILABLE)

/**
 * 재고 점유 실패 - 인프라 일시 장애(데드락 희생, 락 대기 만료, DB 일시 접근 불가).
 *
 * 재고 상태는 멀쩡하므로 같은 요청을 다시 보내면 성공할 수 있다.
 */
class StockUnstableException(
    val reason: String,
    cause: Throwable,
) : BusinessException(ErrorCode.STOCK_UNSTABLE) {
    init {
        initCause(cause)
    }
}
