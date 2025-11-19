import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// 커스텀 메트릭 정의
const errorRate = new Rate('errors');
const partialSuccessRate = new Rate('partial_success');

// 테스트 설정
export const options = {
    stages: [
        { duration: '30s', target: 10 },   // 30초 동안 10명까지 증가
        { duration: '1m', target: 50 },    // 1분 동안 50명까지 증가
        { duration: '2m', target: 100 },   // 2분 동안 100명까지 증가
        { duration: '3m', target: 100 },   // 3분 동안 100명 유지
        { duration: '1m', target: 0 },     // 1분 동안 0명으로 감소
    ],
    // 테스트 실패 여부
    thresholds: {
        http_req_duration: ['p(95)<5000'],  // 95%의 요청이 5초 이내 응답
        errors: ['rate<0.1'],                // 에러율 10% 미만
        http_req_failed: ['rate<0.1'],       // 실패율 10% 미만
    },
};

// 환경 변수 설정 (기본값)
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api/v1';
const PRODUCTS_PER_REQUEST = parseInt(__ENV.PRODUCTS_PER_REQUEST || '3000');

// 한글 상품명 생성용 문자 배열
const koreanChars = '가나다라마바사아자차카타파하거너더러머버서어저처커터퍼허고노도로모보소오조초코토포호구누두루무부수우주추쿠투푸후';
const numbers = '0123456789';
const englishChars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz';

// 랜덤 한글 상품명 생성 (비즈니스 제약: 특수문자 불가, 20자 이하, 한글/영문/숫자/공백만 허용)
function generateProductName() {
    const nameLength = Math.floor(Math.random() * 15) + 5; // 5~19자
    let name = '';

    for (let i = 0; i < nameLength; i++) {
        const charType = Math.random();
        if (charType < 0.6) {
            // 60% 확률로 한글
            name += koreanChars.charAt(Math.floor(Math.random() * koreanChars.length));
        } else if (charType < 0.9) {
            // 30% 확률로 영문
            name += englishChars.charAt(Math.floor(Math.random() * englishChars.length));
        } else {
            // 10% 확률로 숫자
            name += numbers.charAt(Math.floor(Math.random() * numbers.length));
        }
    }

    return name;
}

// 랜덤 상품 코드 생성 (unique해야 하므로 timestamp와 random 조합)
function generateProductCode() {
    const timestamp = Date.now();
    const randomPart = Math.random().toString(36).substring(2, 10).toUpperCase();
    const vuNumber = __VU;
    const iterNumber = __ITER;
    return `PROD-${timestamp}-${vuNumber}-${iterNumber}-${randomPart}`;
}

// 유효한 상품 데이터 생성
function generateValidProduct(index) {
    return {
        name: generateProductName(),
        price: Math.floor(Math.random() * 1000000) + 1000, // 1,000원 ~ 1,000,000원
        stock: Math.floor(Math.random() * 1000) + 1,       // 1 ~ 1,000개
        code: generateProductCode() + `-${index}`,          // index를 추가하여 동일 요청 내에서도 unique 보장
    };
}

// 일부 무효한 상품 데이터 생성 (비즈니스 제약 위반)
function generateInvalidProduct(index, errorType) {
    const baseProduct = generateValidProduct(index);

    switch (errorType) {
        case 'long_name':
            // 상품명 20자 초과
            baseProduct.name = '가'.repeat(25);
            break;
        case 'special_char':
            // 특수문자 포함
            baseProduct.name = '테스트상품@#$';
            break;
        case 'negative_price':
            // 음수 가격
            baseProduct.price = -1000;
            break;
        case 'negative_stock':
            // 음수 재고
            baseProduct.stock = -10;
            break;
        case 'empty_name':
            // 빈 상품명
            baseProduct.name = '';
            break;
        case 'empty_code':
            // 빈 상품 코드
            baseProduct.code = '';
            break;
    }

    return baseProduct;
}

