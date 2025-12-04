/**
 * Step 4: 재고 소진 테스트
 * - 상품 ID=1에 초고강도 부하를 가해 재고 소진
 * - 재고 부족 에러 처리 검증
 * - 목표: 재고 소진 시나리오 및 에러 처리 로직 검증
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// 커스텀 메트릭
const errorRate = new Rate('errors');
const purchaseDuration = new Trend('purchase_duration');
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
  const response = http.post(`${BASE_URL}/api/v1/products/purchase`, payload, params);
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
  console.log('\n=== Step 4: 재고 소진 시나리오 테스트 결과 ===\n');

  const metrics = data.metrics;
  const httpReqs = metrics.http_reqs && metrics.http_reqs.values ? metrics.http_reqs.values : {};
  const httpReqDuration = metrics.http_req_duration && metrics.http_req_duration.values ? metrics.http_req_duration.values : {};
  const errors = metrics.errors && metrics.errors.values ? metrics.errors.values : {};
  const successCnt = metrics.success_count && metrics.success_count.values ? metrics.success_count.values : {};
  const stockErrorCnt = metrics.stock_error_count && metrics.stock_error_count.values ? metrics.stock_error_count.values : {};
  const otherErrorCnt = metrics.other_error_count && metrics.other_error_count.values ? metrics.other_error_count.values : {};

  console.log('[전체 성능 메트릭]');
  console.log('  총 요청 수: ' + (httpReqs.count || 0));
  console.log('  평균 응답 시간: ' + (httpReqDuration.avg || 0).toFixed(2) + 'ms');
  console.log('  P95 응답 시간: ' + (httpReqDuration['p(95)'] || 0).toFixed(2) + 'ms');
  console.log('  P99 응답 시간: ' + (httpReqDuration['p(99)'] || 0).toFixed(2) + 'ms');
  console.log('  최대 응답 시간: ' + (httpReqDuration.max || 0).toFixed(2) + 'ms');
  console.log('  에러율: ' + ((errors.rate || 0) * 100).toFixed(2) + '%');
  console.log('  초당 요청 수(TPS): ' + (httpReqs.rate || 0).toFixed(2));

  console.log('\n[재고 소진 분석]');
  console.log('  성공한 구매: ' + (successCnt.count || 0) + '건');
  console.log('  재고 부족 에러: ' + (stockErrorCnt.count || 0) + '건');
  console.log('  기타 에러: ' + (otherErrorCnt.count || 0) + '건');

  const totalRequests = (successCnt.count || 0) + (stockErrorCnt.count || 0) + (otherErrorCnt.count || 0);
  if (totalRequests > 0) {
    console.log('  성공률: ' + ((successCnt.count || 0) / totalRequests * 100).toFixed(2) + '%');
    console.log('  재고 부족 에러율: ' + ((stockErrorCnt.count || 0) / totalRequests * 100).toFixed(2) + '%\n');
  }

  return {
    'k6-tests/results/step4-stock-depletion-summary.json': JSON.stringify(data, null, 2),
    'k6-tests/results/step4-stock-depletion-summary.html': htmlReport(data),
  };
}

function htmlReport(data) {
  const metrics = data.metrics;
  const httpReqs = metrics.http_reqs && metrics.http_reqs.values ? metrics.http_reqs.values : {};
  const httpReqDuration = metrics.http_req_duration && metrics.http_req_duration.values ? metrics.http_req_duration.values : {};
  const errors = metrics.errors && metrics.errors.values ? metrics.errors.values : {};
  const successCnt = metrics.success_count && metrics.success_count.values ? metrics.success_count.values : {};
  const stockErrorCnt = metrics.stock_error_count && metrics.stock_error_count.values ? metrics.stock_error_count.values : {};
  const otherErrorCnt = metrics.other_error_count && metrics.other_error_count.values ? metrics.other_error_count.values : {};

  const totalRequests = (successCnt.count || 0) + (stockErrorCnt.count || 0) + (otherErrorCnt.count || 0);
  const successRate = totalRequests > 0 ? ((successCnt.count || 0) / totalRequests * 100) : 0;
  const stockErrorRate = totalRequests > 0 ? ((stockErrorCnt.count || 0) / totalRequests * 100) : 0;

  return `
<!DOCTYPE html>
<html>
<head>
    <title>Step 4: 재고 소진 시나리오 테스트 결과</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }
        .container { max-width: 1200px; margin: 0 auto; background-color: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
        h1 { color: #333; border-bottom: 3px solid #F44336; padding-bottom: 10px; }
        h2 { color: #555; margin-top: 30px; }
        .metric-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 20px; margin: 20px 0; }
        .metric-card { background: #f9f9f9; padding: 20px; border-radius: 5px; border-left: 4px solid #F44336; }
        .metric-label { font-size: 14px; color: #666; margin-bottom: 5px; }
        .metric-value { font-size: 24px; font-weight: bold; color: #333; }
        .metric-unit { font-size: 16px; color: #888; }
        .good { color: #4CAF50; }
        .warning { color: #FF9800; }
        .bad { color: #F44336; }
        .info-box { background: #FFEBEE; padding: 15px; border-radius: 5px; margin: 20px 0; border-left: 4px solid #F44336; }
        table { border-collapse: collapse; width: 100%; margin: 20px 0; }
        th, td { border: 1px solid #ddd; padding: 12px; text-align: left; }
        th { background-color: #F44336; color: white; }
        tr:nth-child(even) { background-color: #f9f9f9; }
        .chart-container { margin: 20px 0; }
        .bar { height: 30px; background: linear-gradient(90deg, #FF5722, #FF7043); border-radius: 3px; margin: 10px 0; position: relative; }
        .bar-label { position: absolute; left: 10px; top: 50%; transform: translateY(-50%); color: white; font-weight: bold; }
    </style>
</head>
<body>
    <div class="container">
        <h1>Step 4: 재고 소진 시나리오 테스트 결과</h1>
        <p>제한된 재고(100,000개)에 대한 집중 공격으로 재고 소진 및 에러 처리 검증</p>

        <div class="info-box">
            <strong>⚠️ 테스트 특징:</strong> 상품 ID=1에만 모든 요청을 집중시켜 의도적으로 재고를 소진시킵니다.
            재고 부족 에러가 발생하는 것이 정상이며, 에러 처리 로직과 Lock 경합 상황을 테스트합니다.
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
        </div>

        <h2>재고 소진 분석</h2>
        <div class="metric-grid">
            <div class="metric-card">
                <div class="metric-label">성공한 구매</div>
                <div class="metric-value good">${successCnt.count || 0} <span class="metric-unit">건</span></div>
            </div>
            <div class="metric-card">
                <div class="metric-label">재고 부족 에러</div>
                <div class="metric-value warning">${stockErrorCnt.count || 0} <span class="metric-unit">건</span></div>
            </div>
            <div class="metric-card">
                <div class="metric-label">기타 에러</div>
                <div class="metric-value ${(otherErrorCnt.count || 0) > 0 ? 'bad' : ''}">${otherErrorCnt.count || 0} <span class="metric-unit">건</span></div>
            </div>
            <div class="metric-card">
                <div class="metric-label">전체 에러율</div>
                <div class="metric-value ${((errors.rate || 0) * 100) > 50 ? 'warning' : 'good'}">${((errors.rate || 0) * 100).toFixed(2)} <span class="metric-unit">%</span></div>
            </div>
        </div>

        <h2>성공/실패 비율</h2>
        <div class="chart-container">
            <div>성공: ${successRate.toFixed(2)}%</div>
            <div class="bar" style="width: ${successRate > 0 ? successRate : 1}%; background: linear-gradient(90deg, #4CAF50, #66BB6A);">
                <span class="bar-label">${(successCnt.count || 0).toLocaleString()}건</span>
            </div>

            <div style="margin-top: 20px;">재고 부족 에러: ${stockErrorRate.toFixed(2)}%</div>
            <div class="bar" style="width: ${stockErrorRate > 0 ? stockErrorRate : 1}%; background: linear-gradient(90deg, #FF9800, #FFB74D);">
                <span class="bar-label">${(stockErrorCnt.count || 0).toLocaleString()}건</span>
            </div>
        </div>

        <h2>부하 단계</h2>
        <table>
            <tr>
                <th>단계</th>
                <th>지속 시간</th>
                <th>VU 수</th>
                <th>설명</th>
            </tr>
            <tr>
                <td>Ramp-up</td>
                <td>30초</td>
                <td>0 → 500</td>
                <td>빠른 증가</td>
            </tr>
            <tr>
                <td>High Load</td>
                <td>2분</td>
                <td>500 → 1000</td>
                <td>고강도 부하 - 재고 소진 시작</td>
            </tr>
            <tr>
                <td>Max Load</td>
                <td>1분</td>
                <td>1000 → 1500</td>
                <td>최대 부하 - 재고 소진 가속</td>
            </tr>
            <tr>
                <td>Sustained Max</td>
                <td>3분</td>
                <td>1500</td>
                <td>재고 소진 후 에러 처리 테스트</td>
            </tr>
            <tr>
                <td>Cool-down</td>
                <td>30초</td>
                <td>1500 → 0</td>
                <td>종료</td>
            </tr>
        </table>

        <h2>검증 항목</h2>
        <table>
            <tr>
                <th>항목</th>
                <th>기대값</th>
                <th>실제값</th>
                <th>결과</th>
            </tr>
            <tr>
                <td>성공한 구매 건수</td>
                <td>~100,000건</td>
                <td>${(successCnt.count || 0).toLocaleString()}건</td>
                <td class="${Math.abs((successCnt.count || 0) - 100000) < 10000 ? 'good' : 'warning'}">
                    ${Math.abs((successCnt.count || 0) - 100000) < 10000 ? '✓ 정상' : '⚠ 확인 필요'}
                </td>
            </tr>
            <tr>
                <td>재고 부족 에러 발생</td>
                <td>높은 에러율 (90%+)</td>
                <td>${stockErrorRate.toFixed(2)}%</td>
                <td class="${stockErrorRate > 50 ? 'good' : 'warning'}">
                    ${stockErrorRate > 50 ? '✓ 정상' : '⚠ 확인 필요'}
                </td>
            </tr>
            <tr>
                <td>응답 시간 (P95)</td>
                <td>< 10초</td>
                <td>${(httpReqDuration['p(95)'] || 0).toFixed(2)}ms</td>
                <td class="${(httpReqDuration['p(95)'] || 0) < 10000 ? 'good' : 'bad'}">
                    ${(httpReqDuration['p(95)'] || 0) < 10000 ? '✓ 정상' : '✗ 임계값 초과'}
                </td>
            </tr>
        </table>
    </div>
</body>
</html>
  `;
}
