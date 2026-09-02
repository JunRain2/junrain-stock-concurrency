/**
 * 시나리오 C: 중복 비율
 *
 * 같은 5,000행 요청에서 신규 비율만 100% → 50% → 0%로 낮춘다.
 *
 * 중복 행은 INSERT IGNORE가 스킵하므로 write가 없고, 생성키도 안 돌아와 역조회 IN 절이 짧아진다.
 * 대신 실패 목록이 길어져 응답이 커진다. 중복이 늘 때 비용이 어디로 가는지 보는 것이 목적이다.
 * A의 5,000행 단계가 이 시나리오의 신규 100% 기준선과 같은 조건이다.
 */

import http from 'k6/http';
import exec from 'k6/execution';
import { buildRows, registerBulk, report, stageThresholds, uniqueCode, BULK_URL, PARAMS, TREND_STATS } from './common.js';

// 단계 이름이 곧 신규 비율(%)이다
const NEW_RATIOS = [100, 50, 0];
const ITERATIONS_PER_RATIO = 10;
const BATCH_SIZE = 5000;

const poolCode = (i) => `C-POOL-${i}`;

export const options = {
  scenarios: {
    ratios: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: NEW_RATIOS.length * ITERATIONS_PER_RATIO,
      maxDuration: '30m',
    },
  },
  thresholds: stageThresholds(NEW_RATIOS),
  summaryTrendStats: TREND_STATS,
};

/**
 * 중복으로 쓸 코드를 미리 심는다.
 *
 * setup은 시나리오 밖이라 exec.scenario가 없다. registerBulk를 쓰지 않고 직접 보내며,
 * 이 요청의 지연은 측정 대상이 아니므로 태그도 달지 않는다.
 */
export function setup() {
  const rows = buildRows(BATCH_SIZE, poolCode);
  const res = http.post(BULK_URL, JSON.stringify({ products: rows }), PARAMS);

  if (res.status !== 200 || res.json('data').successCount !== BATCH_SIZE) {
    exec.test.abort(`중복 풀 시드 실패: status=${res.status} body=${res.body}`);
  }
}

export default function () {
  const ratio = NEW_RATIOS[Math.floor(exec.scenario.iterationInTest / ITERATIONS_PER_RATIO)];
  const newCount = Math.round((BATCH_SIZE * ratio) / 100);

  // 앞쪽을 신규로, 뒤쪽을 기존 코드로 채운다. 기존 코드는 요청 안에서 서로 겹치면 안 된다 -
  // 겹치면 DB 중복이 아니라 "요청 내 중복"으로 분류돼 다른 경로를 재게 된다.
  registerBulk(
    buildRows(BATCH_SIZE, (i) => (i < newCount ? uniqueCode('C', i) : poolCode(i))),
    ratio,
  );
}

export function handleSummary(data) {
  return {
    stdout: report('C: 중복 비율', '신규%', NEW_RATIOS, data),
    'k6-tests/results/registration/c-duplicate-ratio.json': JSON.stringify(data, null, 2),
  };
}
