/**
 * Step 1: 기본 성능 측정 (Baseline Performance)
 *
 * 목적: 단계별 데이터 크기에 따른 기본 처리 성능 파악
 *
 * 테스트 단계:
 * - 100개: Warm-up 및 최소 단위 성능 측정
 * - 500개: 중소 규모 배치 처리 성능
 * - 1000개: 표준 배치 크기 성능
 * - 3000개: 대량 배치 처리 성능
 * - 5000개: 최대 권장 배치 크기 성능
 *
 * 측정 지표:
 * - 처리량: 초당 처리 가능한 상품 수 (products/sec)
 * - 응답시간: P50, P95, P99 레이턴시
 * - 에러율: 타임아웃, DB 데드락, 커넥션 풀 고갈
 * - 리소스: 요청당 처리 시간
 *
 * 목표:
 * - 5000개 단일 요청: 30초 이내 처리
 * - 에러율 1% 미만
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

// 배치 크기 설정
const BATCH_SIZES = [100, 500, 1000, 3000, 5000];
const ITERATIONS_PER_BATCH = 5;

// 테스트 설정
export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    sequential_batches: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: '30m',
    },
  },
  thresholds: {
    'http_req_duration{batch_size:5000}': ['p(95)<30000'], // 5000개는 30초 이내
    'http_req_duration{batch_size:3000}': ['p(95)<20000'], // 3000개는 20초 이내
    'http_req_duration{batch_size:1000}': ['p(95)<10000'], // 1000개는 10초 이내
    'errors': ['rate<0.01'], // 에러율 1% 미만
    'throughput': ['avg>100'], // 평균 초당 100개 이상 처리
  },
};

// 환경 변수
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
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
function generateProductCode(batchSize, index) {
  const timestamp = Date.now();
  const vuNumber = __VU;
  const iterNumber = __ITER;
  const randomPart = Math.random().toString(36).substring(2, 8).toUpperCase();
  return `B${batchSize}-${timestamp}-${vuNumber}-${iterNumber}-${index}-${randomPart}`;
}

// 유효한 상품 데이터 생성
function generateValidProduct(batchSize, index) {
  return {
    name: generateProductName(),
    price: Math.floor(Math.random() * 1000000) + 1000, // 1,000원 ~ 1,000,000원
    stock: Math.floor(Math.random() * 1000) + 1,       // 1 ~ 1,000개
    code: generateProductCode(batchSize, index),
  };
}

// 상품 배열 생성
function generateProducts(count) {
  const products = [];
  for (let i = 0; i < count; i++) {
    products.push(generateValidProduct(count, i));
  }
  return products;
}

export default function () {
  console.log('\n========================================');
  console.log('Step 1: 기본 성능 측정 시작');
  console.log('========================================\n');

  // 모든 배치 크기에 대해 순차적으로 실행
  BATCH_SIZES.forEach(batchSize => {
    console.log(`\n[배치 크기: ${batchSize}개] 테스트 시작`);
    console.log(`총 ${ITERATIONS_PER_BATCH}회 반복 실행`);

    // 각 배치 크기당 5회 반복
    for (let iter = 1; iter <= ITERATIONS_PER_BATCH; iter++) {
      const products = generateProducts(batchSize);

      const payload = JSON.stringify({
        products: products,
      });

      const params = {
        headers: {
          'Content-Type': 'application/json',
        },
        tags: {
          name: 'BulkProductRegistration',
          batch_size: batchSize.toString(),
        },
      };

      console.log(`\n  Iteration ${iter}/${ITERATIONS_PER_BATCH}...`);

      const startTime = Date.now();
      const response = http.post(`${BASE_URL}/api/v1/products/bulk?ownerId=${OWNER_ID}`, payload, params);
      const duration = Date.now() - startTime;

      // 응답 검증
      const success = check(response, {
        'status is 200': (r) => r.status === 200,
        'response has body': (r) => r.body && r.body.length > 0,
      });

      errorRate.add(!success);
      registrationDuration.add(duration, { batch_size: batchSize.toString() });

      // 응답 파싱 및 처리량 계산
      if (response.status === 200 && response.body) {
        try {
          const result = JSON.parse(response.body);
          const data = result.data;

          successfulProducts.add(data.successCount, { batch_size: batchSize.toString() });
          failedProducts.add(data.failureCount, { batch_size: batchSize.toString() });

          // 처리량 계산 (products/sec)
          const productsPerSec = (data.successCount / (duration / 1000)).toFixed(2);
          throughput.add(parseFloat(productsPerSec), { batch_size: batchSize.toString() });

          console.log(`    ✓ 완료: ${(duration / 1000).toFixed(2)}초`);
          console.log(`    - 성공: ${data.successCount}/${batchSize}`);
          console.log(`    - 실패: ${data.failureCount}/${batchSize}`);
          console.log(`    - 처리량: ${productsPerSec} products/sec`);
          console.log(`    - 성공률: ${(data.successCount / batchSize * 100).toFixed(2)}%`);

          // 실패 샘플 출력
          if (data.failedProducts && data.failedProducts.length > 0) {
            console.log(`    - 실패 샘플 (최대 3개):`);
            data.failedProducts.slice(0, 3).forEach((failed, idx) => {
              console.log(`      ${idx + 1}. index ${failed.index}: ${failed.message}`);
            });
          }
        } catch (e) {
          console.error(`    ✗ 응답 파싱 실패: ${e.message}`);
          errorRate.add(1);
        }
      } else {
        console.error(`    ✗ 요청 실패 (status: ${response.status})`);
        if (response.body) {
          console.error(`    - Error: ${response.body.substring(0, 200)}`);
        }
      }

      sleep(2); // 요청 사이 2초 대기 (DB 안정화)
    }

    console.log(`\n[배치 크기: ${batchSize}개] 테스트 완료\n`);
  });

  console.log('\n========================================');
  console.log('Step 1: 기본 성능 측정 완료');
  console.log('========================================\n');
}

export function handleSummary(data) {
  const metrics = extractMetrics(data);
  logMetrics('Step 1: 기본 성능 측정 결과', metrics);

  // 배치 크기별 통계
  const batchSizes = ['100', '500', '1000', '3000', '5000'];
  console.log('\n[배치 크기별 성능 분석]');

  batchSizes.forEach(size => {
    const durationMetric = data.metrics[`registration_duration{batch_size:${size}}`];
    const throughputMetric = data.metrics[`throughput{batch_size:${size}}`];
    const successMetric = data.metrics[`successful_products{batch_size:${size}}`];

    if (durationMetric && durationMetric.values) {
      console.log(`\n배치 크기: ${size}개`);
      console.log(`  - 평균 처리 시간: ${(durationMetric.values.avg / 1000).toFixed(2)}초`);
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

  return {
    'k6-tests/results/registration/step1-basic-performance-summary.json': JSON.stringify(data, null, 2),
    'k6-tests/results/registration/step1-basic-performance-summary.html': htmlReport(data, metrics),
  };
}

function htmlReport(data, metrics) {
  const batchSizes = ['100', '500', '1000', '3000', '5000'];
  let batchCards = '';

  batchSizes.forEach(size => {
    const durationMetric = data.metrics[`registration_duration{batch_size:${size}}`];
    const throughputMetric = data.metrics[`throughput{batch_size:${size}}`];
    const successMetric = data.metrics[`successful_products{batch_size:${size}}`];

    if (durationMetric && durationMetric.values) {
      const avgTime = (durationMetric.values.avg / 1000).toFixed(2);
      const p95Time = (durationMetric.values['p(95)'] / 1000).toFixed(2);
      const avgThroughput = throughputMetric ? throughputMetric.values.avg.toFixed(2) : 'N/A';
      const successCount = successMetric ? successMetric.values.count : 0;

      // 5000개는 30초 기준, 3000개는 20초 기준 체크
      const threshold = size === '5000' ? 30 : size === '3000' ? 20 : size === '1000' ? 10 : 5;
      const status = parseFloat(p95Time) <= threshold ? 'good' : 'bad';

      batchCards += `
        <div class="metric-card">
          <div class="metric-label">배치 크기: ${size}개</div>
          <div class="metric-value ${status}">${avgTime}초 (P95: ${p95Time}초)</div>
          <div class="metric-unit">처리량: ${avgThroughput} products/sec</div>
          <div class="metric-unit">총 성공: ${successCount}건</div>
        </div>
      `;
    }
  });

  return `
<!DOCTYPE html>
<html>
<head>
    <title>Step 1: 기본 성능 측정 결과</title>
    <style>${getCommonStyles('#4CAF50')}</style>
</head>
<body>
    <div class="container">
        <h1>Step 1: 기본 성능 측정 (Baseline Performance)</h1>
        <p>단계별 데이터 크기에 따른 처리 성능 파악</p>

        <h2>전체 성능 메트릭</h2>
        <div class="metric-grid">${generateMetricCards(metrics)}</div>

        <h2>배치 크기별 성능</h2>
        <div class="metric-grid">${batchCards}</div>

        <h2>성능 목표 달성 여부</h2>
        <ul>
          <li>5000개 단일 요청: 30초 이내 처리 목표</li>
          <li>에러율: 1% 미만 목표</li>
          <li>평균 처리량: 초당 100개 이상 목표</li>
        </ul>
    </div>
</body>
</html>
  `;
}
