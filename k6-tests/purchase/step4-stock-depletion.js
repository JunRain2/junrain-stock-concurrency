/**
 * Step 4: 재고 소진 테스트
 * - 상품 ID=1에 초고강도 부하를 가해 재고 소진
 * - 재고 부족 에러 처리 검증
 * - 목표: 재고 소진 시나리오 및 에러 처리 로직 검증
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { errorRate, purchaseDuration, extractMetrics, logMetrics, getCommonStyles, generateMetricCards } from '../common/common.js';

const successCount = new Counter('success_count');
const stockErrorCount = new Counter('stock_error_count');
const otherErrorCount = new Counter('other_error_count');

// 테스트 설정
export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    // 재고 100,000개를 빠르게 소진하기 위한 초고강도 테스트
    stock_depletion: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 500 },   // 빠른 증가
        { duration: '2m', target: 1000 },   // 고강도 부하로 재고 소진
        { duration: '1m', target: 1500 },   // 최대 부하 - 재고 소진 가속
        { duration: '3m', target: 1500 },   // 재고 소진 후 에러 처리 테스트
        { duration: '30s', target: 0 },     // 종료
      ],
      tags: { scenario: 'stock_depletion' },
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<10000', 'p(99)<20000'],
    // 재고 소진 시나리오에서는 에러가 예상되므로 threshold 완화
    http_req_failed: ['rate<0.7'], // 70% 이하
    errors: ['rate<0.7'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  // 상품 ID=1 (재고 100,000개)에 집중 공격
  const payload = JSON.stringify({
    items: [
      {
        productId: 1,
        quantity: 1,
      },
    ],
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
    tags: { name: 'StockDepletion' },
  };

  const startTime = Date.now();
  const response = http.post(`${BASE_URL}/api/v1/products/reserve`, payload, params);
  const duration = Date.now() - startTime;

  // 응답 검증 및 에러 분류
  let isSuccess = false;
  let isStockError = false;

  if (response.status === 200) {
    try {
      const body = JSON.parse(response.body);
      if (body.data && body.data.purchasedProducts) {
        isSuccess = true;
      }
    } catch (e) {
      // JSON 파싱 실패
    }
  } else if (response.status === 400 || response.status === 409) {
    // 재고 부족 에러로 간주
    isStockError = true;
  }

  const success = check(response, {
    'status is 200': (r) => r.status === 200,
    'has valid response': () => isSuccess,
  });

  // 메트릭 기록
  errorRate.add(!success);
  purchaseDuration.add(duration);

  if (isSuccess) {
    successCount.add(1);
  } else if (isStockError) {
    stockErrorCount.add(1);
  } else {
    otherErrorCount.add(1);
  }

  // 빠른 재고 소진을 위해 대기 시간 최소화
  sleep(Math.random() * 0.2);
}

export function handleSummary(data) {
  const metrics = extractMetrics(data);
  const successCnt = (data.metrics.success_count && data.metrics.success_count.values) || {};
  const stockErrorCnt = (data.metrics.stock_error_count && data.metrics.stock_error_count.values) || {};
  const otherErrorCnt = (data.metrics.other_error_count && data.metrics.other_error_count.values) || {};

  logMetrics('Step 4: 재고 소진 시나리오 테스트 결과', metrics);

  console.log('[재고 소진 분석]');
  console.log('  성공한 구매: ' + (successCnt.count || 0) + '건');
  console.log('  재고 부족 에러: ' + (stockErrorCnt.count || 0) + '건');
  console.log('  기타 에러: ' + (otherErrorCnt.count || 0) + '건\n');

  return {
    'k6-tests/results/purchase/step4-stock-depletion-summary.json': JSON.stringify(data, null, 2),
    'k6-tests/results/purchase/step4-stock-depletion-summary.html': htmlReport(metrics),
  };
}

function htmlReport(metrics) {
  return `
<!DOCTYPE html>
<html>
<head>
    <title>Step 4: 재고 소진 시나리오 테스트 결과</title>
    <style>${getCommonStyles('#F44336')}</style>
</head>
<body>
    <div class="container">
        <h1>Step 4: 재고 소진 시나리오 테스트 결과</h1>
        <p>제한된 재고에 대한 집중 공격으로 재고 소진 및 에러 처리 검증</p>
        <h2>전체 성능 메트릭</h2>
        <div class="metric-grid">${generateMetricCards(metrics)}</div>
    </div>
</body>
</html>
  `;
}
