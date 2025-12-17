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
echo -e "${YELLOW}[1/9] 애플리케이션 헬스 체크...${NC}"
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

# Phase 1: 기본 성능 측정
echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}Phase 1: 기본 성능 측정 (Baseline)${NC}"
echo -e "${BLUE}=========================================${NC}"
echo -e "데이터: 1,000건 | VU: 1 | 반복: 10회\n"

echo -e "${YELLOW}[2/9] Phase 1 준비 중...${NC}"
reset_database

echo -e "${YELLOW}[3/9] Phase 1 실행 중... (약 5분 소요)${NC}"
k6 run --env BASE_URL="${BASE_URL}" \
    --env OWNER_ID="${OWNER_ID}" \
    --env PRODUCTS_PER_REQUEST="1000" \
    --out json="${RESULTS_DIR}/phase1-result.json" \
    k6-tests/registration/phase1-baseline.js || echo -e "${YELLOW}⚠️  Phase 1: Threshold 경고 발생 (계속 진행)${NC}"

echo -e "${GREEN}✓ Phase 1 완료${NC}\n"
sleep 3

# Phase 2: 배치 크기 최적화
echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}Phase 2: 배치 크기 최적화${NC}"
echo -e "${BLUE}=========================================${NC}"
echo -e "데이터: 100/500/1,000/5,000/10,000건 | VU: 1 | 각 5회 반복\n"

echo -e "${YELLOW}[4/9] Phase 2 준비 중...${NC}"
reset_database

echo -e "${YELLOW}[5/9] Phase 2 실행 중... (약 15분 소요)${NC}"
k6 run --env BASE_URL="${BASE_URL}" \
    --env OWNER_ID="${OWNER_ID}" \
    --out json="${RESULTS_DIR}/phase2-result.json" \
    k6-tests/registration/phase2-batch-optimization.js || echo -e "${YELLOW}⚠️  Phase 2: Threshold 경고 발생 (계속 진행)${NC}"

echo -e "${GREEN}✓ Phase 2 완료${NC}\n"
sleep 3

# Phase 3: 일반 동시성
echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}Phase 3: 일반 동시성 테스트${NC}"
echo -e "${BLUE}=========================================${NC}"
echo -e "데이터: 1,000건 | VU: 10 | 시간: 10분\n"

echo -e "${YELLOW}[6/9] Phase 3 준비 중...${NC}"
reset_database

echo -e "${YELLOW}[7/9] Phase 3 실행 중... (약 10분 소요)${NC}"
k6 run --env BASE_URL="${BASE_URL}" \
    --env OWNER_ID="${OWNER_ID}" \
    --env PRODUCTS_PER_REQUEST="1000" \
    --out json="${RESULTS_DIR}/phase3-result.json" \
    k6-tests/registration/phase3-normal-concurrency.js || echo -e "${YELLOW}⚠️  Phase 3: Threshold 경고 발생 (계속 진행)${NC}"

echo -e "${GREEN}✓ Phase 3 완료${NC}\n"
sleep 3

# Phase 4: 높은 동시성
echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}Phase 4: 높은 동시성 테스트${NC}"
echo -e "${BLUE}=========================================${NC}"
echo -e "데이터: 5,000건 | VU: 50 | 시간: 5분\n"

echo -e "${YELLOW}[8/9] Phase 4 준비 중...${NC}"
reset_database

echo -e "${YELLOW}[9/9] Phase 4 실행 중... (약 5분 소요)${NC}"
k6 run --env BASE_URL="${BASE_URL}" \
    --env OWNER_ID="${OWNER_ID}" \
    --env PRODUCTS_PER_REQUEST="5000" \
    --out json="${RESULTS_DIR}/phase4-result.json" \
    k6-tests/registration/phase4-high-concurrency.js || echo -e "${YELLOW}⚠️  Phase 4: Threshold 경고 발생 (계속 진행)${NC}"

echo -e "${GREEN}✓ Phase 4 완료${NC}\n"

# 결과 요약
echo -e "${GREEN}=========================================${NC}"
echo -e "${GREEN}모든 테스트 완료!${NC}"
echo -e "${GREEN}=========================================${NC}\n"

echo -e "${BLUE}📊 테스트 결과 요약${NC}\n"

echo -e "HTML 리포트:"
echo -e "  - ${RESULTS_DIR}/phase1-baseline-summary.html"
echo -e "  - ${RESULTS_DIR}/phase2-batch-optimization-summary.html"
echo -e "  - ${RESULTS_DIR}/phase3-normal-concurrency-summary.html"
echo -e "  - ${RESULTS_DIR}/phase4-high-concurrency-summary.html\n"

echo -e "JSON 결과:"
echo -e "  - ${RESULTS_DIR}/phase1-baseline-summary.json"
echo -e "  - ${RESULTS_DIR}/phase2-batch-optimization-summary.json"
echo -e "  - ${RESULTS_DIR}/phase3-normal-concurrency-summary.json"
echo -e "  - ${RESULTS_DIR}/phase4-high-concurrency-summary.json\n"

echo -e "${YELLOW}다음 명령으로 HTML 결과를 확인할 수 있습니다:${NC}"
echo -e "  open ${RESULTS_DIR}/phase1-baseline-summary.html"
echo -e "  open ${RESULTS_DIR}/phase2-batch-optimization-summary.html"
echo -e "  open ${RESULTS_DIR}/phase3-normal-concurrency-summary.html"
echo -e "  open ${RESULTS_DIR}/phase4-high-concurrency-summary.html\n"

echo -e "${GREEN}테스트 시나리오 설명:${NC}"
echo -e "  Phase 1: 기준 성능 파악 - 단일 사용자로 기본 처리 속도 측정"
echo -e "  Phase 2: 최적 배치 크기 결정 - 다양한 크기로 처리 효율성 비교"
echo -e "  Phase 3: 일반 부하 테스트 - 실제 운영 환경의 다중 사용자 시뮬레이션"
echo -e "  Phase 4: 높은 부하 테스트 - 시스템 한계 및 안정성 검증\n"