// 3000개 상품 데이터 생성 (일부 무효 데이터 포함)
function generateProducts(count) {
    const products = [];
    const invalidRatio = 0.05; // 5%는 무효한 데이터
    // empty_name, empty_code는 요청 자체를 실패시키므로 제외
    const invalidTypes = ['long_name', 'special_char', 'negative_stock'];

    for (let i = 0; i < count; i++) {
        if (Math.random() < invalidRatio) {
            // 무효한 상품 데이터
            const errorType = invalidTypes[Math.floor(Math.random() * invalidTypes.length)];
            products.push(generateInvalidProduct(i, errorType));
        } else {
            // 유효한 상품 데이터
            products.push(generateValidProduct(i));
        }
    }

    return products;
}

export default function () {
    // 사용자당 3000개의 상품 데이터 생성
    const products = generateProducts(PRODUCTS_PER_REQUEST);

    const payload = JSON.stringify({
        products: products,
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
        timeout: '60s', // 타임아웃 60초
    };

    // POST 요청 전송
    const response = http.post(`${BASE_URL}/products`, payload, params);

    // 응답 검증
    const checkResult = check(response, {
        'status is 200': (r) => r.status === 200,
        'response has body': (r) => r.body && r.body.length > 0,
        'response time < 30s': (r) => r.timings.duration < 30000,
    });

    // 에러율 추적
    errorRate.add(!checkResult);

    // 응답 본문 파싱 및 분석
    if (response.status === 200 && response.body) {
        try {
            const result = JSON.parse(response.body);

            console.log(`VU ${__VU}, Iteration ${__ITER}:`);
            console.log(`  Success: ${result.successCount}/${PRODUCTS_PER_REQUEST}`);
            console.log(`  Failure: ${result.failureCount}/${PRODUCTS_PER_REQUEST}`);
            console.log(`  Success Rate: ${(result.successCount / PRODUCTS_PER_REQUEST * 100).toFixed(2)}%`);

            // 부분 성공 추적 (일부 실패가 있는 경우)
            if (result.failureCount > 0 && result.successCount > 0) {
                partialSuccessRate.add(1);

                // 실패한 상품 샘플 출력 (처음 5개만)
                if (result.failedProducts && result.failedProducts.length > 0) {
                    console.log('  Failed Product Samples:');
                    result.failedProducts.slice(0, 5).forEach((failed, idx) => {
                        console.log(`    ${idx + 1}. ${failed.name}: ${failed.message}`);
                    });
                }
            } else if (result.failureCount === PRODUCTS_PER_REQUEST) {
                console.log('  WARNING: All products failed!');
            }

            // 추가 체크
            check(result, {
                'has success count': (r) => r.successCount !== undefined,
                'has failure count': (r) => r.failureCount !== undefined,
                'total count matches': (r) => r.successCount + r.failureCount === PRODUCTS_PER_REQUEST,
            });

        } catch (e) {
            console.error(`Failed to parse response: ${e.message}`);
            errorRate.add(1);
        }
    } else {
        console.error(`Request failed with status ${response.status}`);
        if (response.body) {
            console.error(`Response body: ${response.body.substring(0, 200)}`);
        }
    }

    // 요청 사이 1~3초 대기 (realistic user behavior)
    sleep(Math.random() * 2 + 1);
}

// 테스트 종료 시 요약 출력
export function handleSummary(data) {
    return {
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
        'summary.json': JSON.stringify(data),
    };
}

function textSummary(data, options) {
    const indent = options.indent || '';
    let summary = '\n' + indent + '=== Load Test Summary ===\n';

    if (data.metrics.http_reqs) {
        summary += indent + `Total Requests: ${data.metrics.http_reqs.values.count}\n`;
    }

    if (data.metrics.http_req_duration) {
        summary += indent + `Avg Response Time: ${data.metrics.http_req_duration.values.avg.toFixed(2)}ms\n`;
        summary += indent + `P95 Response Time: ${data.metrics.http_req_duration.values['p(95)'].toFixed(2)}ms\n`;
        summary += indent + `P99 Response Time: ${data.metrics.http_req_duration.values['p(99)'].toFixed(2)}ms\n`;
    }

    if (data.metrics.errors) {
        summary += indent + `Error Rate: ${(data.metrics.errors.values.rate * 100).toFixed(2)}%\n`;
    }

    if (data.metrics.partial_success) {
        summary += indent + `Partial Success Rate: ${(data.metrics.partial_success.values.rate * 100).toFixed(2)}%\n`;
    }

    return summary;
}