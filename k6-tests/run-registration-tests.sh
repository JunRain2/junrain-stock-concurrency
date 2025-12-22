#!/bin/bash

# K6 상품 등록 부하 테스트 전체 실행 스크립트

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${GREEN}=========================================${NC}"
echo -e "${GREEN}K6 상품 등록 부하 테스트 시작${NC}"
echo -e "${GREEN}=========================================${NC}\n"

# 프로젝트 루트 디렉토리로 이동
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_ROOT"

echo "프로젝트 루트: $PROJECT_ROOT"

# 환경 변수
BASE_URL="${BASE_URL:-http://localhost:8080}"
RESULTS_DIR="k6-tests/results/registration"
DB_RESET_SQL="k6-tests/registration/test-data-registration.sql"
OWNER_ID="${OWNER_ID:-1}"

# MySQL 접속 정보
MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-1234}"
MYSQL_DB="${MYSQL_DB:-foo}"

# Redis 접속 정보
REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6379}"

# 결과 디렉토리 생성
mkdir -p "$RESULTS_DIR"

# 헬스 체크
echo -e "${YELLOW}[1/7] 애플리케이션 헬스 체크...${NC}"
if curl -s "${BASE_URL}/actuator/health" | grep -q "UP"; then
    echo -e "${GREEN}✓ 애플리케이션이 정상 작동 중입니다${NC}\n"
else
    echo -e "${RED}✗ 애플리케이션이 작동하지 않습니다. BASE_URL을 확인하세요: ${BASE_URL}${NC}"
    exit 1
fi

# Redis 초기화 함수
reset_redis() {
    echo -e "${YELLOW}Redis 데이터 초기화 중...${NC}"

    # clear-redis.sh 실행
    REDIS_HOST="${REDIS_HOST}" REDIS_PORT="${REDIS_PORT}" bash k6-tests/common/clear-redis.sh > /dev/null 2>&1

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Redis 데이터 초기화 완료${NC}"
    else
        echo -e "${RED}✗ Redis 데이터 초기화 실패${NC}"
        echo -e "${YELLOW}다음 명령으로 수동 실행: bash k6-tests/common/clear-redis.sh${NC}\n"
        # Redis 초기화 실패는 치명적이지 않으므로 계속 진행
    fi
}

# DB 초기화 함수
reset_database() {
    echo -e "${YELLOW}데이터베이스 리셋 중...${NC}"

    # Docker Compose 사용 여부 확인
    if docker-compose ps mysql &>/dev/null; then
        # Docker Compose 사용
        docker-compose exec -T mysql mysql -u"${MYSQL_USER}" -p"${MYSQL_PASSWORD}" "${MYSQL_DB}" < "${DB_RESET_SQL}" 2>/dev/null
    else
        # 로컬 MySQL 사용
        MYSQL_PWD="${MYSQL_PASSWORD}" mysql -h"${MYSQL_HOST}" -P"${MYSQL_PORT}" -u"${MYSQL_USER}" "${MYSQL_DB}" < "${DB_RESET_SQL}" 2>/dev/null
    fi

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ 데이터베이스 리셋 완료 (Owner ID: ${OWNER_ID})${NC}"
    else
        echo -e "${RED}✗ 데이터베이스 리셋 실패${NC}"
        echo -e "${YELLOW}Docker 사용 시: docker-compose exec -T mysql mysql -u${MYSQL_USER} -p${MYSQL_PASSWORD} ${MYSQL_DB} < ${DB_RESET_SQL}${NC}"
        echo -e "${YELLOW}로컬 MySQL 사용 시: MYSQL_PWD=${MYSQL_PASSWORD} mysql -u${MYSQL_USER} ${MYSQL_DB} < ${DB_RESET_SQL}${NC}\n"
        exit 1
    fi

    # Redis도 초기화
    reset_redis
    echo ""
    sleep 2
}

# Step 1: 기본 성능 측정
echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}Step 1: 기본 성능 측정 (Baseline)${NC}"
echo -e "${BLUE}=========================================${NC}"
echo -e "배치 크기: 100/500/1000/3000/5000개 | VU: 1 | 각 5회 반복\n"

echo -e "${YELLOW}[2/7] Step 1 준비 중...${NC}"
reset_database

