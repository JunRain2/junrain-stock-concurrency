/**
 * 대량 상품 등록 부하테스트 공통 설정.
 * 시나리오 정의·판정 기준은 docs/usecase/상품등록/02-부하테스트-모델.md
 */

import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import exec from 'k6/execution';

// 400(요청 전체 실패)·409는 이 테스트에서 정상 응답이 아니다. 나오면 시나리오가 잘못 짜인 것이므로
// 실패로 잡혀야 한다. 200만 기대한다.
http.setResponseCallback(http.expectedStatuses(200));

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const OWNER_ID = __ENV.OWNER_ID || '1';
export const BULK_URL = `${BASE_URL}/api/v1/products/bulk?ownerId=${OWNER_ID}`;

// 요청 하나가 최대 5,000행이라 초 단위로 끝난다. 재고 점유의 10s를 그대로 쓰면
// 정상 응답이 타임아웃으로 잘려 측정이 아니라 잡음이 된다.
export const PARAMS = {
  headers: { 'Content-Type': 'application/json' },
  timeout: '120s',
};

// 이 경로의 처리량 단위는 요청/초가 아니라 행/초다. 요청 하나의 크기가 100배까지 차이 나서
// 요청 수로는 시나리오 간 비교가 불가능하다.
export const rowsPerSec = new Trend('rows_per_sec');
export const registeredRows = new Counter('registered_rows');
export const rejectedRows = new Counter('rejected_rows');

const NAME = '부하테스트상품';
const PRICE = 1000;
const STOCK = 100;

// 실행마다 달라지는 소금. iterationInTest는 k6 실행이 새로 뜰 때마다 0부터 다시 세므로,
// 리셋 없이 두 번 돌리면 같은 코드가 다시 나가 두 번째 실행이 중복 스킵을 재게 된다.
// 배경 데이터가 큰 측정에서는 리셋(재시드)이 수 분씩 걸려 실행 사이에 안 돌리고 싶을 때가 있다.
const RUN_SALT = __ENV.RUN_ID || String(Date.now());

/**
 * 실행 안에서 유일한 상품코드.
 *
 * 코드가 겹치면 측정 대상 자체가 바뀐다 - 신규 삽입이 아니라 중복 스킵을 재게 된다.
 * iterationInTest는 시나리오 안에서 VU를 가로질러 유일하므로 (실행, 시나리오, 이터레이션, 행)이면 충분하다.
 */
export function uniqueCode(prefix, index) {
  return `${prefix}${RUN_SALT}-${exec.scenario.name}-${exec.scenario.iterationInTest}-${index}`;
}

export function buildRows(count, codeOf) {
  const rows = new Array(count);
  for (let i = 0; i < count; i++) {
    rows[i] = { name: NAME, price: PRICE, stock: STOCK, code: codeOf(i) };
  }
  return rows;
}

/**
 * 벌크 등록 한 번. 응답의 행 단위 집계를 메트릭으로 남긴다.
 *
 * 성공/실패 행 수를 세는 이유는, 의도한 것과 다른 것을 재고 있는지 확인하기 위해서다.
 * 예를 들어 신규 100%로 짠 시나리오에서 rejected가 잡히면 코드가 겹친 것이다.
 *
 * @param ownerId 생략하면 OWNER_ID. 시나리오 B는 VU마다 다른 브랜드를 쓴다
 */
export function registerBulk(rows, stage, ownerId) {
  const tags = { stage: String(stage) };
  const url = ownerId === undefined ? BULK_URL : `${BASE_URL}/api/v1/products/bulk?ownerId=${ownerId}`;
  // k6 0.51의 babel이 오브젝트 스프레드를 못 읽어 Object.assign을 쓴다
  const params = Object.assign({}, PARAMS, { tags: tags });
  const res = http.post(url, JSON.stringify({ products: rows }), params);

  const body = res.status === 200 ? res.json('data') : null;

  check(res, {
    '200': (r) => r.status === 200,
    '모든 행이 보고됐다': () => body !== null && body.successCount + body.failureCount === rows.length,
  });

  if (body !== null) {
    registeredRows.add(body.successCount, tags);
    rejectedRows.add(body.failureCount, tags);
  }
  rowsPerSec.add(rows.length / (res.timings.duration / 1000), tags);

  return body;
}

