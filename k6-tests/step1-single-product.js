/**
 * Step 1: 단일 상품 (경합 최대)
 * - 모든 요청이 같은 상품(ID=1)을 구매
 * - Pessimistic Lock의 최악 케이스 테스트
 * - 목표: 순차 처리 성능 및 Lock 대기 시간 측정
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// 커스텀 메트릭
const errorRate = new Rate('errors');
const purchaseDuration = new Trend('purchase_duration');

// 테스트 설정
export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    // 시나리오 1: 낮은 부하 (Baseline)
    low_load: {
      executor: 'constant-vus',
      vus: 10,
      duration: '30s',
      startTime: '0s',
      tags: { scenario: 'low_load' },
    },
    // 시나리오 2: 중간 부하
    medium_load: {
      executor: 'constant-vus',
      vus: 50,
      duration: '30s',
      startTime: '35s',
      tags: { scenario: 'medium_load' },
    },
    // 시나리오 3: 높은 부하
    high_load: {
      executor: 'constant-vus',
      vus: 100,
      duration: '30s',
      startTime: '70s',
      tags: { scenario: 'high_load' },
    },
    // 시나리오 4: 점진적 증가 (Ramp-up)
    ramp_up: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 100 },
        { duration: '2m', target: 100 },
        { duration: '1m', target: 200 },
        { duration: '2m', target: 200 },
        { duration: '1m', target: 0 },
      ],
      startTime: '105s',
      tags: { scenario: 'ramp_up' },
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<5000', 'p(99)<10000'], // 95%는 5초 미만, 99%는 10초 미만
    http_req_failed: ['rate<0.05'], // 실패율 5% 미만
    errors: ['rate<0.05'],
  },
};

// 환경 변수
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  // 단일 상품 (ID=1) 구매 요청
  const payload = JSON.stringify({
    items: [
      {
        productId: 1, // 항상 같은 상품 -> 최대 경합
        quantity: 1,
      },
    ],
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
    tags: { name: 'PurchaseSingleProduct' },
  };

  const startTime = Date.now();
  const response = http.post(`${BASE_URL}/api/v1/products/purchase`, payload, params);
  const duration = Date.now() - startTime;

  // 응답 검증
  const success = check(response, {
    'status is 200': (r) => r.status === 200,
    'response has data': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.data && body.data.purchasedProducts;
      } catch (e) {
        return false;
      }
    },
  });

  // 메트릭 기록
  errorRate.add(!success);
  purchaseDuration.add(duration);

  // Think time (실제 사용자 행동 시뮬레이션)
  sleep(Math.random() * 2); // 0~2초 랜덤 대기
}

export function handleSummary(data) {
  console.log('\n=== Step 1: 단일 상품 경합 테스트 결과 ===\n');

  const metrics = data.metrics;
  const httpReqs = metrics.http_reqs && metrics.http_reqs.values ? metrics.http_reqs.values : {};
  const httpReqDuration = metrics.http_req_duration && metrics.http_req_duration.values ? metrics.http_req_duration.values : {};
  const errors = metrics.errors && metrics.errors.values ? metrics.errors.values : {};

  console.log('[전체 성능 메트릭]');
  console.log('  총 요청 수: ' + (httpReqs.count || 0));
  console.log('  평균 응답 시간: ' + (httpReqDuration.avg || 0).toFixed(2) + 'ms');
  console.log('  P95 응답 시간: ' + (httpReqDuration['p(95)'] || 0).toFixed(2) + 'ms');
  console.log('  P99 응답 시간: ' + (httpReqDuration['p(99)'] || 0).toFixed(2) + 'ms');
  console.log('  최대 응답 시간: ' + (httpReqDuration.max || 0).toFixed(2) + 'ms');
  console.log('  에러율: ' + ((errors.rate || 0) * 100).toFixed(2) + '%');
  console.log('  초당 요청 수(TPS): ' + (httpReqs.rate || 0).toFixed(2) + '\n');

  return {
    'k6-tests/results/step1-single-product-summary.json': JSON.stringify(data, null, 2),
    'k6-tests/results/step1-single-product-summary.html': htmlReport(data),
  };
}

function htmlReport(data) {
  const metrics = data.metrics;
  const httpReqs = metrics.http_reqs && metrics.http_reqs.values ? metrics.http_reqs.values : {};
  const httpReqDuration = metrics.http_req_duration && metrics.http_req_duration.values ? metrics.http_req_duration.values : {};
  const errors = metrics.errors && metrics.errors.values ? metrics.errors.values : {};

  return `
<!DOCTYPE html>
<html>
<head>
    <title>Step 1: 단일 상품 경합 테스트 결과</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }
        .container { max-width: 1200px; margin: 0 auto; background-color: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
        h1 { color: #333; border-bottom: 3px solid #4CAF50; padding-bottom: 10px; }
        h2 { color: #555; margin-top: 30px; }
        .metric-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 20px; margin: 20px 0; }
        .metric-card { background: #f9f9f9; padding: 20px; border-radius: 5px; border-left: 4px solid #4CAF50; }
        .metric-label { font-size: 14px; color: #666; margin-bottom: 5px; }
        .metric-value { font-size: 24px; font-weight: bold; color: #333; }
        .metric-unit { font-size: 16px; color: #888; }
        .good { color: #4CAF50; }
        .warning { color: #FF9800; }
        .bad { color: #F44336; }
        table { border-collapse: collapse; width: 100%; margin: 20px 0; }
        th, td { border: 1px solid #ddd; padding: 12px; text-align: left; }
        th { background-color: #4CAF50; color: white; }
        tr:nth-child(even) { background-color: #f9f9f9; }
    </style>
</head>
<body>
    <div class="container">
        <h1>Step 1: 단일 상품 경합 테스트 결과</h1>
        <p>동일한 상품에 대한 동시 구매 요청으로 최대 Lock 경합 상황 테스트</p>

        <h2>전체 성능 메트릭</h2>
        <div class="metric-grid">
            <div class="metric-card">
                <div class="metric-label">총 요청 수</div>
                <div class="metric-value">${httpReqs.count || 0}</div>
            </div>
            <div class="metric-card">
                <div class="metric-label">초당 요청 수 (TPS)</div>
                <div class="metric-value">${(httpReqs.rate || 0).toFixed(2)}</div>
            </div>
            <div class="metric-card">
                <div class="metric-label">평균 응답 시간</div>
                <div class="metric-value">${(httpReqDuration.avg || 0).toFixed(2)} <span class="metric-unit">ms</span></div>
            </div>
            <div class="metric-card">
                <div class="metric-label">P95 응답 시간</div>
                <div class="metric-value ${(httpReqDuration['p(95)'] || 0) > 5000 ? 'bad' : (httpReqDuration['p(95)'] || 0) > 3000 ? 'warning' : 'good'}">${(httpReqDuration['p(95)'] || 0).toFixed(2)} <span class="metric-unit">ms</span></div>
            </div>
            <div class="metric-card">
                <div class="metric-label">P99 응답 시간</div>
                <div class="metric-value ${(httpReqDuration['p(99)'] || 0) > 10000 ? 'bad' : (httpReqDuration['p(99)'] || 0) > 5000 ? 'warning' : 'good'}">${(httpReqDuration['p(99)'] || 0).toFixed(2)} <span class="metric-unit">ms</span></div>
            </div>
            <div class="metric-card">
                <div class="metric-label">최대 응답 시간</div>
                <div class="metric-value">${(httpReqDuration.max || 0).toFixed(2)} <span class="metric-unit">ms</span></div>
            </div>
            <div class="metric-card">
                <div class="metric-label">에러율</div>
                <div class="metric-value ${((errors.rate || 0) * 100) > 5 ? 'bad' : ((errors.rate || 0) * 100) > 1 ? 'warning' : 'good'}">${((errors.rate || 0) * 100).toFixed(2)} <span class="metric-unit">%</span></div>
            </div>
        </div>

        <h2>시나리오별 부하 단계</h2>
        <table>
            <tr>
                <th>단계</th>
                <th>VU 수</th>
                <th>지속 시간</th>
                <th>설명</th>
            </tr>
            <tr>
                <td>Warm-up</td>
                <td>10</td>
                <td>1분</td>
                <td>초기 부하 테스트</td>
            </tr>
            <tr>
                <td>Low Load</td>
                <td>50</td>
                <td>2분</td>
                <td>낮은 부하 상태</td>
            </tr>
            <tr>
                <td>Medium Load</td>
                <td>100</td>
                <td>2분</td>
                <td>중간 부하 상태</td>
            </tr>
            <tr>
                <td>High Load</td>
                <td>200</td>
                <td>2분</td>
                <td>높은 부하 - Lock 경합 최대</td>
            </tr>
        </table>
    </div>
</body>
</html>
  `;
}
