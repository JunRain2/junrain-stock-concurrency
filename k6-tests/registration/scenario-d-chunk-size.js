/**
 * 시나리오 D: 청크 크기
 *
 * 요청 크기를 고정한 채 서버의 bulk-insert.chunk-size만 바꿔 행당 비용을 비교한다.
 * 바뀌는 것은 요청 하나가 보내는 문장 수뿐이다 - 5,000행 요청 기준:
 *
 *   청크  250 -> INSERT 20 + 역조회 20 = 40문장
 *   청크 1000 -> INSERT  5 + 역조회  5 = 10문장
 *   청크 4000 -> INSERT  2 + 역조회  2 =  4문장
 *
 * [시나리오 A](scenario-a-batch-size.js)는 요청 크기를 바꾼다. 청크는 거기서 1,000 고정이므로
 * A의 결과로는 청크 값을 정할 수 없다. 이 시나리오가 설계 문서 8.5를 닫는 측정이다.
 *
 * 청크는 서버 설정이라 실행 하나가 값 하나만 잡는다. 스윕은 sweep-chunk.sh가 돈다.
 *
 *   CHUNK_SIZE=250 docker compose ... up -d --wait app
 *   k6 run --env CHUNK=250 scenario-d-chunk-size.js
 */

import exec from 'k6/execution';
import { buildRows, registerBulk, report, stageThresholds, uniqueCode, TREND_STATS } from './common.js';

// 서버가 실제로 쓰는 값과 같아야 한다. k6가 읽을 방법이 없으므로 러너가 양쪽에 같은 값을 준다
const CHUNK = Number(__ENV.CHUNK || 0);
const BATCH_SIZE = Number(__ENV.BATCH || 5000);
const VUS = Number(__ENV.VUS || 1);
const ITERATIONS_PER_VU = 20;

if (CHUNK <= 0) {
  throw new Error('CHUNK를 줘야 한다. 서버 기동 시의 CHUNK_SIZE와 같은 값이어야 결과 라벨이 맞는다');
}

export const options = {
  scenarios: {
    chunk: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: ITERATIONS_PER_VU,
      maxDuration: '30m',
    },
  },
  thresholds: stageThresholds([CHUNK]),
  summaryTrendStats: TREND_STATS,
};

export function setup() {
  if (BATCH_SIZE < CHUNK) {
    exec.test.abort(`요청(${BATCH_SIZE}행)이 청크(${CHUNK})보다 작으면 청크가 하나뿐이라 비교가 성립하지 않는다`);
  }
}

export default function () {
  registerBulk(buildRows(BATCH_SIZE, (i) => uniqueCode('D', i)), CHUNK);
}

export function handleSummary(data) {
  const chunks = Math.ceil(BATCH_SIZE / CHUNK);
  const summary = report(`D: 청크 ${CHUNK} (${BATCH_SIZE}행 = 청크 ${chunks}개, 문장 ${chunks * 2}개)`, '청크', [CHUNK], data);

  return {
    stdout: summary,
    [`k6-tests/results/registration/d-chunk-${CHUNK}-batch-${BATCH_SIZE}.json`]: JSON.stringify(data, null, 2),
    [`k6-tests/results/registration/d-chunk-${CHUNK}-batch-${BATCH_SIZE}.txt`]: summary,
  };
}
