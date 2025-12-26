# K6 부하 테스트 모음

API별로 정리된 K6 부하 테스트 스크립트 모음입니다.

## 📁 디렉토리 구조

```
k6-tests/
├── common/                          # 공통 유틸리티
│   ├── common.js                    # 공통 메트릭, HTML 생성 함수
│   └── clear-redis.sh               # Redis 완전 초기화 스크립트
│
├── purchase/                        # 상품 구매 API 테스트
│   ├── step1-single-product.js      # Step 1: 단일 상품 경합 테스트
│   ├── step2-multiple-products.js   # Step 2: 다중 상품 분산 테스트
│   ├── step3-mixed-scenario.js      # Step 3: 혼합 시나리오 테스트
│   ├── step4-stock-depletion.js     # Step 4: 재고 소진 테스트
│   ├── test-data.sql                # 구매 테스트용 초기 데이터
│   └── init-redis-stock.sh          # Redis 재고 초기화 스크립트
│
├── registration/                    # 상품 등록 API 테스트
│   ├── step1-basic-performance.js   # Step 1: 기본 성능 측정
│   ├── step2-concurrent-brands.js   # Step 2: 동시성 테스트
│   ├── step3-extreme-load.js        # Step 3: 극한 상황 테스트
│   └── test-data-registration.sql   # 등록 테스트용 초기 데이터
│
├── results/                         # 테스트 결과 저장 디렉토리
│   ├── purchase/                    # 구매 API 테스트 결과
│   └── registration/                # 등록 API 테스트 결과
│
├── run-all-tests.sh                 # 구매 API 전체 테스트 실행 스크립트
├── run-registration-tests.sh        # 등록 API 전체 테스트 실행 스크립트
├── analyze.py                       # 결과 분석 Python 스크립트
└── README.md                        # 이 파일
```

## 🚀 빠른 시작

### 1. 사전 준비

```bash
# K6 설치 (macOS)
brew install k6

# 애플리케이션 실행
./gradlew bootRun
```

### 2. 테스트 실행

#### 🛒 상품 구매 API 테스트

```bash
# 테스트 데이터 삽입
mysql -u root -p1234 foo < k6-tests/purchase/test-data.sql

# 전체 테스트 실행 (약 32분 소요)
./k6-tests/run-all-tests.sh

# 개별 테스트 실행
k6 run --env BASE_URL=http://localhost:8080 k6-tests/purchase/step1-single-product.js
k6 run --env BASE_URL=http://localhost:8080 k6-tests/purchase/step2-multiple-products.js
k6 run --env BASE_URL=http://localhost:8080 k6-tests/purchase/step3-mixed-scenario.js
k6 run --env BASE_URL=http://localhost:8080 k6-tests/purchase/step4-stock-depletion.js
```

#### 📝 상품 등록 API 테스트

```bash
# 테스트 데이터 삽입 (Owner 생성)
mysql -u root -p1234 foo < k6-tests/registration/test-data-registration.sql

# 전체 테스트 실행 (약 35분 소요)
./k6-tests/run-registration-tests.sh

# 개별 테스트 실행
k6 run --env BASE_URL=http://localhost:8080 --env OWNER_ID=1 k6-tests/registration/step1-basic-performance.js
k6 run --env BASE_URL=http://localhost:8080 k6-tests/registration/step2-concurrent-brands.js
k6 run --env BASE_URL=http://localhost:8080 k6-tests/registration/step3-extreme-load.js
```

### 3. 결과 확인

#### HTML 리포트

```bash
# 구매 API 테스트 결과
open k6-tests/results/purchase/step1-single-product-summary.html
open k6-tests/results/purchase/step2-multiple-products-summary.html
open k6-tests/results/purchase/step3-mixed-scenario-summary.html
open k6-tests/results/purchase/step4-stock-depletion-summary.html

# 등록 API 테스트 결과
open k6-tests/results/registration/step1-basic-performance-summary.html
open k6-tests/results/registration/step2-concurrent-brands-summary.html
open k6-tests/results/registration/step3-extreme-load-summary.html
```

## 📊 테스트 시나리오

### 🛒 상품 구매 API 테스트 (Pessimistic Lock)

| Step | 목적 | 특징 | VU | 소요시간 |
|------|------|------|----|----|
| **Step 1** | 최악의 Lock 경합 측정 | 모든 요청이 동일 상품(ID=1) 구매 | 10→200 | ~7분 |
| **Step 2** | Lock 경합 분산 성능 | 1~10번 상품 중 랜덤 선택 | 20→500 | ~2.5분 |
| **Step 3** | 실제 운영 환경 시뮬레이션 | Hot Item + 일반 트래픽 혼합 | 80~200 | ~16분 |
| **Step 4** | 재고 소진 시나리오 | 초고강도 부하로 재고 소진 | 500→1500 | ~7분 |

**주요 검증 사항:**
- Pessimistic Lock 성능
- 재고 동시성 제어
- 데드락 방지
- 에러 처리 (재고 부족 등)

### 📝 상품 등록 API 테스트 (Bulk Registration)

| Step | 목적 | 시나리오 | VU | 소요시간 |
|------|------|----------|----|----------|
| **Step 1** | 기본 성능 측정 | 100/500/1K/3K/5K개 × 5회 즉시 순차 실행 | 1 | ~10분 |
| **Step 2** | 동시성 테스트 | 5개 브랜드 동시 등록 (3K×3 + 5K×2) | 5 | ~10분 |
| **Step 3** | 극한 상황 테스트 | 10개 브랜드 × 5K개 동시 등록 | 10 | ~20분 |

**주요 검증 사항:**
- 배치 크기별 처리 성능 (100~5000개)
- 다중 브랜드 동시 등록 성능
- 시스템 리소스 한계 파악
- 부분 성공 처리 (일부 실패)
- 극한 상황에서의 안정성

## 📈 성능 메트릭

### 주요 지표

| 메트릭 | 설명 | 목표 |
|--------|------|------|
| **TPS** | 초당 처리 트랜잭션 수 | 높을수록 좋음 |
| **P95 응답시간** | 95% 요청의 응답 시간 | < 3초 (구매), 배치 크기별 상이 (등록) |
| **P99 응답시간** | 99% 요청의 응답 시간 | < 5초 (구매), 배치 크기별 상이 (등록) |
| **에러율** | 실패한 요청 비율 | < 1% (구매, Step1-2), < 10% (Step3) |
| **처리량** | 초당 처리 상품 수 | > 100 products/sec (등록) |

## 🔧 환경 변수

```bash
# 애플리케이션 URL
export BASE_URL=http://localhost:8080

# MySQL 설정
export MYSQL_HOST=localhost
export MYSQL_PORT=3306
export MYSQL_USER=root
export MYSQL_PASSWORD=1234
export MYSQL_DB=foo

# Redis 설정 (구매 API)
export REDIS_HOST=localhost
export REDIS_PORT=6379

# Owner ID (등록 API)
export OWNER_ID=1
```

## 📚 참고 자료

- [K6 공식 문서](https://k6.io/docs/)
- [K6 시나리오 가이드](https://k6.io/docs/using-k6/scenarios/)
- [K6 메트릭 가이드](https://k6.io/docs/using-k6/metrics/)
