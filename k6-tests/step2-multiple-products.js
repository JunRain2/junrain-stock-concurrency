/**
 * Step 2: 다중 상품 (경합 분산)
 * - 요청마다 다른 상품을 구매 (ID=1~10 랜덤)
 * - Lock 경합이 분산됨
 * - ORDER BY로 인한 데드락 방지 효과 측정
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// 커스텀 메트릭
const errorRate = new Rate('errors');
const purchaseDuration = new Trend('purchase_duration');
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
  console.log('\n=== Step 2: 다중 상품 분산 테스트 결과 ===\n');

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
    'k6-tests/results/step2-multiple-products-summary.json': JSON.stringify(data, null, 2),
    'k6-tests/results/step2-multiple-products-summary.html': htmlReport(data),
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
    <title>Step 2: 다중 상품 분산 테스트 결과</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }
        .container { max-width: 1200px; margin: 0 auto; background-color: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
        h1 { color: #333; border-bottom: 3px solid #2196F3; padding-bottom: 10px; }
        h2 { color: #555; margin-top: 30px; }
        .metric-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 20px; margin: 20px 0; }
        .metric-card { background: #f9f9f9; padding: 20px; border-radius: 5px; border-left: 4px solid #2196F3; }
        .metric-label { font-size: 14px; color: #666; margin-bottom: 5px; }
        .metric-value { font-size: 24px; font-weight: bold; color: #333; }
        .metric-unit { font-size: 16px; color: #888; }
        .good { color: #4CAF50; }
        .warning { color: #FF9800; }
        .bad { color: #F44336; }
        table { border-collapse: collapse; width: 100%; margin: 20px 0; }
        th, td { border: 1px solid #ddd; padding: 12px; text-align: left; }
        th { background-color: #2196F3; color: white; }
        tr:nth-child(even) { background-color: #f9f9f9; }
        .info-box { background: #E3F2FD; padding: 15px; border-radius: 5px; margin: 20px 0; border-left: 4px solid #2196F3; }
    </style>
</head>
<body>
    <div class="container">
        <h1>Step 2: 다중 상품 분산 테스트 결과</h1>
        <p>10개 상품에 대한 랜덤 분산 요청으로 Lock 경합 최소화 테스트</p>

        <div class="info-box">
            <strong>테스트 특징:</strong> 요청마다 ID=1~10 중 랜덤 상품을 선택하여 구매. Lock 경합이 분산되어 Step 1보다 높은 처리량과 낮은 응답 시간이 예상됩니다.
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
                <div class="metric-value ${(httpReqDuration['p(95)'] || 0) > 3000 ? 'bad' : (httpReqDuration['p(95)'] || 0) > 1500 ? 'warning' : 'good'}">${(httpReqDuration['p(95)'] || 0).toFixed(2)} <span class="metric-unit">ms</span></div>
            </div>
            <div class="metric-card">
                <div class="metric-label">P99 응답 시간</div>
                <div class="metric-value ${(httpReqDuration['p(99)'] || 0) > 5000 ? 'bad' : (httpReqDuration['p(99)'] || 0) > 3000 ? 'warning' : 'good'}">${(httpReqDuration['p(99)'] || 0).toFixed(2)} <span class="metric-unit">ms</span></div>
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
                <td>Low Load</td>
                <td>20</td>
                <td>30초</td>
                <td>낮은 부하 상태</td>
            </tr>
            <tr>
                <td>Medium Load</td>
                <td>100</td>
                <td>30초</td>
                <td>중간 부하 상태</td>
            </tr>
            <tr>
                <td>High Load</td>
                <td>200</td>
                <td>30초</td>
                <td>높은 부하 상태</td>
            </tr>
            <tr>
                <td>Very High Load</td>
                <td>500</td>
                <td>30초</td>
                <td>매우 높은 부하 - 병렬 처리 한계 테스트</td>
            </tr>
        </table>
    </div>
</body>
</html>
  `;
}
