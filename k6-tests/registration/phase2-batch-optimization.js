/**
 * Phase 2: 배치 크기 최적화
 * - 데이터: 100 / 500 / 1,000 / 5,000 / 10,000건
 * - VU: 1 (단일 사용자)
 * - 각 크기당: 5회 반복
 * - 목적: 최적 배치 크기 결정
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { errorRate, extractMetrics, logMetrics, getCommonStyles, generateMetricCards } from '../common/common.js';

// 커스텀 메트릭
const successfulProducts = new Counter('successful_products');
const failedProducts = new Counter('failed_products');
const registrationDuration = new Trend('registration_duration');

// 배치 크기별 메트릭
const batch100Duration = new Trend('batch_100_duration');
const batch500Duration = new Trend('batch_500_duration');
const batch1000Duration = new Trend('batch_1000_duration');
const batch5000Duration = new Trend('batch_5000_duration');
const batch10000Duration = new Trend('batch_10000_duration');

// 테스트 설정
export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    batch_100: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 5,
      maxDuration: '10m',
      startTime: '0s',
      env: { BATCH_SIZE: '100' },
      tags: { batch_size: '100' },
    },
    batch_500: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 5,
      maxDuration: '10m',
      startTime: '1m',
      env: { BATCH_SIZE: '500' },
      tags: { batch_size: '500' },
    },
    batch_1000: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 5,
      maxDuration: '10m',
      startTime: '2m',
      env: { BATCH_SIZE: '1000' },
      tags: { batch_size: '1000' },
    },
    batch_5000: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 5,
      maxDuration: '20m',
      startTime: '3m',
      env: { BATCH_SIZE: '5000' },
      tags: { batch_size: '5000' },
    },
    batch_10000: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 5,
      maxDuration: '30m',
      startTime: '6m',
      env: { BATCH_SIZE: '10000' },
      tags: { batch_size: '10000' },
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<60000'], // 95%의 요청이 60초 이내
    errors: ['rate<0.1'],
  },
};

// 환경 변수
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
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

function generateProductCode(batchSize, index) {
  const timestamp = Date.now();
  const vuNumber = __VU;
  const iterNumber = __ITER;
  const randomPart = Math.random().toString(36).substring(2, 8).toUpperCase();
  return `BATCH${batchSize}-${timestamp}-${vuNumber}-${iterNumber}-${index}-${randomPart}`;
}

function generateValidProduct(batchSize, index) {
  return {
    name: generateProductName(),
    price: Math.floor(Math.random() * 1000000) + 1000,
    stock: Math.floor(Math.random() * 1000) + 1,
    code: generateProductCode(batchSize, index),
  };
}

function generateInvalidProduct(batchSize, index, errorType) {
  const baseProduct = generateValidProduct(batchSize, index);

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
      products.push(generateInvalidProduct(count, i, errorType));
    } else {
      products.push(generateValidProduct(count, i));
    }
  }

  return products;
}

export default function () {
  const batchSize = parseInt(__ENV.BATCH_SIZE || '1000');
  const products = generateProducts(batchSize);

  const payload = JSON.stringify({
    products: products,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
    tags: { name: 'BulkProductRegistration', batch_size: batchSize.toString() },
  };

  console.log(`\n[Batch ${batchSize}] [Iteration ${__ITER + 1}/5] ${batchSize}개 상품 등록 시작...`);

  const startTime = Date.now();
  const response = http.post(`${BASE_URL}/api/v1/products/bulk?ownerId=${OWNER_ID}`, payload, params);
  const duration = Date.now() - startTime;

  const success = check(response, {
    'status is 200': (r) => r.status === 200,
    'response has body': (r) => r.body && r.body.length > 0,
  });

  errorRate.add(!success);
  registrationDuration.add(duration);

  // 배치 크기별 메트릭 기록
  switch (batchSize) {
    case 100:
      batch100Duration.add(duration);
      break;
    case 500:
      batch500Duration.add(duration);
      break;
    case 1000:
      batch1000Duration.add(duration);
      break;
    case 5000:
      batch5000Duration.add(duration);
      break;
    case 10000:
      batch10000Duration.add(duration);
      break;
  }

  if (response.status === 200 && response.body) {
    try {
      const result = JSON.parse(response.body);
      const data = result.data;

      successfulProducts.add(data.successCount);
      failedProducts.add(data.failureCount);

      const throughput = (data.successCount / (duration / 1000)).toFixed(2);

      console.log(`  ✓ 완료: ${(duration / 1000).toFixed(2)}초`);
      console.log(`  - 성공: ${data.successCount}/${batchSize}`);
      console.log(`  - 실패: ${data.failureCount}/${batchSize}`);
      console.log(`  - 처리량: ${throughput} 상품/초`);

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
  }

  sleep(2);
}

export function handleSummary(data) {
  const metrics = extractMetrics(data);
  logMetrics('Phase 2: 배치 크기 최적화 결과', metrics);

  console.log('[배치 크기별 성능]');
  if (data.metrics.batch_100_duration) {
    console.log('  Batch 100:   평균 ' + (data.metrics.batch_100_duration.values.avg / 1000).toFixed(2) + '초');
  }
  if (data.metrics.batch_500_duration) {
    console.log('  Batch 500:   평균 ' + (data.metrics.batch_500_duration.values.avg / 1000).toFixed(2) + '초');
  }
  if (data.metrics.batch_1000_duration) {
    console.log('  Batch 1000:  평균 ' + (data.metrics.batch_1000_duration.values.avg / 1000).toFixed(2) + '초');
  }
  if (data.metrics.batch_5000_duration) {
    console.log('  Batch 5000:  평균 ' + (data.metrics.batch_5000_duration.values.avg / 1000).toFixed(2) + '초');
  }
  if (data.metrics.batch_10000_duration) {
    console.log('  Batch 10000: 평균 ' + (data.metrics.batch_10000_duration.values.avg / 1000).toFixed(2) + '초');
  }

  return {
    'k6-tests/results/registration/phase2-batch-optimization-summary.json': JSON.stringify(data, null, 2),
    'k6-tests/results/registration/phase2-batch-optimization-summary.html': htmlReport(data, metrics),
  };
}

function htmlReport(data, metrics) {
  const successCount = ((data.metrics.successful_products && data.metrics.successful_products.values) || {}).count || 0;
  const failureCount = ((data.metrics.failed_products && data.metrics.failed_products.values) || {}).count || 0;

  let batchTable = '<table><thead><tr><th>배치 크기</th><th>평균 시간</th><th>최소 시간</th><th>최대 시간</th><th>초당 처리량</th></tr></thead><tbody>';

  const batches = [
    { size: 100, metric: data.metrics.batch_100_duration },
    { size: 500, metric: data.metrics.batch_500_duration },
    { size: 1000, metric: data.metrics.batch_1000_duration },
    { size: 5000, metric: data.metrics.batch_5000_duration },
    { size: 10000, metric: data.metrics.batch_10000_duration },
  ];

  batches.forEach(batch => {
    if (batch.metric) {
      const avgSec = batch.metric.values.avg / 1000;
      const minSec = batch.metric.values.min / 1000;
      const maxSec = batch.metric.values.max / 1000;
      const throughput = (batch.size / avgSec).toFixed(2);

      batchTable += `<tr>
        <td>${batch.size.toLocaleString()}</td>
        <td>${avgSec.toFixed(2)}초</td>
        <td>${minSec.toFixed(2)}초</td>
        <td>${maxSec.toFixed(2)}초</td>
        <td>${throughput} 상품/초</td>
      </tr>`;
    }
  });

  batchTable += '</tbody></table>';

  return `
<!DOCTYPE html>
<html>
<head>
    <title>Phase 2: 배치 크기 최적화 결과</title>
    <style>${getCommonStyles('#FF9800')}</style>
</head>
<body>
    <div class="container">
        <h1>Phase 2: 배치 크기 최적화</h1>
        <p>데이터: 100/500/1,000/5,000/10,000건 | VU: 1 | 각 5회 반복 | 목적: 최적 배치 크기 결정</p>

        <h2>전체 성능 메트릭</h2>
        <div class="metric-grid">${generateMetricCards(metrics)}</div>

        <h2>배치 크기별 성능 비교</h2>
        ${batchTable}

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
        </div>
    </div>
</body>
</html>
  `;
}
