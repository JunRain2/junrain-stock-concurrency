#!/bin/bash
#
# 실행 사이 초기화. 시나리오마다, 그리고 시나리오 B는 동시성 수준마다 호출한다.
#
# 리셋 없이 이어 돌리면 앞 실행이 남긴 수십만 행이 unique 인덱스 크기를 키워
# 뒤 실행이 다른 조건을 재게 된다.

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

# k6는 handleSummary가 쓰는 경로의 디렉터리를 만들어 주지 않는다. 없으면 실행이 끝나고서야
# "no such file or directory"로 결과가 통째로 날아간다.
mkdir -p "$(cd "${SCRIPT_DIR}/../.." && pwd)/k6-tests/results/registration"

# ddl-auto=create라 앱 기동이 테이블을 다시 만든다. 앱보다 먼저 시드를 넣으면 기동과 함께 사라진다.
if ! curl -sf "${BASE_URL}/actuator/health" | grep -q '"status":"UP"'; then
    echo -e "${RED}✗ 앱이 UP이 아니다: ${BASE_URL}${NC}"
    echo -e "${YELLOW}  앱을 먼저 기동한 뒤 이 스크립트를 실행한다 (ddl-auto=create).${NC}"
    exit 1
fi

mysql_exec() {
    if docker compose ps mysql >/dev/null 2>&1; then
        docker compose exec -T mysql mysql -u"${MYSQL_USER}" -p"${MYSQL_PASSWORD}" "${MYSQL_DB}" "$@"
    else
        MYSQL_PWD="${MYSQL_PASSWORD}" mysql -h"${MYSQL_HOST}" -P"${MYSQL_PORT}" -u"${MYSQL_USER}" "${MYSQL_DB}" "$@"
    fi
}

echo -e "${YELLOW}MySQL 시드 적용...${NC}"
mysql_exec < "${SEED_SQL}" 2>/dev/null

# 배경 데이터. 빈 테이블은 최적 조건이라 unique 인덱스가 얕고 페이지 분할도 거의 없다.
# 운영처럼 이미 쌓인 상태를 재려면 여기에 행 수를 준다: PRESEED_ROWS=1000000 bash reset.sh
#
# 배수로 불린다(1 -> 2 -> 4 ...). API로 넣으면 측정 대상 코드를 타서 시간도 오래 걸리고
# 심는 방식이 결과에 섞인다. 코드 접두어 SEED-는 시나리오가 만드는 코드(A-/B-/C-)와 겹치지 않는다.
# owner_id는 100개로 흩는다 - 전부 같은 owner면 owner_id 인덱스가 선택도 0이라 조회 비교가 무의미해진다.
PRESEED_ROWS="${PRESEED_ROWS:-0}"

if [ "${PRESEED_ROWS}" -gt 0 ]; then
    echo -e "${YELLOW}배경 데이터 ${PRESEED_ROWS}행 생성...${NC}"
    mysql_exec -e "INSERT INTO products (owner_id, product_code, product_price, product_currency_code, stock, name, created_at, updated_at)
                   VALUES (1, 'SEED-0', 1000.00, 'KOR', 100, '배경상품', NOW(6), NOW(6));" 2>/dev/null

    current=1
    generation=0
    while [ "${current}" -lt "${PRESEED_ROWS}" ]; do
        generation=$((generation + 1))
        want=$((PRESEED_ROWS - current))
        [ "${want}" -gt "${current}" ] && want="${current}"

        mysql_exec -e "INSERT INTO products (owner_id, product_code, product_price, product_currency_code, stock, name, created_at, updated_at)
                       SELECT 1 + (id % 100), CONCAT('SEED-${generation}-', id), product_price, product_currency_code,
                              stock, name, created_at, updated_at
                       FROM products LIMIT ${want};" 2>/dev/null
        current=$((current + want))
    done
fi

PRODUCT_COUNT=$(mysql_exec -N -B -e "SELECT COUNT(*) FROM products;" 2>/dev/null)
SELLER_COUNT=$(mysql_exec -N -B -e "SELECT COUNT(*) FROM members WHERE member_type = 'SELLER';" 2>/dev/null)

if [ "${PRODUCT_COUNT}" != "${PRESEED_ROWS}" ] || [ "${SELLER_COUNT}" != "10" ]; then
    echo -e "${RED}✗ 시드 검증 실패: 상품 ${PRODUCT_COUNT}개(기대 ${PRESEED_ROWS}) / 판매자 ${SELLER_COUNT}명(기대 10)${NC}"
    exit 1
fi
echo -e "${GREEN}✓ 상품 ${PRODUCT_COUNT}개, 판매자 ${SELLER_COUNT}명(ownerId 1~10)${NC}"

# 등록 성공 행마다 Redis에 초기 재고가 심긴다. 비우지 않으면 키가 수십만 개씩 쌓여
# 다음 실행의 Redis 메모리·쓰기 지연에 얹힌다.
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
echo -e "${GREEN}✓ Redis 비움${NC}"
