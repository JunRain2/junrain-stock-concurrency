/**
 * Phase 4: 높은 동시성
 * - 데이터: 5,000건
 * - VU: 50 (동시 사용자 50명)
 * - 시간: 5분
 * - 목적: 높은 부하 상황에서의 시스템 성능 및 안정성 검증
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { errorRate, extractMetrics, logMetrics, getCommonStyles, generateMetricCards } from '../common/common.js';

// 커스텀 메트릭
const successfulProducts = new Counter('successful_products');
const failedProducts = new Counter('failed_products');
const registrationDuration = new Trend('registration_duration');
const concurrentRequests = new Counter('concurrent_requests');
const timeoutErrors = new Counter('timeout_errors');

// 테스트 설정
export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    high_concurrency: {
      executor: 'constant-vus',
      vus: 50,              // 동시 사용자 50명
      duration: '5m',       // 5분간 지속
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<60000'], // 95%의 요청이 60초 이내
    http_req_failed: ['rate<0.15'],      // 실패율 15% 미만 (높은 부하 고려)
    errors: ['rate<0.15'],
  },
};

// 환경 변수
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUCTS_PER_REQUEST = parseInt(__ENV.PRODUCTS_PER_REQUEST || '5000');
const OWNER_ID = parseInt(__ENV.OWNER_ID || '1');

// 한글 상품명 생성용 문자 배열
const koreanChars = '가나다라마바사아자차카타파하거너더러머버서어저처커터퍼허고노도로모보소오조초코토포호구누두루무부수우주추쿠투푸후';
const numbers = '0123456789';
const englishChars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz';

function generateProductName() {
  const nameLength = Math.floor(Math.random() * 15) + 5;
  let name = '';

  for (let i = 0; i < nameLength; i++) {
    const charType = Math.random();
    if (charType < 0.6) {
      name += koreanChars.charAt(Math.floor(Math.random() * koreanChars.length));
    } else if (charType < 0.9) {
      name += englishChars.charAt(Math.floor(Math.random() * englishChars.length));
    } else {
      name += numbers.charAt(Math.floor(Math.random() * numbers.length));
    }
  }

  return name;
}

function generateProductCode(index) {
  const timestamp = Date.now();
  const vuNumber = __VU;
  const iterNumber = __ITER;
  const randomPart = Math.random().toString(36).substring(2, 8).toUpperCase();
  return `HIGH-${timestamp}-${vuNumber}-${iterNumber}-${index}-${randomPart}`;
}

function generateValidProduct(index) {
  return {
    name: generateProductName(),
    price: Math.floor(Math.random() * 1000000) + 1000,
    stock: Math.floor(Math.random() * 1000) + 1,
    code: generateProductCode(index),
  };
}

function generateInvalidProduct(index, errorType) {
  const baseProduct = generateValidProduct(index);

  switch (errorType) {
    case 'long_name':
      baseProduct.name = '가'.repeat(25);
      break;
    case 'special_char':
      baseProduct.name = '테스트상품@#$';
      break;
    case 'negative_stock':
      baseProduct.stock = -10;
      break;
  }

  return baseProduct;
}

function generateProducts(count) {
  const products = [];
  const invalidRatio = 0.05;
  const invalidTypes = ['long_name', 'special_char', 'negative_stock'];

  for (let i = 0; i < count; i++) {
    if (Math.random() < invalidRatio) {
      const errorType = invalidTypes[Math.floor(Math.random() * invalidTypes.length)];
      products.push(generateInvalidProduct(i, errorType));
    } else {
      products.push(generateValidProduct(i));
    }
  }

  return products;
}

export default function () {
  const products = generateProducts(PRODUCTS_PER_REQUEST);

  const payload = JSON.stringify({
    products: products,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
    tags: { name: 'BulkProductRegistration' },
    timeout: '120s', // 타임아웃 120초 (높은 부하 고려)
  };

  concurrentRequests.add(1);

  const logPrefix = `[VU ${__VU.toString().padStart(2, '0')}][Iter ${(__ITER + 1).toString().padStart(2, '0')}]`;
  console.log(`${logPrefix} ${PRODUCTS_PER_REQUEST}개 상품 등록 시작...`);

  const startTime = Date.now();
  const response = http.post(`${BASE_URL}/api/v1/products/bulk?ownerId=${OWNER_ID}`, payload, params);
  const duration = Date.now() - startTime;

  const success = check(response, {
    'status is 200': (r) => r.status === 200,
    'response has body': (r) => r.body && r.body.length > 0,
    'no timeout': (r) => r.status !== 0,
  });

  errorRate.add(!success);
  registrationDuration.add(duration);

  if (response.status === 0) {
    timeoutErrors.add(1);
    console.error(`${logPrefix} ✗ 타임아웃 발생 (${(duration / 1000).toFixed(2)}초)`);
  } else if (response.status === 200 && response.body) {
    try {
      const result = JSON.parse(response.body);
      const data = result.data;

      successfulProducts.add(data.successCount);
      failedProducts.add(data.failureCount);

      const throughput = (data.successCount / (duration / 1000)).toFixed(2);
      console.log(`${logPrefix} ✓ 완료: ${(duration / 1000).toFixed(2)}초 | 성공: ${data.successCount}/${PRODUCTS_PER_REQUEST} (${throughput} 상품/초)`);

      // 5% 확률로 실패 샘플 출력 (로그 과다 방지)
      if (data.failedProducts && data.failedProducts.length > 0 && Math.random() < 0.05) {
        console.log(`${logPrefix} 실패 샘플: ${data.failedProducts.slice(0, 2).map(f => f.message).join(', ')}`);
      }
    } catch (e) {
      console.error(`${logPrefix} ✗ 응답 파싱 실패: ${e.message}`);
      errorRate.add(1);
    }
  } else {
    console.error(`${logPrefix} ✗ 요청 실패 (status: ${response.status})`);
    if (response.body && response.body.length < 500) {
      console.error(`${logPrefix} Error: ${response.body}`);
    }
  }

  // 요청 사이 1~4초 랜덤 대기
  sleep(Math.random() * 3 + 1);
}

export function handleSummary(data) {
  const metrics = extractMetrics(data);
  logMetrics('Phase 4: 높은 동시성 테스트 결과', metrics);

  const totalRequests = (data.metrics.concurrent_requests && data.metrics.concurrent_requests.values).count || 0;
  const successCount = (data.metrics.successful_products && data.metrics.successful_products.values).count || 0;
  const failureCount = (data.metrics.failed_products && data.metrics.failed_products.values).count || 0;
  const timeoutCount = (data.metrics.timeout_errors && data.metrics.timeout_errors.values).count || 0;
  const avgRegTime = ((data.metrics.registration_duration && data.metrics.registration_duration.values).avg || 0) / 1000;

  console.log('[높은 동시성 통계]');
  console.log('  총 요청 수: ' + totalRequests);
  console.log('  성공한 상품 수: ' + successCount.toLocaleString());
  console.log('  실패한 상품 수: ' + failureCount.toLocaleString());
  console.log('  타임아웃 발생 수: ' + timeoutCount);
  console.log('  평균 등록 시간: ' + avgRegTime.toFixed(2) + '초');
  console.log('  전체 처리량: ' + (successCount / 300).toFixed(2) + ' 상품/초'); // 5분 = 300초

  return {
    'k6-tests/results/registration/phase4-high-concurrency-summary.json': JSON.stringify(data, null, 2),
    'k6-tests/results/registration/phase4-high-concurrency-summary.html': htmlReport(data, metrics),
  };
}

function htmlReport(data, metrics) {
  const totalRequests = ((data.metrics.concurrent_requests && data.metrics.concurrent_requests.values) || {}).count || 0;
  const successCount = ((data.metrics.successful_products && data.metrics.successful_products.values) || {}).count || 0;
  const failureCount = ((data.metrics.failed_products && data.metrics.failed_products.values) || {}).count || 0;
  const timeoutCount = ((data.metrics.timeout_errors && data.metrics.timeout_errors.values) || {}).count || 0;
  const avgRegTime = (((data.metrics.registration_duration && data.metrics.registration_duration.values) || {}).avg || 0) / 1000;
  const p95RegTime = (((data.metrics.registration_duration && data.metrics.registration_duration.values) || {})['p(95)'] || 0) / 1000;
  const p99RegTime = (((data.metrics.registration_duration && data.metrics.registration_duration.values) || {})['p(99)'] || 0) / 1000;
  const totalThroughput = (successCount / 300).toFixed(2); // 5분 = 300초

  return `
<!DOCTYPE html>
<html>
<head>
    <title>Phase 4: 높은 동시성 테스트 결과</title>
    <style>${getCommonStyles('#F44336')}</style>
</head>
<body>
    <div class="container">
        <h1>Phase 4: 높은 동시성 테스트</h1>
        <p>데이터: 5,000건 | VU: 50 | 시간: 5분 | 목적: 높은 부하 상황 성능 검증</p>

        <h2>전체 성능 메트릭</h2>
        <div class="metric-grid">${generateMetricCards(metrics)}</div>

        <h2>높은 동시성 통계</h2>
        <div class="metric-grid">
            <div class="metric-card">
                <div class="metric-label">총 요청 수</div>
                <div class="metric-value">${totalRequests.toLocaleString()}</div>
            </div>
            <div class="metric-card">
                <div class="metric-label">성공한 상품 수</div>
                <div class="metric-value good">${successCount.toLocaleString()}</div>
            </div>
            <div class="metric-card">
                <div class="metric-label">실패한 상품 수</div>
                <div class="metric-value ${failureCount > successCount * 0.15 ? 'bad' : failureCount > successCount * 0.05 ? 'warning' : 'good'}">${failureCount.toLocaleString()}</div>
            </div>
            <div class="metric-card">
                <div class="metric-label">타임아웃 발생</div>
                <div class="metric-value ${timeoutCount > 0 ? 'warning' : 'good'}">${timeoutCount.toLocaleString()}</div>
            </div>
            <div class="metric-card">
                <div class="metric-label">평균 등록 시간</div>
                <div class="metric-value">${avgRegTime.toFixed(2)} <span class="metric-unit">초</span></div>
            </div>
            <div class="metric-card">
                <div class="metric-label">P95 등록 시간</div>
                <div class="metric-value ${p95RegTime > 60 ? 'bad' : p95RegTime > 30 ? 'warning' : 'good'}">${p95RegTime.toFixed(2)} <span class="metric-unit">초</span></div>
            </div>
            <div class="metric-card">
                <div class="metric-label">P99 등록 시간</div>
                <div class="metric-value ${p99RegTime > 90 ? 'bad' : p99RegTime > 60 ? 'warning' : 'good'}">${p99RegTime.toFixed(2)} <span class="metric-unit">초</span></div>
            </div>
            <div class="metric-card">
                <div class="metric-label">전체 처리량</div>
                <div class="metric-value">${totalThroughput} <span class="metric-unit">상품/초</span></div>
            </div>
            <div class="metric-card">
                <div class="metric-label">요청당 처리량</div>
                <div class="metric-value">${(PRODUCTS_PER_REQUEST / avgRegTime).toFixed(2)} <span class="metric-unit">상품/초</span></div>
            </div>
        </div>

        <h2>성능 분석</h2>
        <table>
            <thead>
                <tr>
                    <th>항목</th>
                    <th>값</th>
                    <th>평가</th>
                </tr>
            </thead>
            <tbody>
                <tr>
                    <td>동시 사용자 처리</td>
                    <td>50 VUs</td>
                    <td class="${timeoutCount === 0 && failureCount / (successCount + failureCount) < 0.1 ? 'good' : 'warning'}">
                        ${timeoutCount === 0 && failureCount / (successCount + failureCount) < 0.1 ? '안정적' : '개선 필요'}
                    </td>
                </tr>
                <tr>
                    <td>응답 시간 안정성</td>
                    <td>P95: ${p95RegTime.toFixed(2)}초</td>
                    <td class="${p95RegTime < 30 ? 'good' : p95RegTime < 60 ? 'warning' : 'bad'}">
                        ${p95RegTime < 30 ? '우수' : p95RegTime < 60 ? '보통' : '개선 필요'}
                    </td>
                </tr>
                <tr>
                    <td>에러 처리</td>
                    <td>${((failureCount / (successCount + failureCount)) * 100).toFixed(2)}%</td>
                    <td class="${failureCount / (successCount + failureCount) < 0.05 ? 'good' : failureCount / (successCount + failureCount) < 0.15 ? 'warning' : 'bad'}">
                        ${failureCount / (successCount + failureCount) < 0.05 ? '우수' : failureCount / (successCount + failureCount) < 0.15 ? '보통' : '개선 필요'}
                    </td>
                </tr>
            </tbody>
        </table>
    </div>
</body>
</html>
  `;
}
