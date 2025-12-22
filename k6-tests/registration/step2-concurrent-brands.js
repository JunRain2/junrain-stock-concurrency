/**
 * Step 2: 동시성 테스트 (Concurrent Brands)
 *
 * 목적: 여러 브랜드가 동시에 상품을 등록하는 실제 운영 환경 시뮬레이션
 *
 * 시나리오:
 * - Brand 1, 2, 3: 각 3000개씩 등록
 * - Brand 4, 5: 각 5000개씩 등록
 * - 총 5개 브랜드가 동시에 상품 등록
 * - 총 상품 수: 3000*3 + 5000*2 = 19,000개
 *
 * 측정 지표:
 * - 처리량: 브랜드별 초당 처리 가능한 상품 수
 * - 응답시간: P50, P95, P99 레이턴시 (브랜드별)
 * - 에러율: 타임아웃, DB 데드락, 커넥션 풀 고갈
 * - 공정성: 각 브랜드의 처리 시간이 공평한지 확인
 *
 * 목표:
 * - 각 브랜드 60초 이내 처리
 * - 에러율 1% 미만
 * - 브랜드 간 처리 시간 편차 30% 이내
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

// 테스트 설정
export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    // Brand 1: 3000개
    brand_1: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 3, // 3회 반복
      maxDuration: '10m',
      startTime: '0s',
      tags: { brand: 'brand_1', batch_size: '3000' },
      env: { PRODUCTS_PER_REQUEST: '3000', OWNER_ID: '1' },
    },
    // Brand 2: 3000개
    brand_2: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 3,
      maxDuration: '10m',
      startTime: '0s', // 동시 시작
      tags: { brand: 'brand_2', batch_size: '3000' },
      env: { PRODUCTS_PER_REQUEST: '3000', OWNER_ID: '2' },
    },
    // Brand 3: 3000개
    brand_3: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 3,
      maxDuration: '10m',
      startTime: '0s', // 동시 시작
      tags: { brand: 'brand_3', batch_size: '3000' },
      env: { PRODUCTS_PER_REQUEST: '3000', OWNER_ID: '3' },
    },
    // Brand 4: 5000개
    brand_4: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 3,
      maxDuration: '10m',
      startTime: '0s', // 동시 시작
      tags: { brand: 'brand_4', batch_size: '5000' },
      env: { PRODUCTS_PER_REQUEST: '5000', OWNER_ID: '4' },
    },
    // Brand 5: 5000개
    brand_5: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 3,
      maxDuration: '10m',
      startTime: '0s', // 동시 시작
      tags: { brand: 'brand_5', batch_size: '5000' },
      env: { PRODUCTS_PER_REQUEST: '5000', OWNER_ID: '5' },
    },
  },
  thresholds: {
    'http_req_duration{brand:brand_1}': ['p(95)<60000'], // 60초 이내
    'http_req_duration{brand:brand_2}': ['p(95)<60000'],
    'http_req_duration{brand:brand_3}': ['p(95)<60000'],
    'http_req_duration{brand:brand_4}': ['p(95)<60000'],
    'http_req_duration{brand:brand_5}': ['p(95)<60000'],
    'errors': ['rate<0.01'], // 에러율 1% 미만
  },
};

// 환경 변수
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

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
function generateProductCode(ownerId, batchSize, index) {
  const timestamp = Date.now();
  const vuNumber = __VU;
  const iterNumber = __ITER;
  const randomPart = Math.random().toString(36).substring(2, 8).toUpperCase();
  return `O${ownerId}-B${batchSize}-${timestamp}-${vuNumber}-${iterNumber}-${index}-${randomPart}`;
}

// 유효한 상품 데이터 생성
function generateValidProduct(ownerId, batchSize, index) {
  return {
    name: generateProductName(),
    price: Math.floor(Math.random() * 1000000) + 1000, // 1,000원 ~ 1,000,000원
    stock: Math.floor(Math.random() * 1000) + 1,       // 1 ~ 1,000개
    code: generateProductCode(ownerId, batchSize, index),
  };
}

// 상품 배열 생성
function generateProducts(ownerId, count) {
  const products = [];
  for (let i = 0; i < count; i++) {
    products.push(generateValidProduct(ownerId, count, i));
  }
  return products;
}

export default function () {
  // 시나리오별 설정 가져오기
  const productsPerRequest = parseInt(__ENV.PRODUCTS_PER_REQUEST || '3000');
  const ownerId = parseInt(__ENV.OWNER_ID || '1');
  const brandTag = __ENV.K6_SCENARIO || 'unknown';

  const products = generateProducts(ownerId, productsPerRequest);

  const payload = JSON.stringify({
    products: products,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
    tags: {
      name: 'ConcurrentBrandRegistration',
      brand: brandTag,
      batch_size: productsPerRequest.toString(),
    },
  };

  console.log(`\n[${brandTag}] Owner ${ownerId}, ${productsPerRequest}개 상품 등록 시작... (Iteration ${__ITER + 1}/3)`);

  const startTime = Date.now();
  const response = http.post(`${BASE_URL}/api/v1/products/bulk?ownerId=${ownerId}`, payload, params);
  const duration = Date.now() - startTime;

  // 응답 검증
  const success = check(response, {
    'status is 200': (r) => r.status === 200,
    'response has body': (r) => r.body && r.body.length > 0,
  });

  errorRate.add(!success, { brand: brandTag });
  registrationDuration.add(duration, { brand: brandTag, batch_size: productsPerRequest.toString() });

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
      console.log(`  - 성공: ${data.successCount}/${productsPerRequest}`);
      console.log(`  - 실패: ${data.failureCount}/${productsPerRequest}`);
      console.log(`  - 처리량: ${productsPerSec} products/sec`);
      console.log(`  - 성공률: ${(data.successCount / productsPerRequest * 100).toFixed(2)}%`);

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
  logMetrics('Step 2: 동시성 테스트 결과', metrics);

  // 브랜드별 통계
  const brands = ['brand_1', 'brand_2', 'brand_3', 'brand_4', 'brand_5'];
  console.log('\n[브랜드별 성능 분석]');

  const brandStats = [];
  brands.forEach(brand => {
    const durationMetric = data.metrics[`registration_duration{brand:${brand}}`];
    const throughputMetric = data.metrics[`throughput{brand:${brand}}`];
    const successMetric = data.metrics[`successful_products{brand:${brand}}`];

    if (durationMetric && durationMetric.values) {
      const avgTime = durationMetric.values.avg / 1000;
      brandStats.push(avgTime);

      console.log(`\n${brand.toUpperCase()}`);
      console.log(`  - 평균 처리 시간: ${avgTime.toFixed(2)}초`);
      console.log(`  - P95 처리 시간: ${(durationMetric.values['p(95)'] / 1000).toFixed(2)}초`);
      console.log(`  - P99 처리 시간: ${(durationMetric.values['p(99)'] / 1000).toFixed(2)}초`);

      if (throughputMetric && throughputMetric.values) {
        console.log(`  - 평균 처리량: ${throughputMetric.values.avg.toFixed(2)} products/sec`);
      }

      if (successMetric && successMetric.values) {
        console.log(`  - 총 성공 건수: ${successMetric.values.count}`);
      }
    }
  });

  // 브랜드 간 공정성 분석 (처리 시간 편차)
  if (brandStats.length > 0) {
    const avgOfAvgs = brandStats.reduce((a, b) => a + b, 0) / brandStats.length;
    const maxDeviation = Math.max(...brandStats.map(t => Math.abs(t - avgOfAvgs) / avgOfAvgs));

    console.log('\n[공정성 분석]');
    console.log(`  - 평균 처리 시간: ${avgOfAvgs.toFixed(2)}초`);
    console.log(`  - 최대 편차: ${(maxDeviation * 100).toFixed(2)}%`);
    console.log(`  - 공정성 목표 (30% 이내): ${maxDeviation <= 0.3 ? '✓ 달성' : '✗ 미달성'}`);
  }

  return {
    'k6-tests/results/registration/step2-concurrent-brands-summary.json': JSON.stringify(data, null, 2),
    'k6-tests/results/registration/step2-concurrent-brands-summary.html': htmlReport(data, metrics),
  };
}

function htmlReport(data, metrics) {
  const brands = ['brand_1', 'brand_2', 'brand_3', 'brand_4', 'brand_5'];
  let brandCards = '';
  const brandStats = [];

  brands.forEach(brand => {
    const durationMetric = data.metrics[`registration_duration{brand:${brand}}`];
    const throughputMetric = data.metrics[`throughput{brand:${brand}}`];
    const successMetric = data.metrics[`successful_products{brand:${brand}}`];

    if (durationMetric && durationMetric.values) {
      const avgTime = (durationMetric.values.avg / 1000).toFixed(2);
      const p95Time = (durationMetric.values['p(95)'] / 1000).toFixed(2);
      const avgThroughput = throughputMetric ? throughputMetric.values.avg.toFixed(2) : 'N/A';
      const successCount = successMetric ? successMetric.values.count : 0;

      brandStats.push(parseFloat(avgTime));

      // 60초 기준 체크
      const status = parseFloat(p95Time) <= 60 ? 'good' : 'bad';

      brandCards += `
        <div class="metric-card">
          <div class="metric-label">${brand.toUpperCase()}</div>
          <div class="metric-value ${status}">${avgTime}초 (P95: ${p95Time}초)</div>
          <div class="metric-unit">처리량: ${avgThroughput} products/sec</div>
          <div class="metric-unit">총 성공: ${successCount}건</div>
        </div>
      `;
    }
  });

  // 공정성 분석
  let fairnessInfo = '';
  if (brandStats.length > 0) {
    const avgOfAvgs = brandStats.reduce((a, b) => a + b, 0) / brandStats.length;
    const maxDeviation = Math.max(...brandStats.map(t => Math.abs(t - avgOfAvgs) / avgOfAvgs));
    const fairnessStatus = maxDeviation <= 0.3 ? 'good' : 'bad';

    fairnessInfo = `
      <h2>공정성 분석</h2>
      <div class="metric-grid">
        <div class="metric-card">
          <div class="metric-label">평균 처리 시간</div>
          <div class="metric-value">${avgOfAvgs.toFixed(2)}초</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">최대 편차</div>
          <div class="metric-value ${fairnessStatus}">${(maxDeviation * 100).toFixed(2)}%</div>
          <div class="metric-unit">목표: 30% 이내</div>
        </div>
      </div>
    `;
  }

  return `
<!DOCTYPE html>
<html>
<head>
    <title>Step 2: 동시성 테스트 결과</title>
    <style>${getCommonStyles('#2196F3')}</style>
</head>
<body>
    <div class="container">
        <h1>Step 2: 동시성 테스트 (Concurrent Brands)</h1>
        <p>5개 브랜드가 동시에 상품 등록 (Brand 1-3: 3000개, Brand 4-5: 5000개)</p>

        <h2>전체 성능 메트릭</h2>
        <div class="metric-grid">${generateMetricCards(metrics)}</div>

        <h2>브랜드별 성능</h2>
        <div class="metric-grid">${brandCards}</div>

        ${fairnessInfo}

        <h2>성능 목표 달성 여부</h2>
        <ul>
          <li>각 브랜드 60초 이내 처리 목표</li>
          <li>에러율: 1% 미만 목표</li>
          <li>브랜드 간 처리 시간 편차: 30% 이내 목표</li>
        </ul>
    </div>
</body>
</html>
  `;
}
