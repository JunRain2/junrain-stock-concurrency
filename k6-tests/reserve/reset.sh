#!/bin/bash
#
# 실행 사이 초기화. 시나리오마다 호출한다.
#
# 1회 실행이 상품 1개에서 약 45,000건을 쓰므로, 리셋 없이 반복하면
# 세 번째 회차가 재고 부족 측정이 된다.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

BASE_URL="${BASE_URL:-http://localhost:8080}"
SEED_SQL="${SCRIPT_DIR}/seed.sql"

MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-1234}"
MYSQL_DB="${MYSQL_DB:-foo}"

REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6379}"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

# 앱이 떠 있어야 한다. ddl-auto=create 이므로 앱 기동이 테이블을 다시 만든다.
# 앱보다 먼저 시드를 넣으면 기동과 함께 전부 사라진다.
if ! curl -sf "${BASE_URL}/actuator/health" | grep -q '"status":"UP"'; then
    echo -e "${RED}✗ 앱이 UP이 아니다: ${BASE_URL}${NC}"
    echo -e "${YELLOW}  앱을 먼저 기동한 뒤 이 스크립트를 실행한다 (ddl-auto=create).${NC}"
    exit 1
fi

# --- MySQL ---------------------------------------------------------------
mysql_exec() {
    if docker compose ps mysql >/dev/null 2>&1; then
        docker compose exec -T mysql mysql -u"${MYSQL_USER}" -p"${MYSQL_PASSWORD}" "${MYSQL_DB}" "$@"
    else
        MYSQL_PWD="${MYSQL_PASSWORD}" mysql -h"${MYSQL_HOST}" -P"${MYSQL_PORT}" -u"${MYSQL_USER}" "${MYSQL_DB}" "$@"
    fi
}

echo -e "${YELLOW}MySQL 시드 적용...${NC}"
mysql_exec < "${SEED_SQL}" 2>/dev/null

PRODUCT_COUNT=$(mysql_exec -N -B -e "SELECT COUNT(*) FROM products;" 2>/dev/null)
MIN_STOCK=$(mysql_exec -N -B -e "SELECT MIN(stock) FROM products;" 2>/dev/null)

if [ "${PRODUCT_COUNT}" != "100" ] || [ "${MIN_STOCK}" != "3000000" ]; then
    echo -e "${RED}✗ 시드 검증 실패: 상품 ${PRODUCT_COUNT}개 / 최소 재고 ${MIN_STOCK}${NC}"
    exit 1
fi
echo -e "${GREEN}✓ 상품 ${PRODUCT_COUNT}개, 재고 각 ${MIN_STOCK}${NC}"

# --- Redis ---------------------------------------------------------------
# RedisStockWriterImpl이 @Primary라 예약 경로가 실제로 Redis를 쓴다.
# MySQL 시드와 같은 상품 수/재고로 product_stock 키를 채운다.
redis_exec() {
    if command -v redis-cli >/dev/null 2>&1; then
        redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" "$@"
    elif docker compose ps redis >/dev/null 2>&1; then
        docker compose exec -T redis redis-cli "$@"
    else
        echo -e "${RED}✗ redis-cli도 redis 컨테이너도 없다${NC}" >&2
        return 1
    fi
}

echo -e "${YELLOW}Redis 초기화...${NC}"
redis_exec FLUSHALL >/dev/null

for i in $(seq 1 "${PRODUCT_COUNT}"); do
    redis_exec SET "product_stock:${i}" "${MIN_STOCK}" >/dev/null
done

REMAINING=$(redis_exec DBSIZE | tr -d '[:space:]')
if [ "${REMAINING}" != "${PRODUCT_COUNT}" ]; then
    echo -e "${RED}✗ Redis 키 ${REMAINING}개 (기대 ${PRODUCT_COUNT}개)${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Redis 상품 ${PRODUCT_COUNT}개, 재고 각 ${MIN_STOCK}${NC}"
