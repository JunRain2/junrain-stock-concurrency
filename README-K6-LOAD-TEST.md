# K6 부하 테스트 가이드 - Product Registration API

## 개요

이 테스트는 `/products` 엔드포인트의 대량 상품 등록 API에 대한 부하 테스트입니다.
사용자당 3000개의 상품 데이터를 등록하며, 비즈니스 제약사항을 고려하여 작성되었습니다.

## 비즈니스 제약사항

테스트는 다음 비즈니스 규칙을 반영합니다:

### Product 도메인 제약사항
- **상품명**: 필수, 20자 이하, 특수문자 불가, 한글/영문/숫자/공백만 허용
- **상품 코드**: 필수, unique (DB 레벨)
- **가격**: 0 이상
- **재고**: 0 이상

### API 동작 특성
- **부분 성공 지원**: 일부 상품이 실패해도 나머지는 저장됨
- **청크 처리**: 기본 10개씩 분할 처리 (`bulk-insert.chunk-size: 10`)
- **재시도 전략**: 일시적 DB 장애 시 2초, 5초 후 재시도 (`retry-milliseconds: 2000,5000`)
- **실패 원인**:
  - 비즈니스 로직 위반 (상품명 길이, 특수문자 등)
  - 중복 코드
  - 일시적 DB 장애

## 테스트 구성

### 부하 단계 (Stages)
```
1. Ramp-up 1: 30초 동안 10명까지 증가
2. Ramp-up 2: 1분 동안 50명까지 증가
3. Ramp-up 3: 2분 동안 100명까지 증가
4. Steady State: 3분 동안 100명 유지
5. Ramp-down: 1분 동안 0명으로 감소
```

총 테스트 시간: 7분 30초

### 성능 임계값 (Thresholds)
- **응답 시간 P95**: 5초 이내
- **에러율**: 10% 미만
- **요청 실패율**: 10% 미만

### 테스트 데이터
- **사용자당 상품 수**: 3000개
- **무효 데이터 비율**: 5% (비즈니스 로직 위반 케이스 포함)
- **무효 데이터 유형**:
  - 상품명 20자 초과
  - 특수문자 포함
  - 음수 재고
  - 빈 상품명

## 실행 방법

### 1. K6 설치
```bash
# macOS
brew install k6

# Linux
sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6

# Windows (Chocolatey)
choco install k6
```

### 2. 애플리케이션 실행
```bash
./gradlew bootRun
```

### 3. Prometheus로 메트릭 전송하며 테스트 실행 (권장)
```bash
# Prometheus Remote Write로 메트릭 전송
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
k6 run -o experimental-prometheus-rw k6-load-test-product-registration.js

# 빠른 테스트 (30초, VU 5명, 상품 100개)
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
k6 run -o experimental-prometheus-rw \
  --vus 5 --duration 30s \
  -e PRODUCTS_PER_REQUEST=100 \
  k6-load-test-product-registration.js
```

### 4. 기본 테스트 실행 (Prometheus 없이)
```bash
k6 run k6-load-test-product-registration.js
```

### 5. 커스텀 설정으로 실행
```bash
# Prometheus + 커스텀 상품 수
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
k6 run -o experimental-prometheus-rw \
  -e PRODUCTS_PER_REQUEST=5000 \
  k6-load-test-product-registration.js

# 짧은 부하 테스트
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
k6 run -o experimental-prometheus-rw \
  --vus 10 --duration 1m \
  -e PRODUCTS_PER_REQUEST=1000 \
  k6-load-test-product-registration.js
```

## 모니터링

### 실시간 콘솔 출력
테스트 실행 중 다음 정보가 출력됩니다:
- VU별 성공/실패 건수
- 성공률
- 실패한 상품 샘플 (처음 5개)

### 메트릭
- `http_req_duration`: 요청 응답 시간
- `errors`: 커스텀 에러율
- `partial_success`: 부분 성공 비율 (일부만 실패한 요청)
- `http_reqs`: 총 요청 수
- `http_req_failed`: 실패한 요청 비율

## 예상 결과

### 정상 시나리오
```
Success: 2850/3000 (95%)
Failure: 150/3000 (5%)
```
- 무효 데이터 5%가 실패로 처리됨
- P95 응답 시간이 5초 이내
- 에러율 10% 미만

### 실패 원인 분석
테스트 결과에서 다음과 같은 실패 메시지를 볼 수 있습니다:
- "상품명은 20자 이하여야 합니다"
- "상품명은 특수문자를 포함할 수 없습니다"
- "재고는 0개 이상이어야 합니다"
- "상품명은 필수입니다"
- 중복 코드 오류 (동시 요청 시 발생 가능)

## 데이터베이스 고려사항

### 청크 사이즈 튜닝
현재 설정: `bulk-insert.chunk-size: 10`

대량 데이터 처리 시 청크 사이즈를 조정할 수 있습니다:
```yaml
bulk-insert:
  chunk-size: 100  # 더 큰 배치 사이즈
  retry-milliseconds: 2000,5000
```

### 데이터베이스 초기화
부하 테스트 전 깨끗한 상태로 시작하려면:
```sql
TRUNCATE TABLE product;
```

또는 애플리케이션 재시작 (DDL auto: create 설정 시)

## 트러블슈팅

### 타임아웃 발생 시
- 청크 사이즈를 줄이기
- VU 수를 줄이기
- DB 커넥션 풀 크기 증가
- 타임아웃 설정 증가: 스크립트의 `timeout: '60s'` 수정

### 메모리 부족 시
- PRODUCTS_PER_REQUEST 값을 줄이기
- VU 수를 줄이기
- 애플리케이션 힙 메모리 증가: `-Xmx2g` 등

### 중복 코드 오류가 많이 발생하는 경우
- 정상 동작 (동시 요청으로 인한 경합)
- DB unique constraint로 인해 부분 실패 처리됨

## 결과 파일

테스트 완료 후 `summary.json` 파일이 생성되며, 상세한 메트릭 정보가 포함됩니다.

```bash
# 요약 확인
cat summary.json | jq '.metrics'
```