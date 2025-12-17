/**
 * Phase 3: 일반 동시성
 * - 데이터: 1,000건
 * - VU: 10 (동시 사용자 10명)
 * - 시간: 10분
 * - 목적: 일반적인 다중 사용자 시나리오
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

// 테스트 설정
export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    normal_concurrency: {
      executor: 'constant-vus',
      vus: 10,              // 동시 사용자 10명
      duration: '10m',      // 10분간 지속
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<30000'], // 95%의 요청이 30초 이내
    http_req_failed: ['rate<0.1'],       // 실패율 10% 미만
    errors: ['rate<0.1'],
  },
};

// 환경 변수
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUCTS_PER_REQUEST = parseInt(__ENV.PRODUCTS_PER_REQUEST || '1000');
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
  return `CONC-${timestamp}-${vuNumber}-${iterNumber}-${index}-${randomPart}`;
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
  };

  concurrentRequests.add(1);

  console.log(`[VU ${__VU}] [Iteration ${__ITER + 1}] ${PRODUCTS_PER_REQUEST}개 상품 등록 시작...`);

  const startTime = Date.now();
  const response = http.post(`${BASE_URL}/api/v1/products/bulk?ownerId=${OWNER_ID}`, payload, params);
  const duration = Date.now() - startTime;

  const success = check(response, {
    'status is 200': (r) => r.status === 200,
    'response has body': (r) => r.body && r.body.length > 0,
  });

  errorRate.add(!success);
  registrationDuration.add(duration);

  if (response.status === 200 && response.body) {
    try {
      const result = JSON.parse(response.body);
      const data = result.data;

      successfulProducts.add(data.successCount);
      failedProducts.add(data.failureCount);

      console.log(`[VU ${__VU}] ✓ 완료: ${(duration / 1000).toFixed(2)}초 | 성공: ${data.successCount}/${PRODUCTS_PER_REQUEST}`);

      if (data.failedProducts && data.failedProducts.length > 0 && Math.random() < 0.1) {
        // 10% 확률로 실패 샘플 출력 (로그 과다 방지)
        console.log(`[VU ${__VU}] 실패 샘플: ${data.failedProducts.slice(0, 2).map(f => f.message).join(', ')}`);
      }
    } catch (e) {
      console.error(`[VU ${__VU}] ✗ 응답 파싱 실패: ${e.message}`);
      errorRate.add(1);
    }
  } else {
    console.error(`[VU ${__VU}] ✗ 요청 실패 (status: ${response.status})`);
  }

  // 요청 사이 1~3초 랜덤 대기 (실제 사용자 행동 시뮬레이션)
  sleep(Math.random() * 2 + 1);
}

export function handleSummary(data) {
  const metrics = extractMetrics(data);
  logMetrics('Phase 3: 일반 동시성 테스트 결과', metrics);

  const totalRequests = ((data.metrics.concurrent_requests && data.metrics.concurrent_requests.values) || {}).count || 0;
  const successCount = ((data.metrics.successful_products && data.metrics.successful_products.values) || {}).count || 0;
  const failureCount = ((data.metrics.failed_products && data.metrics.failed_products.values) || {}).count || 0;
  const avgRegTime = (((data.metrics.registration_duration && data.metrics.registration_duration.values) || {}).avg || 0) / 1000;

  console.log('[동시성 통계]');
  console.log('  총 요청 수: ' + totalRequests);
  console.log('  성공한 상품 수: ' + successCount.toLocaleString());
  console.log('  실패한 상품 수: ' + failureCount.toLocaleString());
  console.log('  평균 등록 시간: ' + avgRegTime.toFixed(2) + '초');
  console.log('  전체 처리량: ' + (successCount / (600)).toFixed(2) + ' 상품/초'); // 10분 = 600초

  return {
    'k6-tests/results/registration/phase3-normal-concurrency-summary.json': JSON.stringify(data, null, 2),
    'k6-tests/results/registration/phase3-normal-concurrency-summary.html': htmlReport(data, metrics),
  };
}

function htmlReport(data, metrics) {
  const totalRequests = ((data.metrics.concurrent_requests && data.metrics.concurrent_requests.values) || {}).count || 0;
  const successCount = ((data.metrics.successful_products && data.metrics.successful_products.values) || {}).count || 0;
  const failureCount = ((data.metrics.failed_products && data.metrics.failed_products.values) || {}).count || 0;
  const avgRegTime = (((data.metrics.registration_duration && data.metrics.registration_duration.values) || {}).avg || 0) / 1000;
  const totalThroughput = (successCount / 600).toFixed(2); // 10분 = 600초

  return `
<!DOCTYPE html>
<html>
<head>
    <title>Phase 3: 일반 동시성 테스트 결과</title>
    <style>${getCommonStyles('#9C27B0')}</style>
</head>
<body>
    <div class="container">
        <h1>Phase 3: 일반 동시성 테스트</h1>
        <p>데이터: 1,000건 | VU: 10 | 시간: 10분 | 목적: 일반적인 다중 사용자 시나리오</p>

        <h2>전체 성능 메트릭</h2>
        <div class="metric-grid">${generateMetricCards(metrics)}</div>

        <h2>동시성 통계</h2>
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
                <div class="metric-value ${failureCount > successCount * 0.1 ? 'bad' : 'good'}">${failureCount.toLocaleString()}</div>
            </div>
            <div class="metric-card">
                <div class="metric-label">평균 등록 시간</div>
                <div class="metric-value">${avgRegTime.toFixed(2)} <span class="metric-unit">초</span></div>
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
    </div>
</body>
</html>
  `;
}
