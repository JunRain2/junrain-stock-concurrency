/**
 * Step 3: 극한 상황 테스트 (Extreme Load)
 *
 * 목적: 시스템의 한계점 파악 및 극한 상황에서의 안정성 검증
 *
 * 시나리오:
 * - 10개 브랜드가 동시에 각 5000개씩 상품 등록
 * - 총 상품 수: 50,000개
 * - 시스템 리소스 한계 테스트 (CPU, 메모리, DB 커넥션 풀)
 *
 * 측정 지표:
 * - 처리량: 초당 처리 가능한 상품 수 (전체 시스템)
 * - 응답시간: P50, P95, P99 레이턴시
 * - 에러율: 타임아웃, DB 데드락, 커넥션 풀 고갈
 * - 리소스 한계: CPU, 메모리, DB 커넥션 사용률
 * - 안정성: 에러 발생 시 시스템 복구 능력
 *
 * 목표:
 * - 시스템 다운 없이 완료
 * - 에러율 10% 미만 (극한 상황 고려)
 * - 전체 처리 시간 파악
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { errorRate, extractMetrics, logMetrics, getCommonStyles, generateMetricCards } from '../common/common.js';

// 커스텀 메트릭
const successfulProducts = new Counter('successful_products');
const failedProducts = new Counter('failed_products');
const registrationDuration = new Trend('registration_duration');
const throughput = new Trend('throughput'); // products/sec
const systemErrors = new Counter('system_errors'); // 시스템 레벨 에러
const businessErrors = new Counter('business_errors'); // 비즈니스 로직 에러

// 테스트 설정
export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    // 10개 브랜드 동시 등록
    brand_1: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 2, // 각 2회 반복
      maxDuration: '20m',
      startTime: '0s',
      tags: { brand: 'brand_1' },
      env: { OWNER_ID: '1' },
    },
    brand_2: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 2,
      maxDuration: '20m',
      startTime: '0s',
      tags: { brand: 'brand_2' },
      env: { OWNER_ID: '2' },
    },
    brand_3: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 2,
      maxDuration: '20m',
      startTime: '0s',
      tags: { brand: 'brand_3' },
      env: { OWNER_ID: '3' },
    },
    brand_4: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 2,
      maxDuration: '20m',
      startTime: '0s',
      tags: { brand: 'brand_4' },
      env: { OWNER_ID: '4' },
    },
    brand_5: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 2,
      maxDuration: '20m',
      startTime: '0s',
      tags: { brand: 'brand_5' },
      env: { OWNER_ID: '5' },
    },
    brand_6: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 2,
      maxDuration: '20m',
      startTime: '0s',
      tags: { brand: 'brand_6' },
      env: { OWNER_ID: '6' },
    },
    brand_7: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 2,
      maxDuration: '20m',
      startTime: '0s',
      tags: { brand: 'brand_7' },
      env: { OWNER_ID: '7' },
    },
    brand_8: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 2,
      maxDuration: '20m',
      startTime: '0s',
      tags: { brand: 'brand_8' },
      env: { OWNER_ID: '8' },
    },
    brand_9: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 2,
      maxDuration: '20m',
      startTime: '0s',
      tags: { brand: 'brand_9' },
      env: { OWNER_ID: '9' },
    },
    brand_10: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 2,
      maxDuration: '20m',
      startTime: '0s',
      tags: { brand: 'brand_10' },
      env: { OWNER_ID: '10' },
    },
  },
  thresholds: {
    'http_req_duration': ['p(95)<120000'], // 극한 상황: 2분 이내
    'errors': ['rate<0.1'], // 에러율 10% 미만
    'system_errors': ['count<20'], // 시스템 에러 20건 미만
  },
};

// 환경 변수
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUCTS_PER_REQUEST = 5000; // 고정 5000개

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
function generateProductCode(ownerId, index) {
  const timestamp = Date.now();
  const vuNumber = __VU;
  const iterNumber = __ITER;
  const randomPart = Math.random().toString(36).substring(2, 8).toUpperCase();
  return `EXTREME-O${ownerId}-${timestamp}-${vuNumber}-${iterNumber}-${index}-${randomPart}`;
}

// 유효한 상품 데이터 생성
function generateValidProduct(ownerId, index) {
  return {
    name: generateProductName(),
    price: Math.floor(Math.random() * 1000000) + 1000, // 1,000원 ~ 1,000,000원
    stock: Math.floor(Math.random() * 1000) + 1,       // 1 ~ 1,000개
    code: generateProductCode(ownerId, index),
  };
}

// 상품 배열 생성
function generateProducts(ownerId, count) {
  const products = [];
  for (let i = 0; i < count; i++) {
    products.push(generateValidProduct(ownerId, i));
  }
  return products;
}

export default function () {
  const ownerId = parseInt(__ENV.OWNER_ID || '1');
  const brandTag = __ENV.K6_SCENARIO || 'unknown';

  const products = generateProducts(ownerId, PRODUCTS_PER_REQUEST);

  const payload = JSON.stringify({
    products: products,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
    tags: {
      name: 'ExtremeLoadRegistration',
      brand: brandTag,
    },
    timeout: '180s', // 3분 타임아웃
  };

  console.log(`\n[EXTREME] ${brandTag}, Owner ${ownerId}, ${PRODUCTS_PER_REQUEST}개 상품 등록 시작... (Iteration ${__ITER + 1}/2)`);

  const startTime = Date.now();
  const response = http.post(`${BASE_URL}/api/v1/products/bulk?ownerId=${ownerId}`, payload, params);
  const duration = Date.now() - startTime;

  // 응답 검증
  const success = check(response, {
    'status is 200': (r) => r.status === 200,
    'response has body': (r) => r.body && r.body.length > 0,
    'no timeout': (r) => r.status !== 0,
  });

  errorRate.add(!success, { brand: brandTag });
  registrationDuration.add(duration, { brand: brandTag });

  // 에러 유형 분류
  if (!success) {
    if (response.status === 0 || response.status >= 500) {
      systemErrors.add(1, { brand: brandTag, error_type: 'system' });
      console.error(`  ✗ 시스템 에러 발생 (status: ${response.status})`);
    } else if (response.status >= 400) {
      businessErrors.add(1, { brand: brandTag, error_type: 'business' });
      console.error(`  ✗ 비즈니스 에러 발생 (status: ${response.status})`);
    }
  }

  // 응답 파싱 및 처리량 계산
  if (response.status === 200 && response.body) {
    try {
      const result = JSON.parse(response.body);
      const data = result.data;

      successfulProducts.add(data.successCount, { brand: brandTag });
      failedProducts.add(data.failureCount, { brand: brandTag });

      // 처리량 계산 (products/sec)
      const productsPerSec = (data.successCount / (duration / 1000)).toFixed(2);
      throughput.add(parseFloat(productsPerSec), { brand: brandTag });

      console.log(`  ✓ 완료: ${(duration / 1000).toFixed(2)}초`);
      console.log(`  - 성공: ${data.successCount}/${PRODUCTS_PER_REQUEST}`);
      console.log(`  - 실패: ${data.failureCount}/${PRODUCTS_PER_REQUEST}`);
      console.log(`  - 처리량: ${productsPerSec} products/sec`);
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
      errorRate.add(1, { brand: brandTag });
      systemErrors.add(1, { brand: brandTag, error_type: 'parse_error' });
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
  logMetrics('Step 3: 극한 상황 테스트 결과', metrics);

  // 전체 통계
  const totalSuccess = (data.metrics.successful_products && data.metrics.successful_products.values) || {};
  const totalFailed = (data.metrics.failed_products && data.metrics.failed_products.values) || {};
  const avgDuration = (data.metrics.registration_duration && data.metrics.registration_duration.values) || {};
  const avgThroughput = (data.metrics.throughput && data.metrics.throughput.values) || {};
  const sysErrors = (data.metrics.system_errors && data.metrics.system_errors.values) || {};
  const bizErrors = (data.metrics.business_errors && data.metrics.business_errors.values) || {};

  console.log('\n[전체 시스템 성능 분석]');
  console.log(`  - 총 성공 상품: ${totalSuccess.count || 0}건`);
  console.log(`  - 총 실패 상품: ${totalFailed.count || 0}건`);
  console.log(`  - 평균 처리 시간: ${(avgDuration.avg / 1000 || 0).toFixed(2)}초`);
  console.log(`  - P95 처리 시간: ${(avgDuration['p(95)'] / 1000 || 0).toFixed(2)}초`);
  console.log(`  - P99 처리 시간: ${(avgDuration['p(99)'] / 1000 || 0).toFixed(2)}초`);
  console.log(`  - 평균 처리량: ${(avgThroughput.avg || 0).toFixed(2)} products/sec`);

  console.log('\n[에러 분석]');
  console.log(`  - 시스템 에러: ${sysErrors.count || 0}건`);
  console.log(`  - 비즈니스 에러: ${bizErrors.count || 0}건`);

  // 브랜드별 통계
  const brands = ['brand_1', 'brand_2', 'brand_3', 'brand_4', 'brand_5',
                  'brand_6', 'brand_7', 'brand_8', 'brand_9', 'brand_10'];
  console.log('\n[브랜드별 성능 분석]');

  brands.forEach(brand => {
    const durationMetric = data.metrics[`registration_duration{brand:${brand}}`];
    const throughputMetric = data.metrics[`throughput{brand:${brand}}`];
    const successMetric = data.metrics[`successful_products{brand:${brand}}`];

    if (durationMetric && durationMetric.values) {
      console.log(`\n${brand.toUpperCase()}`);
      console.log(`  - 평균 처리 시간: ${(durationMetric.values.avg / 1000).toFixed(2)}초`);
      console.log(`  - P95 처리 시간: ${(durationMetric.values['p(95)'] / 1000).toFixed(2)}초`);

      if (throughputMetric && throughputMetric.values) {
        console.log(`  - 평균 처리량: ${throughputMetric.values.avg.toFixed(2)} products/sec`);
      }

      if (successMetric && successMetric.values) {
        console.log(`  - 총 성공 건수: ${successMetric.values.count}`);
      }
    }
  });

  return {
    'k6-tests/results/registration/step3-extreme-load-summary.json': JSON.stringify(data, null, 2),
    'k6-tests/results/registration/step3-extreme-load-summary.html': htmlReport(data, metrics),
  };
}

function htmlReport(data, metrics) {
  // 전체 통계
  const totalSuccess = ((data.metrics.successful_products && data.metrics.successful_products.values) || {}).count || 0;
  const totalFailed = ((data.metrics.failed_products && data.metrics.failed_products.values) || {}).count || 0;
  const avgDuration = ((data.metrics.registration_duration && data.metrics.registration_duration.values) || {}).avg || 0;
  const p95Duration = ((data.metrics.registration_duration && data.metrics.registration_duration.values) || {})['p(95)'] || 0;
  const avgThroughput = ((data.metrics.throughput && data.metrics.throughput.values) || {}).avg || 0;
  const sysErrors = ((data.metrics.system_errors && data.metrics.system_errors.values) || {}).count || 0;
  const bizErrors = ((data.metrics.business_errors && data.metrics.business_errors.values) || {}).count || 0;

  const totalProducts = totalSuccess + totalFailed;
  const successRate = totalProducts > 0 ? (totalSuccess / totalProducts * 100).toFixed(2) : 0;

  // 브랜드별 카드
  const brands = ['brand_1', 'brand_2', 'brand_3', 'brand_4', 'brand_5',
                  'brand_6', 'brand_7', 'brand_8', 'brand_9', 'brand_10'];
  let brandCards = '';

  brands.forEach(brand => {
    const durationMetric = data.metrics[`registration_duration{brand:${brand}}`];
    const throughputMetric = data.metrics[`throughput{brand:${brand}}`];
    const successMetric = data.metrics[`successful_products{brand:${brand}}`];

    if (durationMetric && durationMetric.values) {
      const avgTime = (durationMetric.values.avg / 1000).toFixed(2);
      const p95Time = (durationMetric.values['p(95)'] / 1000).toFixed(2);
      const brandThroughput = throughputMetric ? throughputMetric.values.avg.toFixed(2) : 'N/A';
      const successCount = successMetric ? successMetric.values.count : 0;

      // 120초 기준 체크
      const status = parseFloat(p95Time) <= 120 ? 'good' : 'bad';

      brandCards += `
        <div class="metric-card">
          <div class="metric-label">${brand.toUpperCase()}</div>
          <div class="metric-value ${status}">${avgTime}초 (P95: ${p95Time}초)</div>
          <div class="metric-unit">처리량: ${brandThroughput} products/sec</div>
          <div class="metric-unit">총 성공: ${successCount}건</div>
        </div>
      `;
    }
  });

  return `
<!DOCTYPE html>
<html>
<head>
    <title>Step 3: 극한 상황 테스트 결과</title>
    <style>${getCommonStyles('#F44336')}</style>
</head>
<body>
    <div class="container">
        <h1>Step 3: 극한 상황 테스트 (Extreme Load)</h1>
        <p>10개 브랜드가 동시에 각 5000개씩 상품 등록 (총 50,000개)</p>

        <h2>전체 성능 메트릭</h2>
        <div class="metric-grid">${generateMetricCards(metrics)}</div>

        <h2>전체 시스템 통계</h2>
        <div class="metric-grid">
          <div class="metric-card">
            <div class="metric-label">총 성공 상품</div>
            <div class="metric-value good">${totalSuccess.toLocaleString()}</div>
          </div>
          <div class="metric-card">
            <div class="metric-label">총 실패 상품</div>
            <div class="metric-value ${totalFailed > totalSuccess * 0.1 ? 'bad' : 'good'}">${totalFailed.toLocaleString()}</div>
          </div>
          <div class="metric-card">
            <div class="metric-label">성공률</div>
            <div class="metric-value ${parseFloat(successRate) >= 90 ? 'good' : 'bad'}">${successRate}%</div>
          </div>
          <div class="metric-card">
            <div class="metric-label">평균 처리량</div>
            <div class="metric-value">${avgThroughput.toFixed(2)} <span class="metric-unit">products/sec</span></div>
          </div>
        </div>

        <h2>에러 분석</h2>
        <div class="metric-grid">
          <div class="metric-card">
            <div class="metric-label">시스템 에러</div>
            <div class="metric-value ${sysErrors > 20 ? 'bad' : 'good'}">${sysErrors}</div>
            <div class="metric-unit">목표: 20건 미만</div>
          </div>
          <div class="metric-card">
            <div class="metric-label">비즈니스 에러</div>
            <div class="metric-value">${bizErrors}</div>
          </div>
        </div>

        <h2>브랜드별 성능</h2>
        <div class="metric-grid">${brandCards}</div>

        <h2>성능 목표 달성 여부</h2>
        <ul>
          <li>시스템 다운 없이 완료 목표</li>
          <li>에러율: 10% 미만 목표</li>
          <li>P95 처리 시간: 2분 이내 목표</li>
          <li>시스템 에러: 20건 미만 목표</li>
        </ul>
    </div>
</body>
</html>
  `;
}
