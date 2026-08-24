/**
 * 재고 점유 부하테스트 공통 설정.
 * 시나리오 정의는 docs/usecase/재고점유/02-부하테스트-모델.md
 */

import http from 'k6/http';

// 재고 부족(400)·락 획득 실패(409)는 비즈니스적으로 정상 응답이다.
// 기본값이면 http_req_failed에 잡혀 실패율과 threshold를 오염시킨다.
// options 필드가 아니라 init 컨텍스트에서 전역으로 건다.
http.setResponseCallback(http.expectedStatuses(200, 400, 409));

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const RESERVE_URL = `${BASE_URL}/api/v1/products/reserve`;

// 이 서비스의 SLO. threshold는 SLO와 같아야 통과/실패가 신호가 된다.
const SLO_P95_MS = 200;

const WARMUP_SEC = 60;
const STEP_SEC = 40;
const GAP_SEC = 15;

// p99는 표본이 이만큼 쌓인 구간에서만 읽는다. 그 아래는 상위 몇 건이 값을 정한다.
const P99_MIN_SAMPLES = 10000;

export const PARAMS = {
  headers: { 'Content-Type': 'application/json' },
  // 매달린 요청 하나가 VU를 계속 잡으면 도착률이 무너진다
  timeout: '10s',
};

/**
 * 도착률 고정 사다리를 만든다.
 *
 * 단계마다 독립 시나리오 + rate 태그라 결과가 섞이지 않는다.
 * 워밍업은 threshold를 걸지 않는다 = 결과를 버린다는 뜻.
 */
export function buildOptions(rates) {
  // 워밍업은 사다리 최고 rate로 돈다.
  // 낮은 rate로 데우면 커넥션이 그만큼만 열린 채 끝나고, 첫 측정 구간이 나머지 연결을
  // 한꺼번에 여는 비용을 혼자 떠안는다. 그 구간만 꼬리가 두꺼워져 부하와 무관하게 실패한다.
  const warmupRate = Math.max(...rates);

  const scenarios = {
    warmup: {
      executor: 'constant-arrival-rate',
      rate: warmupRate,
      timeUnit: '1s',
      duration: `${WARMUP_SEC}s`,
      preAllocatedVUs: Math.min(warmupRate, 1000),
      maxVUs: Math.min(warmupRate * 3, 2500),
      startTime: '0s',
      // threshold를 걸지 않는다 = 결과에서 제외한다.
      tags: { rate: 'warmup' },
    },
  };

  // setResponseCallback 덕분에 5xx와 타임아웃만 실패로 잡힌다.
  const thresholds = { http_req_failed: ['rate<0.01'] };

  // 상태코드별 건수를 요약에 남긴다. 락 획득 실패(409)는 빠르게 끝나므로,
  // 비중이 커지면 느린 요청이 실패로 빠지면서 p95가 실제보다 좋아 보인다.
  // status 는 k6 기본 시스템 태그라 threshold를 거는 것만으로 서브메트릭이 생성된다.
  // 건수만 필요하므로 조건은 항상 참인 것을 쓴다. Trend 는 threshold 집계로 count 를
  // 지원하지 않으므로(avg/min/max/med/p 만) max 로 건다. 건수는 summaryTrendStats 가 남긴다.
  for (const status of [200, 400, 409]) {
    thresholds[`http_req_duration{status:${status}}`] = ['max>=0'];
  }

  let startAt = WARMUP_SEC + GAP_SEC;
  for (const rate of rates) {
    scenarios[`rate_${rate}`] = {
      executor: 'constant-arrival-rate',
      rate: rate,
      timeUnit: '1s',
      duration: `${STEP_SEC}s`,
      preAllocatedVUs: Math.min(rate, 1000),
      maxVUs: Math.min(rate * 3, 2500),
      startTime: `${startAt}s`,
      // 기본 30초. 포화 구간에서 매달린 이터레이션을 그만큼 기다리면 실행 시간이 배가 된다.
      gracefulStop: '10s',
      tags: { rate: String(rate) },
    };
    thresholds[`http_req_duration{rate:${rate}}`] = [`p(95)<${SLO_P95_MS}`];
    startAt += STEP_SEC + GAP_SEC;
  }

  return {
    summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max', 'count'],
    scenarios: scenarios,
    thresholds: thresholds,
  };
}

/**
 * 구간별 결과 표 + 한계 rate + 유효성 경고.
 */
export function report(title, rates, data) {
  const lines = [];
  const pad = (s, n) => String(s).padStart(n);

  lines.push('');
  lines.push(`=== ${title} ===`);
  lines.push('');
  lines.push('  rate     요청수      p95        p99     판정');

  let limit = null;
  let anyPass = false;
  let anyFail = false;

  for (const rate of rates) {
    const m = data.metrics[`http_req_duration{rate:${rate}}`];
    if (!m) {
      lines.push(`${pad(rate, 6)}   (측정 없음)`);
      continue;
    }
    const v = m.values;
    const pass = v['p(95)'] < SLO_P95_MS;
    const p99 = v.count >= P99_MIN_SAMPLES ? `${v['p(99)'].toFixed(0)}ms` : '표본부족';

    if (pass) {
      anyPass = true;
      limit = rate;
    } else {
      anyFail = true;
    }

    lines.push(
      `${pad(rate, 6)} ${pad(v.count, 10)} ${pad(v['p(95)'].toFixed(0) + 'ms', 9)} ${pad(p99, 10)}   ${pass ? 'PASS' : 'FAIL'}`,
    );
  }

  lines.push('');
  lines.push(`  처리 한계: ${limit === null ? '없음 (최저 rate에서 이미 실패)' : `${limit} RPS`}  @ p95 < ${SLO_P95_MS}ms`);
  lines.push('');

  // 무효 판정
  const dropped = (data.metrics.dropped_iterations && data.metrics.dropped_iterations.values.count) || 0;
  const failedRate = (data.metrics.http_req_failed && data.metrics.http_req_failed.values.rate) || 0;

  const warns = [];
  if (dropped > 0) {
    warns.push(`dropped_iterations=${dropped} — k6가 부하를 못 냈다. 이 실행은 무효. maxVUs를 올리고 다시 잰다`);
  }
  if (failedRate > 0.01) {
    warns.push(`http_req_failed=${(failedRate * 100).toFixed(2)}% — 5xx/타임아웃이 섞였다. 에러 경로는 싸므로 지연이 좋아 보인다`);
  }
  if (!anyFail) {
    warns.push('사다리 전 구간 통과 — 한계가 이 위에 있다. rate를 올리고 다시 잰다');
  }
  if (!anyPass) {
    warns.push('사다리 전 구간 실패 — 한계가 이 아래에 있다. rate를 내리고 다시 잰다');
  }

  if (warns.length > 0) {
    lines.push('  [경고]');
    warns.forEach((w) => lines.push(`   - ${w}`));
    lines.push('');
  }

  return lines.join('\n') + '\n';
}
