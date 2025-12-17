/**
 * Phase 1: 기본 성능 측정 (Baseline)
 * - 데이터: 1,000건
 * - VU: 1 (단일 사용자)
 * - 반복: 10회
 * - 목적: 기준 성능 파악 (단일 스레드 처리 속도)
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { errorRate, extractMetrics, logMetrics, getCommonStyles, generateMetricCards } from '../common/common.js';

// 커스텀 메트릭
const successfulProducts = new Counter('successful_products');
const failedProducts = new Counter('failed_products');
const registrationDuration = new Trend('registration_duration');

// 테스트 설정
export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    baseline: {
      executor: 'per-vu-iterations',
      vus: 1,           // 단일 사용자
      iterations: 10,   // 10회 반복
      maxDuration: '30m',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<30000'], // 95%의 요청이 30초 이내
    errors: ['rate<0.1'],                // 에러율 10% 미만
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

// 랜덤 한글 상품명 생성 (5~19자)
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

// 유니크한 상품 코드 생성
function generateProductCode(index) {
  const timestamp = Date.now();
  const vuNumber = __VU;
  const iterNumber = __ITER;
  const randomPart = Math.random().toString(36).substring(2, 8).toUpperCase();
  return `BULK-${timestamp}-${vuNumber}-${iterNumber}-${index}-${randomPart}`;
}

// 유효한 상품 데이터 생성
function generateValidProduct(index) {
  return {
    name: generateProductName(),
    price: Math.floor(Math.random() * 1000000) + 1000, // 1,000원 ~ 1,000,000원
    stock: Math.floor(Math.random() * 1000) + 1,       // 1 ~ 1,000개
    code: generateProductCode(index),
  };
}

// 일부 무효한 상품 생성 (비즈니스 제약 위반)
function generateInvalidProduct(index, errorType) {
  const baseProduct = generateValidProduct(index);

  switch (errorType) {
    case 'long_name':
      baseProduct.name = '가'.repeat(25); // 20자 초과
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

// 상품 배열 생성 (5%는 무효 데이터)
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

  console.log(`\n[VU ${__VU}, Iteration ${__ITER + 1}/10] ${PRODUCTS_PER_REQUEST}개 상품 등록 시작...`);

  const startTime = Date.now();
  const response = http.post(`${BASE_URL}/api/v1/products/bulk?ownerId=${OWNER_ID}`, payload, params);
  const duration = Date.now() - startTime;

  // 응답 검증
  const success = check(response, {
    'status is 200': (r) => r.status === 200,
    'response has body': (r) => r.body && r.body.length > 0,
  });

  errorRate.add(!success);
  registrationDuration.add(duration);

  // 응답 파싱
  if (response.status === 200 && response.body) {
    try {
      const result = JSON.parse(response.body);
      const data = result.data;

      successfulProducts.add(data.successCount);
      failedProducts.add(data.failureCount);

      console.log(`  ✓ 완료: ${(duration / 1000).toFixed(2)}초`);
      console.log(`  - 성공: ${data.successCount}/${PRODUCTS_PER_REQUEST}`);
      console.log(`  - 실패: ${data.failureCount}/${PRODUCTS_PER_REQUEST}`);
      console.log(`  - 성공률: ${(data.successCount / PRODUCTS_PER_REQUEST * 100).toFixed(2)}%`);

      // 실패 샘플 출력
      if (data.failedProducts && data.failedProducts.length > 0) {
        console.log(`  - 실패 샘플 (최대 3개):`);
        data.failedProducts.slice(0, 3).forEach((failed, idx) => {
          console.log(`    ${idx + 1}. ${failed.code}: ${failed.message}`);
        });
      }
    } catch (e) {
      console.error(`  ✗ 응답 파싱 실패: ${e.message}`);
      errorRate.add(1);
    }
  } else {
    console.error(`  ✗ 요청 실패 (status: ${response.status})`);
    if (response.body) {
      console.error(`  - Error: ${response.body.substring(0, 200)}`);
    }
  }

  sleep(1); // 요청 사이 1초 대기
}

export function handleSummary(data) {
  const metrics = extractMetrics(data);
  logMetrics('Phase 1: 기본 성능 측정 결과', metrics);

  // 추가 메트릭 출력
  console.log('[상품 등록 통계]');
  console.log('  성공한 상품 수: ' + ((data.metrics.successful_products && data.metrics.successful_products.values).count || 0));
  console.log('  실패한 상품 수: ' + ((data.metrics.failed_products && data.metrics.failed_products.values).count || 0));
  console.log('  평균 등록 시간: ' + (((data.metrics.registration_duration && data.metrics.registration_duration.values).avg || 0) / 1000).toFixed(2) + '초');

  return {
    'k6-tests/results/registration/phase1-baseline-summary.json': JSON.stringify(data, null, 2),
    'k6-tests/results/registration/phase1-baseline-summary.html': htmlReport(data, metrics),
  };
}

function htmlReport(data, metrics) {
  const successCount = ((data.metrics.successful_products && data.metrics.successful_products.values) || {}).count || 0;
  const failureCount = ((data.metrics.failed_products && data.metrics.failed_products.values) || {}).count || 0;
  const avgRegTime = (((data.metrics.registration_duration && data.metrics.registration_duration.values) || {}).avg || 0) / 1000;

  return `
<!DOCTYPE html>
<html>
<head>
    <title>Phase 1: 기본 성능 측정 결과</title>
    <style>${getCommonStyles('#2196F3')}</style>
</head>
<body>
    <div class="container">
        <h1>Phase 1: 기본 성능 측정 (Baseline)</h1>
        <p>데이터: ${PRODUCTS_PER_REQUEST}건 | VU: 1 | 반복: 10회 | 목적: 기준 성능 파악</p>

        <h2>전체 성능 메트릭</h2>
        <div class="metric-grid">${generateMetricCards(metrics)}</div>

        <h2>상품 등록 통계</h2>
        <div class="metric-grid">
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
                <div class="metric-label">초당 등록 상품 수</div>
                <div class="metric-value">${(successCount / ((((data.metrics.http_req_duration && data.metrics.http_req_duration.values) || {}).count || 1) * avgRegTime)).toFixed(2)}</div>
            </div>
        </div>
    </div>
</body>
</html>
  `;
}
