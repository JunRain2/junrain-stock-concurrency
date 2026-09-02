#!/bin/bash
#
# 대량 상품 등록 부하테스트 실행. A(배치 크기) → B(동시 요청) → C(중복 비율).
#
# 시나리오 B는 동시성 수준마다 k6를 새로 띄운다. 수준 사이에 리셋이 필요해서다.
#
# 사전 조건: Docker Desktop 리소스를 CPU 6 / Memory 8GB 이상으로
# 자세한 제약은 docs/usecase/상품등록/02-부하테스트-모델.md

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${PROJECT_ROOT}"

BASE_URL="${BASE_URL:-http://localhost:8080}"
OWNER_ID="${OWNER_ID:-1}"
RESULTS_DIR="k6-tests/results/registration"
CONCURRENCY_LEVELS=(1 2 4 8 16)
# B의 기본 배치. 포화 조합(예: 10 x 5000)은 개별 실행으로 따로 잰다 - 사다리와 섞으면 축이 둘이 된다
B_BATCH="${B_BATCH:-1000}"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

kst() { TZ=Asia/Seoul date '+%Y-%m-%d %H:%M:%S KST'; }

STARTED_AT=$(kst)
SECONDS=0
echo -e "${GREEN}시작  ${STARTED_AT}${NC}"

mkdir -p "${RESULTS_DIR}"

COMPOSE=(docker compose -f docker-compose.yml -f docker-compose.bench.yml)

# 코드가 바뀌었을 수 있으므로 항상 다시 빌드한다. 이미지가 낡으면 옛 코드를 재게 된다.
echo -e "${YELLOW}이미지 빌드 + 기동 (첫 실행은 수 분)${NC}"
"${COMPOSE[@]}" up -d --build --wait

run_scenario() {
    local name="$1"; shift

    echo -e "\n${YELLOW}[${name}] 초기화  $(kst)${NC}"
    BASE_URL="${BASE_URL}" bash "${SCRIPT_DIR}/reset.sh"

    echo -e "${YELLOW}[${name}] 실행${NC}"
    k6 run --env BASE_URL="${BASE_URL}" --env OWNER_ID="${OWNER_ID}" "$@" || true
}

run_scenario "A 배치 크기" "${SCRIPT_DIR}/scenario-a-batch-size.js"

for vus in "${CONCURRENCY_LEVELS[@]}"; do
    run_scenario "B 동시 요청 ${vus}" --env VUS="${vus}" --env BATCH="${B_BATCH}" "${SCRIPT_DIR}/scenario-b-concurrent.js"
done

# 피크는 사다리와 축이 달라(크기 x 동시성) 따로 돌린다. 실제 트래픽을 본뜬 조합이다
run_scenario "B 피크 10x5000" --env VUS=10 --env BATCH=5000 "${SCRIPT_DIR}/scenario-b-concurrent.js"

run_scenario "C 중복 비율" "${SCRIPT_DIR}/scenario-c-duplicate-ratio.js"

echo -e "\n${GREEN}=== B 요약 (동시성 수준별) ===${NC}"
for vus in "${CONCURRENCY_LEVELS[@]}"; do
    [ -f "${RESULTS_DIR}/b-vus-${vus}-rows-${B_BATCH}.txt" ] && cat "${RESULTS_DIR}/b-vus-${vus}-rows-${B_BATCH}.txt"
done
[ -f "${RESULTS_DIR}/b-vus-10-rows-5000.txt" ] && cat "${RESULTS_DIR}/b-vus-10-rows-5000.txt"

ELAPSED=${SECONDS}
echo -e "\n${GREEN}완료${NC}"
echo -e "  시작  ${STARTED_AT}"
echo -e "  종료  $(kst)"
echo -e "  소요  $((ELAPSED / 60))분 $((ELAPSED % 60))초"
echo -e "  원본  ${RESULTS_DIR}/*.json"
echo -e "${YELLOW}환경 스탬프를 결과 옆에 남길 것 (docs/usecase/상품등록/02-부하테스트-모델.md).${NC}"
