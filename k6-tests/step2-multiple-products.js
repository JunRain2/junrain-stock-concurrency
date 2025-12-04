/**
 * Step 2: 다중 상품 (경합 분산)
 * - 요청마다 다른 상품을 구매 (ID=1~10 랜덤)
 * - Lock 경합이 분산됨
 * - ORDER BY로 인한 데드락 방지 효과 측정
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { errorRate, purchaseDuration, extractMetrics, logMetrics, getCommonStyles, generateMetricCards } from './common.js';

const productDistribution = new Counter('product_distribution');

// 테스트 설정
export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    // 시나리오 1: 낮은 부하
    low_load: {
      executor: 'constant-vus',
      vus: 20,
      duration: '30s',
      startTime: '0s',
      tags: { scenario: 'low_load' },
    },
    // 시나리오 2: 중간 부하
    medium_load: {
      executor: 'constant-vus',
      vus: 100,
      duration: '30s',
      startTime: '35s',
      tags: { scenario: 'medium_load' },
    },
    // 시나리오 3: 높은 부하
    high_load: {
      executor: 'constant-vus',
      vus: 200,
      duration: '30s',
      startTime: '70s',
      tags: { scenario: 'high_load' },
    },
    // 시나리오 4: 매우 높은 부하 (병렬 처리 한계 테스트)
    very_high_load: {
      executor: 'constant-vus',
      vus: 500,
      duration: '30s',
      startTime: '105s',
      tags: { scenario: 'very_high_load' },
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<3000', 'p(99)<5000'], // 단일 상품보다 빠를 것으로 예상
    http_req_failed: ['rate<0.05'],
    errors: ['rate<0.05'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  // 1~10 중 랜덤 상품 선택 (경합 분산)
  const productId = Math.floor(Math.random() * 10) + 1;

  const payload = JSON.stringify({
    items: [
      {
        productId: productId,
        quantity: 1,
      },
    ],
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
    tags: {
      name: 'PurchaseMultipleProducts',
      productId: productId.toString(),
    },
  };

  const startTime = Date.now();
  const response = http.post(`${BASE_URL}/api/v1/products/purchase`, payload, params);
  const duration = Date.now() - startTime;

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

  errorRate.add(!success);
  purchaseDuration.add(duration);
  productDistribution.add(1, { productId: productId.toString() });

  sleep(Math.random() * 2);
}

export function handleSummary(data) {
  const metrics = extractMetrics(data);
  logMetrics('Step 2: 다중 상품 분산 테스트 결과', metrics);

  return {
    'k6-tests/results/step2-multiple-products-summary.json': JSON.stringify(data, null, 2),
    'k6-tests/results/step2-multiple-products-summary.html': htmlReport(metrics),
  };
}

function htmlReport(metrics) {
  return `
<!DOCTYPE html>
<html>
<head>
    <title>Step 2: 다중 상품 분산 테스트 결과</title>
    <style>${getCommonStyles('#2196F3')}</style>
</head>
<body>
    <div class="container">
        <h1>Step 2: 다중 상품 분산 테스트 결과</h1>
        <p>10개 상품에 대한 랜덤 분산 요청으로 Lock 경합 최소화 테스트</p>
        <h2>전체 성능 메트릭</h2>
        <div class="metric-grid">${generateMetricCards(metrics)}</div>
    </div>
</body>
</html>
  `;
}
