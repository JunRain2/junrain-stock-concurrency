/**
 * 시나리오 A: 배치 크기 (분모)
 *
 * 동시 요청 없이 배치 크기만 바꾼다. 행 수가 100배 차이 나는 요청들의 행당 비용을 비교해
 * 고정비(요청 왕복·검증)와 변동비(행당 삽입)를 가른다.
 *
 * 여기서 나온 행/초가 경합 없는 천장이다. B·C의 분모가 된다.
 */

import { buildRows, registerBulk, report, stageThresholds, uniqueCode, TREND_STATS } from './common.js';
import exec from 'k6/execution';

const BATCH_SIZES = [100, 500, 1000, 5000];
const ITERATIONS_PER_SIZE = 20;

// 단계를 시나리오로 쪼개지 않고 이터레이션 번호로 가른다. 요청 하나의 소요를 미리 모르는데
// startTime으로 배치하면 앞 단계가 넘칠 때 구간이 겹쳐 측정이 조용히 오염된다.
export const options = {
  scenarios: {
    ladder: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: BATCH_SIZES.length * ITERATIONS_PER_SIZE,
      maxDuration: '30m',
    },
  },
  thresholds: stageThresholds(BATCH_SIZES),
  summaryTrendStats: TREND_STATS,
};

export default function () {
  const size = BATCH_SIZES[Math.floor(exec.scenario.iterationInTest / ITERATIONS_PER_SIZE)];

  registerBulk(buildRows(size, (i) => uniqueCode('A', i)), size);
}

export function handleSummary(data) {
  return {
    stdout: report('A: 배치 크기', '행/요청', BATCH_SIZES, data, (size) => size),
    'k6-tests/results/registration/a-batch-size.json': JSON.stringify(data, null, 2),
  };
}