/**
 * 단계 태그별 서브메트릭을 요약에 남기는 threshold.
 *
 * k6는 threshold가 걸린 서브메트릭만 요약에 싣는다. 값 판정이 목적이 아니라 집계가 목적이므로
 * 항상 참인 조건을 건다. Trend는 threshold 집계로 count를 지원하지 않아 max로 건다.
 */
export function stageThresholds(stages) {
  const thresholds = { http_req_failed: ['rate<0.01'], checks: ['rate>0.99'] };

  for (const stage of stages) {
    thresholds[`http_req_duration{stage:${stage}}`] = ['max>=0'];
    thresholds[`rows_per_sec{stage:${stage}}`] = ['max>=0'];
    thresholds[`registered_rows{stage:${stage}}`] = ['count>=0'];
    thresholds[`rejected_rows{stage:${stage}}`] = ['count>=0'];
  }
  return thresholds;
}

export const TREND_STATS = ['avg', 'med', 'p(95)', 'max', 'count'];

/**
 * 단계별 결과 표.
 *
 * @param label   단계 열의 제목 (배치 크기 / 동시 요청 수 / 신규 비율)
 * @param rowsOf  단계 하나가 요청당 보낸 행 수. 행/초를 요청 수로 되돌리는 데 쓴다
 */
export function report(title, label, stages, data, rowsOf) {
  const pad = (s, n) => String(s).padStart(n);
  const metric = (name) => (data.metrics[name] ? data.metrics[name].values : null);

  const lines = ['', `=== ${title} ===`, ''];
  lines.push(`  ${label.padStart(8)}   요청수      p95      행/초     성공행     실패행`);

  for (const stage of stages) {
    const d = metric(`http_req_duration{stage:${stage}}`);
    const rps = metric(`rows_per_sec{stage:${stage}}`);
    if (!d || !rps) {
      lines.push(`  ${pad(stage, 8)}   (측정 없음)`);
      continue;
    }
    const ok = metric(`registered_rows{stage:${stage}}`) || { count: 0 };
    const ng = metric(`rejected_rows{stage:${stage}}`) || { count: 0 };

    lines.push(
      `  ${pad(stage, 8)} ${pad(d.count, 8)} ${pad(d['p(95)'].toFixed(0) + 'ms', 9)} ` +
        `${pad(rps.med.toFixed(0), 10)} ${pad(ok.count, 10)} ${pad(ng.count, 10)}`,
    );
  }
  lines.push('');

  // 행/초는 중앙값을 싣는다. 요청 하나가 초 단위라 표본이 수십 건뿐이고, 평균은 첫 요청의
  // 커넥션 수립·JIT 워밍업 비용에 끌려간다.
  const warns = [];
  const failed = metric('http_req_failed');
  const checks = metric('checks');
  if (failed && failed.rate > 0) {
    warns.push(`http_req_failed=${(failed.rate * 100).toFixed(2)}% — 200 아닌 응답이 섞였다. 시나리오 전제를 확인한다`);
  }
  if (checks && checks.rate < 1) {
    warns.push(`checks 실패 ${(100 - checks.rate * 100).toFixed(2)}% — 보고된 행 수가 보낸 행 수와 다르다`);
  }
  if (rowsOf) {
    for (const stage of stages) {
      const ng = metric(`rejected_rows{stage:${stage}}`);
      const d = metric(`http_req_duration{stage:${stage}}`);
      if (!ng || !d) continue;
      const expected = rowsOf(stage) * d.count;
      if (expected > 0 && ng.count > expected * 0.5) {
        warns.push(`${label} ${stage}: 실패행이 절반을 넘는다(${ng.count}/${expected}) — 코드가 겹쳤을 수 있다`);
      }
    }
  }
  if (warns.length > 0) {
    lines.push('  [경고]');
    warns.forEach((w) => lines.push(`   - ${w}`));
    lines.push('');
  }

  return lines.join('\n') + '\n';
}
