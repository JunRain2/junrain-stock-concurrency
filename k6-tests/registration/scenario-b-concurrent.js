/**
 * 시나리오 B: 동시 벌크 요청
 *
 * 브랜드 여럿이 같은 시간에 대량 등록을 하는 상황이다. 요청 하나가 청크마다 커넥션을 빌렸다
 * 돌려주므로(설계 문서 8.4), 동시 요청 수가 늘면 풀 대기가 어디서 시작되는지 여기서 보인다.
 *
 * 동시성 수준 하나가 k6 실행 하나다. 수준을 한 실행에 몰아넣으면 앞 수준이 길어질 때
 * 구간이 겹치고, 앞 수준이 남긴 행이 뒤 수준의 인덱스 크기를 바꾼다.
 *
 *   k6 run --env VUS=8 scenario-b-concurrent.js                    # 사다리 한 칸
 *   k6 run --env VUS=10 --env BATCH=5000 scenario-b-concurrent.js  # 피크(동시 50,000행)
 *
 * 부하 산정 근거는 모델 문서 3.2.
 */

import { buildRows, registerBulk, report, stageThresholds, uniqueCode, TREND_STATS } from './common.js';

const VUS = Number(__ENV.VUS || 1);
const ITERATIONS_PER_VU = Number(__ENV.ITERATIONS || 10);

// 기본 1,000행 = 청크 1개. 요청당 문장 수를 최소로 묶어야 꺾이는 원인이 커넥션인지 문장 수인지 갈린다.
// 피크 조합(브랜드 10 x 5,000행)은 이 값을 올려서 만든다: --env VUS=10 --env BATCH=5000
const BATCH_SIZE = Number(__ENV.BATCH || 1000);

// VU를 브랜드에 나눠 준다. 전부 한 owner로 보내면 owner_id 인덱스 삽입이 한 값에 몰려
// 실제로는 없을 핫스팟이 생긴다. seed.sql이 심는 판매자 수가 상한이다.
const BRANDS = Number(__ENV.BRANDS || 10);
const brandOf = (vu) => ((vu - 1) % BRANDS) + 1;

export const options = {
  scenarios: {
    concurrent: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: ITERATIONS_PER_VU,
      maxDuration: '30m',
    },
  },
  thresholds: stageThresholds([VUS]),
  summaryTrendStats: TREND_STATS,
};

export default function () {
  registerBulk(buildRows(BATCH_SIZE, (i) => uniqueCode('B', i)), VUS, brandOf(__VU));
}

export function handleSummary(data) {
  const brands = Math.min(VUS, BRANDS);
  const summary = report(
    `B: 동시 ${VUS} x ${BATCH_SIZE}행 (브랜드 ${brands}곳, 동시 ${VUS * BATCH_SIZE}행)`,
    '동시요청',
    [VUS],
    data,
    () => BATCH_SIZE,
  );

  return {
    stdout: summary,
    [`k6-tests/results/registration/b-vus-${VUS}-rows-${BATCH_SIZE}.json`]: JSON.stringify(data, null, 2),
    [`k6-tests/results/registration/b-vus-${VUS}-rows-${BATCH_SIZE}.txt`]: summary,
  };
}
