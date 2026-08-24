/**
 * 시나리오 A: 단일 행 경합 (최악값)
 *
 * 모든 요청이 상품 1개(ID=1)를 점유한다. 재고 행이 직렬화되므로
 * 여기서 나온 처리 한계가 이 로직의 최악 조건 성능이다.
 */

import http from 'k6/http';
import { buildOptions, report, RESERVE_URL, PARAMS } from './common.js';

const RATES = [1000, 1300, 1600, 1900];

export const options = buildOptions(RATES);

const PAYLOAD = JSON.stringify({
  items: [{ productId: 1, quantity: 1 }],
});

export default function () {
  http.post(RESERVE_URL, PAYLOAD, PARAMS);
}

export function handleSummary(data) {
  return {
    stdout: report('A: 단일 행 경합', RATES, data),
    'k6-tests/results/reserve/a-single-row.json': JSON.stringify(data, null, 2),
  };
}
