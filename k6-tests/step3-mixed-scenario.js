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
        body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }
        .container { max-width: 1200px; margin: 0 auto; background-color: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
        h1 { color: #333; border-bottom: 3px solid #FF9800; padding-bottom: 10px; }
        h2 { color: #555; margin-top: 30px; }
        .metric-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 20px; margin: 20px 0; }
        .metric-card { background: #f9f9f9; padding: 20px; border-radius: 5px; border-left: 4px solid #FF9800; }
        .metric-label { font-size: 14px; color: #666; margin-bottom: 5px; }
        .metric-value { font-size: 24px; font-weight: bold; color: #333; }
        .metric-unit { font-size: 16px; color: #888; }
        .good { color: #4CAF50; }
        .warning { color: #FF9800; }
        .bad { color: #F44336; }
        .info-box { background: #FFF3E0; padding: 15px; border-radius: 5px; margin: 20px 0; border-left: 4px solid #FF9800; }
        table { border-collapse: collapse; width: 100%; margin: 20px 0; }
        th, td { border: 1px solid #ddd; padding: 12px; text-align: left; }
        th { background-color: #FF9800; color: white; }
        tr:nth-child(even) { background-color: #f9f9f9; }
    </style>
</head>
<body>
    <div class="container">
        <h1>Step 3: 혼합 시나리오 테스트 결과</h1>
        <p>실제 운영 환경을 시뮬레이션한 복합 시나리오 테스트</p>

        <div class="info-box">
            <strong>테스트 특징:</strong> Hot Item(20%)과 일반 트래픽(80%) 혼합, 장바구니 기능(30% 확률), 트래픽 스파이크 포함
        </div>

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
                <div class="metric-value ${(httpReqDuration['p(95)'] || 0) > 4000 ? 'bad' : (httpReqDuration['p(95)'] || 0) > 2000 ? 'warning' : 'good'}">${(httpReqDuration['p(95)'] || 0).toFixed(2)} <span class="metric-unit">ms</span></div>
            </div>
            <div class="metric-card">
                <div class="metric-label">P99 응답 시간</div>
                <div class="metric-value ${(httpReqDuration['p(99)'] || 0) > 8000 ? 'bad' : (httpReqDuration['p(99)'] || 0) > 4000 ? 'warning' : 'good'}">${(httpReqDuration['p(99)'] || 0).toFixed(2)} <span class="metric-unit">ms</span></div>
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

        <h2>시나리오 구성</h2>
        <table>
            <tr>
                <th>시나리오</th>
                <th>VU 비율</th>
                <th>특징</th>
            </tr>
            <tr>
                <td>일반 트래픽</td>
                <td>80%</td>
                <td>다양한 상품 구매, 장바구니 기능 사용</td>
            </tr>
            <tr>
                <td>Hot Item 집중</td>
                <td>20%</td>
                <td>인기 상품(ID=1)에 집중</td>
            </tr>
            <tr>
                <td>트래픽 스파이크</td>
                <td>10분 시점</td>
                <td>갑작스런 부하 증가 (200 VU)</td>
            </tr>
        </table>

        <h2>부하 단계</h2>
        <table>
            <tr>
                <th>단계</th>
                <th>지속 시간</th>
                <th>VU 수</th>
                <th>설명</th>
            </tr>
            <tr>
                <td>Warm-up</td>
                <td>2분</td>
                <td>0 → 80</td>
                <td>시스템 준비</td>
            </tr>
            <tr>
                <td>Steady State</td>
                <td>5분</td>
                <td>80</td>
                <td>정상 부하</td>
            </tr>
            <tr>
                <td>Spike</td>
                <td>3분</td>
                <td>200</td>
                <td>트래픽 급증</td>
            </tr>
            <tr>
                <td>Recovery</td>
                <td>3분</td>
                <td>80</td>
                <td>정상 복귀</td>
            </tr>
            <tr>
                <td>Cool-down</td>
                <td>2분</td>
                <td>80 → 0</td>
                <td>종료</td>
            </tr>
        </table>
    </div>
</body>
</html>
  `;
}
