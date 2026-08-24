/**
 * 시나리오 B: 경합 분산 (분모)
 *
 * 상품 100개에 요청을 흩는다. 최고 rate 1200에서도 행당 12 RPS라
 * 같은 행에 요청이 겹칠 확률이 사실상 0이다. 경합을 뺀 순수 처리 비용.
 *
 * A와 나란히 놓기 위해 150/300/600을 공유한다.
 */

import http from 'k6/http';
import { buildOptions, report, RESERVE_URL, PARAMS } from './common.js';

const RATES = [1000, 1300, 1600, 1900];
const PRODUCT_COUNT = 100;

export const options = buildOptions(RATES);

// payload를 미리 만들어 둔다. 이터레이션마다 JSON.stringify를 돌면 그만큼이
// 부하 생성기의 CPU 비용이 되고, 초당 수백 회에서는 도착률 유지에 영향을 준다.
const PAYLOADS = Array.from({ length: PRODUCT_COUNT }, (_, i) =>
  JSON.stringify({ items: [{ productId: i + 1, quantity: 1 }] }),
);

export default function () {
  http.post(RESERVE_URL, PAYLOADS[Math.floor(Math.random() * PRODUCT_COUNT)], PARAMS);
}

export function handleSummary(data) {
  return {
    stdout: report('B: 경합 분산', RATES, data),
    'k6-tests/results/reserve/b-spread.json': JSON.stringify(data, null, 2),
  };
}