echo -e "${YELLOW}[3/7] Step 1 실행 중... (약 75분 소요)${NC}"
k6 run --env BASE_URL="${BASE_URL}" \
    --env OWNER_ID="${OWNER_ID}" \
    --out json="${RESULTS_DIR}/step1-result.json" \
    k6-tests/registration/step1-basic-performance.js || echo -e "${YELLOW}⚠️  Step 1: Threshold 경고 발생 (계속 진행)${NC}"

echo -e "${GREEN}✓ Step 1 완료${NC}\n"
sleep 3

# Step 2: 동시성 테스트
echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}Step 2: 동시성 테스트 (Concurrent Brands)${NC}"
echo -e "${BLUE}=========================================${NC}"
echo -e "5개 브랜드 동시 등록 | Brand 1-3: 3000개, Brand 4-5: 5000개 | 각 3회 반복\n"

echo -e "${YELLOW}[4/7] Step 2 준비 중...${NC}"
reset_database

echo -e "${YELLOW}[5/7] Step 2 실행 중... (약 10분 소요)${NC}"
k6 run --env BASE_URL="${BASE_URL}" \
    --out json="${RESULTS_DIR}/step2-result.json" \
    k6-tests/registration/step2-concurrent-brands.js || echo -e "${YELLOW}⚠️  Step 2: Threshold 경고 발생 (계속 진행)${NC}"

echo -e "${GREEN}✓ Step 2 완료${NC}\n"
sleep 3

# Step 3: 극한 상황 테스트
echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}Step 3: 극한 상황 테스트 (Extreme Load)${NC}"
echo -e "${BLUE}=========================================${NC}"
echo -e "10개 브랜드 동시 등록 | 각 5000개씩 (총 50,000개) | 각 2회 반복\n"

echo -e "${YELLOW}[6/7] Step 3 준비 중...${NC}"
reset_database

echo -e "${YELLOW}[7/7] Step 3 실행 중... (약 20분 소요)${NC}"
k6 run --env BASE_URL="${BASE_URL}" \
    --out json="${RESULTS_DIR}/step3-result.json" \
    k6-tests/registration/step3-extreme-load.js || echo -e "${YELLOW}⚠️  Step 3: Threshold 경고 발생 (계속 진행)${NC}"

echo -e "${GREEN}✓ Step 3 완료${NC}\n"

# 결과 요약
echo -e "${GREEN}=========================================${NC}"
echo -e "${GREEN}모든 테스트 완료!${NC}"
echo -e "${GREEN}=========================================${NC}\n"

echo -e "${BLUE}📊 테스트 결과 요약${NC}\n"

echo -e "HTML 리포트:"
echo -e "  - ${RESULTS_DIR}/step1-basic-performance-summary.html"
echo -e "  - ${RESULTS_DIR}/step2-concurrent-brands-summary.html"
echo -e "  - ${RESULTS_DIR}/step3-extreme-load-summary.html\n"

echo -e "JSON 결과:"
echo -e "  - ${RESULTS_DIR}/step1-basic-performance-summary.json"
echo -e "  - ${RESULTS_DIR}/step2-concurrent-brands-summary.json"
echo -e "  - ${RESULTS_DIR}/step3-extreme-load-summary.json\n"

echo -e "${YELLOW}다음 명령으로 HTML 결과를 확인할 수 있습니다:${NC}"
echo -e "  open ${RESULTS_DIR}/step1-basic-performance-summary.html"
echo -e "  open ${RESULTS_DIR}/step2-concurrent-brands-summary.html"
echo -e "  open ${RESULTS_DIR}/step3-extreme-load-summary.html\n"

echo -e "${GREEN}테스트 시나리오 설명:${NC}"
echo -e "  Step 1: 기본 성능 측정 - 단계별 배치 크기(100~5000개)에 따른 처리 성능 파악"
echo -e "  Step 2: 동시성 테스트 - 5개 브랜드가 동시에 상품 등록하는 실전 시나리오"
echo -e "  Step 3: 극한 상황 - 10개 브랜드가 각 5000개씩 등록하여 시스템 한계 테스트\n"

echo -e "${BLUE}성능 목표:${NC}"
echo -e "  - 5000개 단일 요청: 30초 이내 (P95)"
echo -e "  - 동시 브랜드: 각 60초 이내 (P95)"
echo -e "  - 에러율: 1% 미만 (극한 상황 10% 미만)"
echo -e "  - 평균 처리량: 100 products/sec 이상\n"
