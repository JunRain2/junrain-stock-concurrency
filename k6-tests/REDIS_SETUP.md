# Redis 초기화 가이드

## 📋 개요

k6 부하 테스트 실행 시 Redis 데이터를 자동으로 초기화합니다.

## 🔧 스크립트 구성

### 1. `common/clear-redis.sh` - Redis 완전 초기화
모든 Redis 데이터를 삭제합니다.

```bash
bash k6-tests/common/clear-redis.sh
```

**기능:**
- `FLUSHALL` 명령으로 모든 데이터 삭제
- Docker 및 로컬 redis-cli 자동 감지
- 삭제 후 남은 키 개수 확인

### 2. `purchase/init-redis-stock.sh` - 재고 데이터 초기화
구매 API 테스트를 위한 재고 데이터를 설정합니다.

```bash
bash k6-tests/purchase/init-redis-stock.sh
```

**기능:**
- `product:1` ~ `product:10` 키에 100,000개 재고 설정
- 설정 후 값 검증

## 🔄 자동 초기화 프로세스

### 구매 API 테스트 (`run-all-tests.sh`)

각 Step 실행 전:
1. **DB 초기화** - MySQL 테스트 데이터 삽입
2. **Redis 완전 초기화** - 모든 데이터 삭제
3. **Redis 재고 설정** - 상품별 100,000개 재고 설정

```bash
./k6-tests/run-all-tests.sh
```

### 등록 API 테스트 (`run-registration-tests.sh`)

각 Phase 실행 전:
1. **DB 초기화** - MySQL Owner 데이터 삽입
2. **Redis 완전 초기화** - 모든 데이터 삭제 (잔여 데이터 제거)

```bash
./k6-tests/run-registration-tests.sh
```

## 🛠️ 수동 초기화

### 전체 Redis 초기화
```bash
bash k6-tests/common/clear-redis.sh
```

### 구매 테스트용 재고 설정
```bash
bash k6-tests/common/clear-redis.sh
bash k6-tests/purchase/init-redis-stock.sh
```

## 🔍 검증

### Redis 데이터 확인
```bash
# Docker 사용 시
docker exec -i demo-redis redis-cli KEYS "*"
docker exec -i demo-redis redis-cli GET "product:1"

# 로컬 Redis 사용 시
redis-cli KEYS "*"
redis-cli GET "product:1"
```

### 예상 결과 (구매 테스트 초기화 후)
```
product:1 = 100000
product:2 = 100000
...
product:10 = 100000
```

## ⚙️ 환경 변수

```bash
# Redis 호스트 (기본값: localhost)
export REDIS_HOST=localhost

# Redis 포트 (기본값: 6379)
export REDIS_PORT=6379
```

## 🐳 Docker 환경

스크립트는 Docker 환경을 자동으로 감지합니다:

1. **redis-cli 설치됨** → 직접 사용
2. **Docker 컨테이너 실행 중** → `docker exec` 사용
3. **둘 다 없음** → 에러

## ⚠️ 주의사항

### 구매 API 테스트
- Redis 초기화 실패 시 **테스트 중단** (재고 데이터 필수)
- 각 Step마다 재고가 100,000개로 리셋됨

### 등록 API 테스트
- Redis 초기화 실패 시 **경고만 표시** (Redis 불필요)
- 잔여 데이터 방지 목적

## 📁 관련 파일

- `common/clear-redis.sh` - Redis 완전 초기화
- `purchase/init-redis-stock.sh` - 재고 데이터 설정
- `run-all-tests.sh` - 구매 API 테스트 (Redis 필수)
- `run-registration-tests.sh` - 등록 API 테스트 (Redis 선택)
