#!/bin/bash

# K6 동시성 테스트 전체 실행 스크립트

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=====================================${NC}"
echo -e "${GREEN}K6 동시성 제어 성능 테스트 시작${NC}"
echo -e "${GREEN}=====================================${NC}\n"

# 프로젝트 루트 디렉토리로 이동 (스크립트 위치 기준)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_ROOT"

echo "프로젝트 루트: $PROJECT_ROOT"

# 환경 변수
BASE_URL="${BASE_URL:-http://localhost:8080}"
RESULTS_DIR="k6-tests/results"
DB_RESET_SQL="k6-tests/test-data.sql"

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
echo -e "${YELLOW}[1/9] 애플리케이션 헬스 체크...${NC}"
if curl -s "${BASE_URL}/actuator/health" | grep -q "UP"; then
    echo -e "${GREEN}✓ 애플리케이션이 정상 작동 중입니다${NC}\n"
else
    echo -e "${RED}✗ 애플리케이션이 작동하지 않습니다. BASE_URL을 확인하세요: ${BASE_URL}${NC}"
    exit 1
fi

# Redis 초기화 함수
reset_redis() {
    echo -e "${YELLOW}Redis 재고 초기화 중...${NC}"

    # init-redis-stock.sh 실행
    REDIS_HOST="${REDIS_HOST}" REDIS_PORT="${REDIS_PORT}" bash k6-tests/init-redis-stock.sh > /dev/null 2>&1

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Redis 재고 초기화 완료${NC}"
    else
        echo -e "${RED}✗ Redis 재고 초기화 실패${NC}"
        echo -e "${YELLOW}다음 명령으로 수동 실행: bash k6-tests/init-redis-stock.sh${NC}\n"
        exit 1
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
        echo -e "${GREEN}✓ 데이터베이스 리셋 완료${NC}"
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

# Step 1: 단일 상품 경합 테스트
echo -e "${YELLOW}[2/9] Step 1: 단일 상품 경합 테스트 준비...${NC}"
reset_database

echo -e "${YELLOW}[3/9] Step 1 실행 중... (약 7분 소요)${NC}"
k6 run --env BASE_URL="${BASE_URL}" \
    --out json="${RESULTS_DIR}/step1-result.json" \
    k6-tests/step1-single-product.js || echo -e "${YELLOW}⚠️  Step 1: Threshold 경고 발생 (계속 진행)${NC}"

echo -e "${GREEN}✓ Step 1 완료${NC}\n"
sleep 5

# Step 2: 다중 상품 분산 테스트
echo -e "${YELLOW}[4/9] Step 2: 다중 상품 분산 테스트 준비...${NC}"
reset_database

echo -e "${YELLOW}[5/9] Step 2 실행 중... (약 2.5분 소요)${NC}"
k6 run --env BASE_URL="${BASE_URL}" \
    --out json="${RESULTS_DIR}/step2-result.json" \
    k6-tests/step2-multiple-products.js || echo -e "${YELLOW}⚠️  Step 2: Threshold 경고 발생 (계속 진행)${NC}"

echo -e "${GREEN}✓ Step 2 완료${NC}\n"
sleep 5

# Step 3: 혼합 시나리오
echo -e "${YELLOW}[6/9] Step 3: 혼합 시나리오 준비...${NC}"
reset_database

echo -e "${YELLOW}[7/9] Step 3 실행 중... (약 16분 소요)${NC}"
k6 run --env BASE_URL="${BASE_URL}" \
    --out json="${RESULTS_DIR}/step3-result.json" \
    k6-tests/step3-mixed-scenario.js || echo -e "${YELLOW}⚠️  Step 3: Threshold 경고 발생 (계속 진행)${NC}"

echo -e "${GREEN}✓ Step 3 완료${NC}\n"
sleep 5

# Step 4: 재고 소진 테스트
echo -e "${YELLOW}[8/9] Step 4: 재고 소진 테스트 준비...${NC}"
reset_database

echo -e "${YELLOW}[9/9] Step 4 실행 중... (약 7분 소요)${NC}"
k6 run --env BASE_URL="${BASE_URL}" \
    --out json="${RESULTS_DIR}/step4-result.json" \
    k6-tests/step4-stock-depletion.js || echo -e "${YELLOW}⚠️  Step 4: Threshold 경고 발생 (계속 진행)${NC}"

echo -e "${GREEN}✓ Step 4 완료${NC}\n"

# 결과 요약
echo -e "${GREEN}=====================================${NC}"
echo -e "${GREEN}모든 테스트 완료!${NC}"
echo -e "${GREEN}=====================================${NC}\n"

echo -e "결과 파일 위치:"
echo -e "  - ${RESULTS_DIR}/step1-single-product-summary.html"
echo -e "  - ${RESULTS_DIR}/step2-multiple-products-summary.html"
echo -e "  - ${RESULTS_DIR}/step3-mixed-scenario-summary.html"
echo -e "  - ${RESULTS_DIR}/step4-stock-depletion-summary.html\n"

echo -e "${YELLOW}다음 명령으로 HTML 결과를 확인할 수 있습니다:${NC}"
echo -e "  open ${RESULTS_DIR}/step1-single-product-summary.html"
echo -e "  open ${RESULTS_DIR}/step2-multiple-products-summary.html"
echo -e "  open ${RESULTS_DIR}/step3-mixed-scenario-summary.html"
echo -e "  open ${RESULTS_DIR}/step4-stock-depletion-summary.html\n"
