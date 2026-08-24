# 재고 점유 인프라 설계 — MySQL 단일 UPDATE(CASE)

| | |
|---|---|
| 대상 | `StockWriter` 포트의 MySQL 구현. UPDATE 한 문장에 CASE로 상품별 증감을 묶는 전략 |
| 구현체 | `MySqlStockWriterImpl`, `JdbcProductRepository.applyStockDeltas` |
| 관련 문서 | [비즈니스 규칙](01-비즈니스-규칙.md) |

`StockWriter`는 여러 구현체를 가질 수 있다. 이 문서는 그중 하나(MySQL 단일 UPDATE 전략)만 다룬다. 다른 전략(MySQL SKIP LOCKED, Redis 등)은 각각 별도 문서로 추가한다 — 이 문서를 고쳐 쓰지 않는다.

## 목차

1. [범위](#1-범위)
2. [설계 목표](#2-설계-목표)
3. [UPDATE 문 설계](#3-update-문-설계)
4. [정렬 규칙](#4-정렬-규칙)
5. [실패 판정](#5-실패-판정)
6. [예외 변환](#6-예외-변환)
7. [실패 원인 로깅](#7-실패-원인-로깅)

---

## 1. 범위

`StockWriter`의 유일한 완성 구현이자 현재 유일하게 사용 중인 전략이다. 같은 포트를 만족하는 다른 전략(MySQL `SKIP LOCKED`, Redis 기반 `RedisStockWriterImpl` — 현재 `TODO` 미구현)은 이 문서의 범위 밖이며, 구현되는 시점에 각자 별도 문서로 추가한다.

전략 간 공통 계약(재시도 가능 여부, 실패 시 사용자 응답)은 [비즈니스 규칙](01-비즈니스-규칙.md)이 정의한다. 이 문서를 포함한 구현별 문서는 그 계약을 "어떻게" 지키는지만 다룬다.

## 2. 설계 목표

여러 상품의 재고를 한 요청 안에서 증감시킬 때, 상품 수만큼 DB를 왕복하지 않고 UPDATE 한 번으로 처리한다. 상품별 조건 판정(재고 부족 시 해당 행만 갱신 제외)과 원자성(전부 성공 또는 전부 실패)을 애플리케이션 코드의 재시도나 락 없이 SQL 자체의 성질로 보장한다.

## 3. UPDATE 문 설계

```sql
UPDATE products
SET stock = stock + CASE id WHEN ? THEN ? WHEN ? THEN ? ... END
WHERE id IN (?, ?, ...)
  AND stock + CASE id WHEN ? THEN ? WHEN ? THEN ? ... END >= 0
```

상품 ID → 증감량(`delta`)을 `CASE`로 한 문장에 담는다. 감소(`decrease`)는 `delta`를 음수로, 증가(`increase`)는 양수로 넘길 뿐 `JdbcProductRepository` 입장에서는 같은 SQL이다.

- `SET`의 `CASE`가 상품별로 다른 증감량을 한 번에 적용한다
- `WHERE`의 두 번째 `CASE`가 갱신 후 값이 음수가 되는 행을 조건에서 제외한다 — 재고가 모자란 상품은 애초에 갱신 대상에서 빠진다
- 반환되는 갱신 행 수가 요청한 상품 수보다 작으면, 그 차이만큼 조건에 걸린(재고 부족 또는 존재하지 않는) 상품이 있다는 뜻이다. 어떤 상품인지는 이 시점에 알 수 없다 — [7절](#7-실패-원인-로깅) 참고

증가(`increase`)에서는 재고 부족 조건이 걸릴 일이 없다(늘리기만 하므로 결과가 음수가 되지 않는다). 그런데도 행 수가 모자라다면 원인은 하나뿐이다: 상품 ID 자체가 `products` 테이블에 없다.

## 4. 정렬 규칙

`applyStockDeltas`는 실행 전에 `deltas`를 상품 ID 오름차순으로 정렬한다. 이유는 두 가지다.

1. **문 캐시(statement cache) 재사용** — 같은 상품 조합이라도 순서가 바뀌면 SQL 텍스트(파라미터 자리 수는 같아도 바인딩 순서가 다른 논리적 실행 계획)가 달라진다고 볼 여지가 생긴다. 정렬로 SQL을 결정적으로 만든다.
2. **데드락 방지** — 여러 트랜잭션이 같은 상품 집합을 서로 다른 순서로 갱신하면 상호 대기(A→B, B→A)가 발생한다. 단일 UPDATE가 PK 인덱스를 항상 오름차순으로 스캔하도록 정렬해두면, 모든 트랜잭션이 같은 순서로 행을 잠그므로 상호 대기 자체가 성립하지 않는다.

## 5. 실패 판정

같은 상품이 요청에 여러 번 들어오면 `CASE`는 첫 값만 적용되고 `IN`도 상품마다 한 번씩만 등장한다. 이 상태로 실행하면 "갱신 행 수 < 요청 수"가 되어 정상 실패 경로와 구분되지 않고, 자칫 여러 delta가 조용히 합산된 것으로 오해할 수 있다.

그래서 `applyStockDeltas`는 SQL을 만들기 전에 상품 ID 중복을 검사하고, 중복이면 `IllegalArgumentException`으로 즉시 막는다. 이 검증은 `ReserveProducts.Command`의 중복 검증([비즈니스 규칙 3절](01-비즈니스-규칙.md#3-입력-규칙))과 같은 규칙을 인프라 계층에서 한 번 더 확인하는 것이다 — 포트를 직접 호출하는 다른 경로가 생기더라도 이 불변식은 깨지지 않는다.

## 6. 예외 변환

`MySqlStockWriterImpl.applyOrThrow`가 두 가지 실패를 한곳에서 구분한다. 증감 양쪽이 같은 UPDATE를 타므로 위험도 동일하다.

```
try {
    updated = jdbcProductRepository.applyStockDeltas(deltas)
} catch (CannotAcquireLockException) {
    → StockUnstableException (재시도 가능)
}

if (updated != deltas.size) {
    → onShortfall() 실행 (증감마다 다른 예외)
}
```

`updated != deltas.size` 판정은 반드시 `try` 블록 밖에서 한다. `onShortfall()`이 던지는 예외(`StockUnavailableException` 또는 `ProductNotFoundException`)가 같은 `try` 안에 있으면 `catch (CannotAcquireLockException)`과는 무관하지만, 판정 로직을 락 실패 처리와 뒤섞으면 두 실패 원인이 코드상 한 분기처럼 보여 유지보수 중 오해를 낳기 쉽다. 분리해두면 "락 실패"와 "조건 미달"이 서로 다른 코드 경로라는 것이 그대로 드러난다.

## 7. 실패 원인 로깅

행 수 부족만으로는 "어떤 상품이 왜 실패했는지" 알 수 없다. `decrease` 실패 시에만 `diagnose()`가 추가 조회(`JpaProductRepository.findAllById`)를 돌려 상품별로 "상품없음" 또는 "재고부족(요청/현재)" 문자열을 만들어 로그에 남긴다.

이 조회는 같은 트랜잭션 안에서 실행된다. 즉 이미 갱신에 성공한 다른 상품은 감소가 반영된 값으로 조회된다 — 실패 지점을 정확히 지목하는 판정 근거가 아니라, 사후 원인 추정을 돕는 참고 정보다. 사용자 응답에는 노출되지 않고 로그 전용이다([비즈니스 규칙 5절](01-비즈니스-규칙.md#5-실패-분류)).

`increase` 실패 시에는 `diagnose()`를 돌리지 않는다 — 원인이 "없는 상품"으로 이미 확정이므로 추가 조회가 필요 없다.
