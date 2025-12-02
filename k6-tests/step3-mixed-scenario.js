/**
 * Step 3: 혼합 시나리오 (실제 운영 환경 시뮬레이션)
 * - Hot Item: 20%의 요청이 인기 상품(ID=1) 집중
 * - 나머지 80%는 다른 상품들에 분산
 * - 장바구니 시나리오: 한 번에 여러 상품 구매
 * - 실제 사용자 행동 패턴 반영
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// 커스텀 메트릭
const errorRate = new Rate('errors');
const purchaseDuration = new Trend('purchase_duration');
const cartSize = new Trend('cart_size');
const hotItemRequests = new Counter('hot_item_requests');
const normalItemRequests = new Counter('normal_item_requests');

// 테스트 설정
export const options = {
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
  console.log('\n=== Step 3: 혼합 시나리오 테스트 결과 ===\n');

  const metrics = data.metrics;
  const httpReqs = metrics.http_reqs && metrics.http_reqs.values ? metrics.http_reqs.values : {};
  const httpReqDuration = metrics.http_req_duration && metrics.http_req_duration.values ? metrics.http_req_duration.values : {};
  const errors = metrics.errors && metrics.errors.values ? metrics.errors.values : {};
  const cartSize = metrics.cart_size && metrics.cart_size.values ? metrics.cart_size.values : {};
  const hotItemReqs = metrics.hot_item_requests && metrics.hot_item_requests.values ? metrics.hot_item_requests.values : {};
  const normalItemReqs = metrics.normal_item_requests && metrics.normal_item_requests.values ? metrics.normal_item_requests.values : {};

  console.log('[전체 성능 메트릭]');
  console.log('  총 요청 수: ' + (httpReqs.count || 0));
  console.log('  평균 응답 시간: ' + (httpReqDuration.avg || 0).toFixed(2) + 'ms');
  console.log('  P95 응답 시간: ' + (httpReqDuration['p(95)'] || 0).toFixed(2) + 'ms');
  console.log('  P99 응답 시간: ' + (httpReqDuration['p(99)'] || 0).toFixed(2) + 'ms');
  console.log('  최대 응답 시간: ' + (httpReqDuration.max || 0).toFixed(2) + 'ms');
  console.log('  에러율: ' + ((errors.rate || 0) * 100).toFixed(2) + '%');
  console.log('  초당 요청 수(TPS): ' + (httpReqs.rate || 0).toFixed(2));

  console.log('\n[사용자 행동 패턴]');
  console.log('  평균 장바구니 크기: ' + (cartSize.avg || 0).toFixed(2) + '개');
  console.log('  Hot Item 요청: ' + (hotItemReqs.count || 0) + '회');
  console.log('  일반 상품 요청: ' + (normalItemReqs.count || 0) + '회');

  const hotItemCount = hotItemReqs.count || 0;
  const normalItemCount = normalItemReqs.count || 0;
  const totalItemCount = hotItemCount + normalItemCount;
  const hotItemRatio = totalItemCount > 0 ? (hotItemCount / totalItemCount * 100) : 0;
  console.log('  Hot Item 비율: ' + hotItemRatio.toFixed(2) + '%\n');

  return {
    'k6-tests/results/step3-mixed-scenario-summary.json': JSON.stringify(data, null, 2),
    'k6-tests/results/step3-mixed-scenario-summary.html': htmlReport(data),
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
    <title>Step 3: 혼합 시나리오 테스트 결과</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        h1 { color: #333; }
        table { border-collapse: collapse; width: 100%; margin: 20px 0; }
        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
        th { background-color: #4CAF50; color: white; }
        .metric { font-size: 18px; margin: 10px 0; }
        .good { color: green; }
        .warning { color: orange; }
        .bad { color: red; }
    </style>
</head>
<body>
    <h1>Step 3: 혼합 시나리오 테스트 결과</h1>
    <div class="metric">총 요청 수: ${httpReqs.count || 0}</div>
    <div class="metric">평균 응답 시간: ${(httpReqDuration.avg || 0).toFixed(2)}ms</div>
    <div class="metric">P95 응답 시간: ${(httpReqDuration['p(95)'] || 0).toFixed(2)}ms</div>
    <div class="metric">P99 응답 시간: ${(httpReqDuration['p(99)'] || 0).toFixed(2)}ms</div>
    <div class="metric">에러율: ${((errors.rate || 0) * 100).toFixed(2)}%</div>
    <div class="metric">TPS: ${(httpReqs.rate || 0).toFixed(2)}</div>
</body>
</html>
  `;
}
