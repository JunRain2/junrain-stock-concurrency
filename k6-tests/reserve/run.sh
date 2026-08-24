#!/bin/bash
#
# 재고 점유 부하테스트 실행. A(최악값) → B(분모) 순차.
#
# 앱 이미지 빌드부터 리포트까지 전부 여기서 한다.
#
# 사전 조건: Docker Desktop 리소스를 CPU 6 / Memory 8GB 이상으로
#
# 자세한 제약은 docs/load_test/재고점유.md

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${PROJECT_ROOT}"

BASE_URL="${BASE_URL:-http://localhost:8080}"
RESULTS_DIR="k6-tests/results/reserve"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

# 배경 잡음(다른 빌드, 브라우저 등)과 결과를 맞춰보려면 시각이 필요하다.
kst() { TZ=Asia/Seoul date '+%Y-%m-%d %H:%M:%S KST'; }

STARTED_AT=$(kst)
SECONDS=0
echo -e "${GREEN}시작  ${STARTED_AT}${NC}"

mkdir -p "${RESULTS_DIR}"

COMPOSE=(docker compose -f docker-compose.yml -f docker-compose.bench.yml)

# 코드가 바뀌었을 수 있으므로 항상 다시 빌드한다. 이미지가 낡으면 옛 코드를 재게 된다.
# --wait 은 healthcheck가 통과할 때까지 잡고 있는다.
echo -e "${YELLOW}이미지 빌드 + 기동 (첫 실행은 수 분)${NC}"
"${COMPOSE[@]}" up -d --build --wait

# fd 고갈 방지. 1200 RPS에서 macOS 기본값(256)이면 마른다.
CURRENT_NOFILE=$(ulimit -n)
if [ "${CURRENT_NOFILE}" != "unlimited" ] && [ "${CURRENT_NOFILE}" -lt 10240 ]; then
    echo -e "${YELLOW}ulimit -n 이 ${CURRENT_NOFILE}. 10240으로 올린다${NC}"
    ulimit -n 10240 || {
        echo "ulimit 상향 실패. 셸에서 직접 실행: ulimit -n 10240"
        exit 1
    }
fi

run_scenario() {
    local name="$1"
    local script="$2"

    echo -e "\n${YELLOW}[${name}] 초기화  $(kst)${NC}"
    BASE_URL="${BASE_URL}" bash "${SCRIPT_DIR}/reset.sh"

    echo -e "${YELLOW}[${name}] 실행 (약 5분)${NC}"
    k6 run --env BASE_URL="${BASE_URL}" "${script}" || true
}

run_scenario "A 단일 행 경합" "${SCRIPT_DIR}/scenario-a-single-row.js"
run_scenario "B 경합 분산"   "${SCRIPT_DIR}/scenario-b-spread.js"

echo -e "\n${YELLOW}리포트 생성${NC}"
python3 "${SCRIPT_DIR}/report.py"

ELAPSED=${SECONDS}
echo -e "\n${GREEN}완료${NC}"
echo -e "  시작  ${STARTED_AT}"
echo -e "  종료  $(kst)"
echo -e "  소요  $((ELAPSED / 60))분 $((ELAPSED % 60))초"
echo -e "  원본  ${RESULTS_DIR}/*.json"
echo -e "  리포트 ${RESULTS_DIR}/report.html"
echo -e "${YELLOW}환경 스탬프를 결과 옆에 남길 것 (docs/load_test/재고점유.md).${NC}"
