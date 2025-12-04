/**
 * Step 1: 단일 상품 (경합 최대)
 * - 모든 요청이 같은 상품(ID=1)을 구매
 * - Pessimistic Lock의 최악 케이스 테스트
 * - 목표: 순차 처리 성능 및 Lock 대기 시간 측정
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { errorRate, purchaseDuration, extractMetrics, logMetrics, getCommonStyles, generateMetricCards } from './common.js';

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
  const metrics = extractMetrics(data);
  logMetrics('Step 1: 단일 상품 경합 테스트 결과', metrics);

  return {
    'k6-tests/results/step1-single-product-summary.json': JSON.stringify(data, null, 2),
    'k6-tests/results/step1-single-product-summary.html': htmlReport(metrics),
  };
}

function htmlReport(metrics) {
  return `
<!DOCTYPE html>
<html>
<head>
    <title>Step 1: 단일 상품 경합 테스트 결과</title>
    <style>${getCommonStyles('#4CAF50')}</style>
</head>
<body>
    <div class="container">
        <h1>Step 1: 단일 상품 경합 테스트 결과</h1>
        <p>동일한 상품에 대한 동시 구매 요청으로 최대 Lock 경합 상황 테스트</p>
        <h2>전체 성능 메트릭</h2>
        <div class="metric-grid">${generateMetricCards(metrics)}</div>
    </div>
</body>
</html>
  `;
}
