# K6 부하 테스트

API별 K6 부하 테스트 스크립트.

```
k6-tests/
├── reserve/                         # 재고 점유 API (POST /api/v1/products/reserve)
│   ├── common.js                    # 도착률 사다리 빌더, 결과 리포트
│   ├── scenario-a-single-row.js     # A: 단일 행 경합 (최악값)
│   ├── scenario-b-spread.js         # B: 경합 분산 (분모)
│   ├── seed.sql                     # 상품 100개, 재고 각 100,000
│   ├── reset.sh                     # 실행 사이 초기화 (MySQL 시드 + Redis 비움)
│   └── run.sh                       # A → B 순차 실행
│
├── registration/                    # 상품 등록 API (POST /api/v1/products/bulk)
│   ├── step1-basic-performance.js
│   ├── step2-concurrent-brands.js
│   ├── step3-extreme-load.js
│   └── test-data-registration.sql
│
├── common/                          # registration 전용 공통 유틸
│   ├── common.js
│   └── clear-redis.sh
│
├── run-registration-tests.sh
└── results/
```

## 사전 준비

```bash
brew install k6
docker compose -f docker-compose.yml -f docker-compose.bench.yml up -d
./gradlew bootRun
```

---

## 재고 점유 API

시나리오 설계·환경 제약·판정 기준은 **[docs/load_test/재고점유.md](../docs/load_test/재고점유.md)** 에 있다. 아래는 실행법만.

```bash
bash k6-tests/reserve/run.sh
```

`run.sh`가 시나리오마다 `reset.sh`를 호출한다. 워밍업 60초는 각 스크립트 안에 있고 threshold를 걸지 않아 결과에서 빠진다.

개별 실행:

```bash
bash k6-tests/reserve/reset.sh
k6 run --env BASE_URL=http://localhost:8080 k6-tests/reserve/scenario-a-single-row.js
```

### 시나리오

| | 대상 | rate 사다리 (RPS) | 역할 | 소요 |
|---|---|---|---|---|
| **A** | 상품 1개 고정 | 150 / 300 / 600 | 경합 최악값. 결론이 되는 숫자 | ~4분 |
| **B** | 상품 100개 랜덤 | 150 / 300 / 600 / 1200 | 분모. 경합이 없을 때의 천장 | ~5분 |

VU가 아니라 **초당 요청 수를 고정**한다(`constant-arrival-rate`). 지연이 늘어도 부하가 줄지 않으므로 포화점을 직접 읽을 수 있다.

`경합 비용 = B의 처리 한계 ÷ A의 처리 한계`

### 결과 읽기

```
  rate     요청수      p95        p99     판정
   150       6000     142ms   표본부족     PASS
   300      12000     198ms      410ms    PASS
   600      24000    2410ms     5200ms    FAIL

  처리 한계: 300 RPS  @ p95 < 200ms
```

- **처리 한계** — p95 < 200ms(CLAUDE.md SLO)를 통과하는 마지막 rate. 시나리오마다 이 숫자 하나가 결론이다.
- **표본부족** — 표본 10,000건 미만 구간의 p99는 상위 몇 건이 값을 정한다. 인용하지 않는다.
- **경고** — `dropped_iterations > 0`(k6가 부하를 못 냄), 5xx 혼입, 사다리 전 구간 통과/실패. 뜨면 그 실행은 버린다.

### 주의

`ddl-auto=create`라 **앱 기동이 시드보다 먼저다.** 기동이 테이블을 다시 만든다. `reset.sh`가 헬스체크로 이 순서를 강제한다.

원본 JSON은 `results/reserve/`.

---

## 상품 등록 API

```bash
mysql -u root -p1234 foo < k6-tests/registration/test-data-registration.sql

# 전체 (약 35분)
./k6-tests/run-registration-tests.sh

# 개별
k6 run --env BASE_URL=http://localhost:8080 --env OWNER_ID=1 k6-tests/registration/step1-basic-performance.js
k6 run --env BASE_URL=http://localhost:8080 k6-tests/registration/step2-concurrent-brands.js
k6 run --env BASE_URL=http://localhost:8080 k6-tests/registration/step3-extreme-load.js
```

| Step | 목적 | 시나리오 | VU | 소요 |
|---|---|---|---|---|
| **1** | 기본 성능 측정 | 100/500/1K/3K/5K개 × 5회 순차 | 1 | ~10분 |
| **2** | 동시성 | 5개 브랜드 동시 등록 (3K×3 + 5K×2) | 5 | ~10분 |
| **3** | 극한 상황 | 10개 브랜드 × 5K개 동시 등록 | 10 | ~20분 |

검증 대상: 배치 크기별 처리 성능, 다중 브랜드 동시 등록, 부분 성공 처리.

---

## 환경 변수

```bash
export BASE_URL=http://localhost:8080

export MYSQL_HOST=localhost
export MYSQL_PORT=3306
export MYSQL_USER=root
export MYSQL_PASSWORD=1234
export MYSQL_DB=foo

export REDIS_HOST=localhost
export REDIS_PORT=6379

export OWNER_ID=1     # 등록 API
```

## 참고

- [K6 시나리오 가이드](https://k6.io/docs/using-k6/scenarios/)
- [K6 메트릭 가이드](https://k6.io/docs/using-k6/metrics/)
