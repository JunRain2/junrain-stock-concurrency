/**
 * Step 3: 혼합 시나리오 (실제 운영 환경 시뮬레이션)
 * - Hot Item: 20%의 요청이 인기 상품(ID=1) 집중
 * - 나머지 80%는 다른 상품들에 분산
 * - 장바구니 시나리오: 한 번에 여러 상품 구매
 * - 실제 사용자 행동 패턴 반영
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { errorRate, purchaseDuration, extractMetrics, logMetrics, getCommonStyles, generateMetricCards } from './common.js';

const cartSize = new Trend('cart_size');
const hotItemRequests = new Counter('hot_item_requests');
const normalItemRequests = new Counter('normal_item_requests');

// 테스트 설정
export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    // 일반 사용자 트래픽 (80%)
    normal_traffic: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: 80 },   // Warm-up
        { duration: '5m', target: 80 },   // Steady state
        { duration: '2m', target: 160 },  // Peak time
        { duration: '3m', target: 160 },  // Sustained peak
        { duration: '2m', target: 80 },   // Cool down
        { duration: '1m', target: 0 },    // Shutdown
      ],
      exec: 'normalUser',
      tags: { scenario: 'normal_traffic' },
    },
    // Hot Item 집중 트래픽 (20%)
    hot_item_traffic: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: 20 },
        { duration: '5m', target: 20 },
        { duration: '2m', target: 40 },
        { duration: '3m', target: 40 },
        { duration: '2m', target: 20 },
        { duration: '1m', target: 0 },
      ],
      exec: 'hotItemUser',
      startTime: '0s',
      tags: { scenario: 'hot_item_traffic' },
    },
    // 스파이크 테스트 (갑작스런 트래픽 급증)
    spike_test: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 200 },  // 급증
        { duration: '1m', target: 200 },   // 유지
        { duration: '10s', target: 0 },    // 급감
      ],
      exec: 'normalUser',
      startTime: '10m',
      tags: { scenario: 'spike_test' },
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<4000', 'p(99)<8000'],
    http_req_failed: ['rate<0.1'], // 혼합 시나리오는 10% 허용
    errors: ['rate<0.1'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 일반 사용자: 랜덤 상품 구매 또는 장바구니
export function normalUser() {
  const isCartPurchase = Math.random() < 0.3; // 30% 확률로 장바구니 구매

  let items;
  if (isCartPurchase) {
    // 장바구니: 2~5개 상품
    const itemCount = Math.floor(Math.random() * 4) + 2;
    items = [];
    const selectedProducts = new Set();

    for (let i = 0; i < itemCount; i++) {
      let productId;
      do {
        productId = Math.floor(Math.random() * 10) + 1;
      } while (selectedProducts.has(productId));

      selectedProducts.add(productId);
      items.push({
        productId: productId,
        quantity: Math.floor(Math.random() * 3) + 1, // 1~3개
      });
    }
    cartSize.add(items.length);
  } else {
    // 단일 상품 구매
    items = [
      {
        productId: Math.floor(Math.random() * 10) + 1,
        quantity: 1,
      },
    ];
    cartSize.add(1);
  }

  const payload = JSON.stringify({ items });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
    tags: {
      name: 'NormalUserPurchase',
      purchaseType: isCartPurchase ? 'cart' : 'single',
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
  normalItemRequests.add(1);

  // 사용자 행동 시뮬레이션
  sleep(Math.random() * 5 + 1); // 1~6초 대기
}

// Hot Item 사용자: 주로 상품 ID=1 구매
export function hotItemUser() {
  const payload = JSON.stringify({
    items: [
      {
        productId: 1, // Hot Item
        quantity: Math.floor(Math.random() * 3) + 1,
      },
    ],
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
    tags: {
      name: 'HotItemPurchase',
      productId: '1',
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
  hotItemRequests.add(1);
  cartSize.add(1);

  sleep(Math.random() * 3 + 1); // 1~4초 대기
}

export function handleSummary(data) {
  const metrics = extractMetrics(data);
  const cartSizeMetric = data.metrics.cart_size?.values || {};
  const hotItemReqs = data.metrics.hot_item_requests?.values || {};
  const normalItemReqs = data.metrics.normal_item_requests?.values || {};

  logMetrics('Step 3: 혼합 시나리오 테스트 결과', metrics);

  console.log('[사용자 행동 패턴]');
  console.log('  평균 장바구니 크기: ' + (cartSizeMetric.avg || 0).toFixed(2) + '개');
  console.log('  Hot Item 요청: ' + (hotItemReqs.count || 0) + '회');
  console.log('  일반 상품 요청: ' + (normalItemReqs.count || 0) + '회\n');

  return {
    'k6-tests/results/step3-mixed-scenario-summary.json': JSON.stringify(data, null, 2),
    'k6-tests/results/step3-mixed-scenario-summary.html': htmlReport(metrics),
  };
}

function htmlReport(metrics) {
  return `
<!DOCTYPE html>
<html>
<head>
    <title>Step 3: 혼합 시나리오 테스트 결과</title>
    <style>${getCommonStyles('#FF9800')}</style>
</head>
<body>
    <div class="container">
        <h1>Step 3: 혼합 시나리오 테스트 결과</h1>
        <p>실제 운영 환경을 시뮬레이션한 복합 시나리오 테스트</p>
        <h2>전체 성능 메트릭</h2>
        <div class="metric-grid">${generateMetricCards(metrics)}</div>
    </div>
</body>
</html>
  `;
}
