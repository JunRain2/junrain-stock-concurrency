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
│   ├── common.js                    # 행 생성, 행/초 메트릭, 결과 리포트
│   ├── scenario-a-batch-size.js     # A: 배치 크기 (분모)
│   ├── scenario-b-concurrent.js     # B: 동시 요청 (커넥션 압박)
│   ├── scenario-c-duplicate-ratio.js # C: 중복 비율
│   ├── scenario-d-chunk-size.js     # D: 청크 크기
│   ├── sweep-chunk.sh               # D 스윕 (값마다 앱 재기동)
│   ├── seed.sql                     # 판매자 10명, products 비움
│   ├── reset.sh
│   └── run.sh                       # A → B(5수준) → C
│
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

시나리오 설계·환경 제약·판정 기준은 **[docs/usecase/재고점유/02-부하테스트-모델.md](../docs/usecase/재고점유/02-부하테스트-모델.md)** 에 있다. 아래는 실행법만.

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

시나리오 설계·판정 기준은 **[docs/usecase/상품등록/02-부하테스트-모델.md](../docs/usecase/상품등록/02-부하테스트-모델.md)** 에 있다. 아래는 실행법만.

```bash
bash k6-tests/registration/run.sh
```

개별 실행:

```bash
bash k6-tests/registration/reset.sh
k6 run --env BASE_URL=http://localhost:8080 --env OWNER_ID=1 k6-tests/registration/scenario-a-batch-size.js
k6 run --env VUS=8 k6-tests/registration/scenario-b-concurrent.js
k6 run k6-tests/registration/scenario-c-duplicate-ratio.js

# D는 서버 설정을 바꿔야 해서 전용 러너가 앱을 값마다 다시 띄운다
bash k6-tests/registration/sweep-chunk.sh
```

### 시나리오

| | 부하 축 | 배치 | 답하는 것 |
|---|---|---|---|
| **A** | 배치 크기 100 / 500 / 1,000 / 5,000 | 동시 1 | 행당 비용 대 요청 고정비. 청크 크기의 근거 |
| **B** | 동시 요청 1 / 2 / 4 / 8 / 16 (+ 피크 10×5,000) | 1,000행 | 커넥션 풀이 마르는 지점, 그리고 실제 피크 |
| **C** | 신규 비율 100% / 50% / 0% | 5,000행 | 중복 판별(스킵 + 역조회) 경로의 비용 |
| **D** | `chunk-size` 250 / 500 / 1,000 / 2,000 / 4,000 | 5,000행 | 청크 값 자체. 요청당 문장 수가 40 → 4로 바뀐다 |

요청 하나가 초 단위라 **도착률이 아니라 동시 요청 수를 고정**한다. B는 VU 수가 달라 수준마다 k6 실행을 새로 띄우고 사이에 리셋한다.

B는 VU를 판매자 10명에게 나눠 준다 — 한 owner로 몰면 `owner_id` 인덱스에 실제로는 없을 핫스팟이 생긴다.

배치 1,000은 원인을 가르기 위한 값이지 최대 부하가 아니다. 실제 피크(브랜드 10곳 × 5,000행 = 동시 5만 행)는 배치를 올려 따로 돌린다. `run.sh`가 사다리 뒤에 한 번 더 돈다.

```bash
k6 run --env VUS=10 --env BATCH=5000 k6-tests/registration/scenario-b-concurrent.js
```

### 결과 읽기

```
=== A: 배치 크기 ===

   행/요청   요청수      p95      행/초     성공행     실패행
       100       20     41ms       2439       2000          0
      5000       20    703ms       7112     100000          0
```

- **행/초** — 이 API의 처리량 단위. 요청 크기가 100배 차이 나서 요청/초로는 비교가 안 된다. 표본이 수십 건이라 중앙값을 싣는다
- **실패행** — A·B에서 0이 아니면 코드가 겹친 것이다. 신규 삽입이 아니라 중복 스킵을 잰 실행이므로 버린다
- **경고** — `http_req_failed > 0`(200 아닌 응답), `checks` 실패(보고된 행 수 불일치). 뜨면 그 실행은 무효다

### 주의

`ddl-auto=create`라 **앱 기동이 시드보다 먼저다.** `reset.sh`가 헬스체크로 강제한다.

한 실행이 수십만 행을 남긴다. 리셋 없이 이어 돌리면 인덱스가 계속 커져 뒤 실행이 다른 조건을 잰다.

원본 JSON은 `results/registration/`.

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

export OWNER_ID=1     # 등록 API (seed.sql이 심는 판매자)
export VUS=8          # 등록 API 시나리오 B의 동시 요청 수
```

## 참고

- [K6 시나리오 가이드](https://k6.io/docs/using-k6/scenarios/)
- [K6 메트릭 가이드](https://k6.io/docs/using-k6/metrics/)
