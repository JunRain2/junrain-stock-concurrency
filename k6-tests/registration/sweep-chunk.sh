#!/bin/bash
#
# 시나리오 D 스윕. bulk-insert.chunk-size만 바꿔 가며 같은 요청을 반복한다.
#
# 청크는 서버 설정이라 값마다 앱을 다시 띄운다. 그래서 run.sh와 분리했다 -
# 전체 실행에 끼우면 회차마다 기동 시간이 붙고, ddl-auto=create가 배경 데이터를 지운다.
#
#   bash k6-tests/registration/sweep-chunk.sh
#   PRESEED_ROWS=1000000 bash k6-tests/registration/sweep-chunk.sh   # 배경 데이터까지
#
# 자세한 것은 docs/usecase/상품등록/02-부하테스트-모델.md 6.2

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${PROJECT_ROOT}"

BASE_URL="${BASE_URL:-http://localhost:8080}"
RESULTS_DIR="k6-tests/results/registration"
# 5,000행 요청 기준 청크 20개 ~ 2개. 8,191을 넘기면 파라미터 상한에 걸려 문장이 깨진다
read -ra CHUNKS <<< "${CHUNKS:-250 500 1000 2000 4000}"
BATCH="${BATCH:-5000}"
PRESEED_ROWS="${PRESEED_ROWS:-0}"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

kst() { TZ=Asia/Seoul date '+%Y-%m-%d %H:%M:%S KST'; }

STARTED_AT=$(kst)
SECONDS=0
echo -e "${GREEN}시작  ${STARTED_AT}${NC}"
mkdir -p "${RESULTS_DIR}"

COMPOSE=(docker compose -f docker-compose.yml -f docker-compose.bench.yml)

for chunk in "${CHUNKS[@]}"; do
    echo -e "\n${YELLOW}[청크 ${chunk}] 앱 재기동  $(kst)${NC}"
    # 앱만 다시 띄운다. ddl-auto=create라 이 기동이 테이블을 새로 만든다
    CHUNK_SIZE="${chunk}" "${COMPOSE[@]}" up -d --wait app

    echo -e "${YELLOW}[청크 ${chunk}] 초기화${NC}"
    BASE_URL="${BASE_URL}" PRESEED_ROWS="${PRESEED_ROWS}" bash "${SCRIPT_DIR}/reset.sh"

    echo -e "${YELLOW}[청크 ${chunk}] 실행${NC}"
    k6 run --env BASE_URL="${BASE_URL}" --env CHUNK="${chunk}" --env BATCH="${BATCH}" \
        "${SCRIPT_DIR}/scenario-d-chunk-size.js" || true
done

# 기본값으로 되돌린다. 안 그러면 다음 사람이 마지막 청크 값으로 측정하게 된다
echo -e "\n${YELLOW}앱을 기본 청크(application.yml)로 되돌린다${NC}"
"${COMPOSE[@]}" up -d --wait app

echo -e "\n${GREEN}=== 청크별 요약 (요청 ${BATCH}행) ===${NC}"
for chunk in "${CHUNKS[@]}"; do
    [ -f "${RESULTS_DIR}/d-chunk-${chunk}-batch-${BATCH}.txt" ] && cat "${RESULTS_DIR}/d-chunk-${chunk}-batch-${BATCH}.txt"
done

ELAPSED=${SECONDS}
echo -e "\n${GREEN}완료${NC}  소요 $((ELAPSED / 60))분 $((ELAPSED % 60))초"
echo -e "  원본  ${RESULTS_DIR}/d-chunk-*.json"
echo -e "${YELLOW}배경 데이터를 쓴 스윕이면 회차마다 재시드된다 - 시드 시간이 결과에 안 섞이는지 로그로 확인할 것${NC}"
