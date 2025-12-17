/**
 * k6 테스트 공통 유틸리티
 */

import { Rate, Trend } from 'k6/metrics';

// 공통 메트릭
export const errorRate = new Rate('errors');
export const purchaseDuration = new Trend('purchase_duration');

/**
 * 공통 HTML 스타일
 */
export function getCommonStyles(themeColor = '#4CAF50') {
  return `
    body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }
    .container { max-width: 1200px; margin: 0 auto; background-color: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
    h1 { color: #333; border-bottom: 3px solid ${themeColor}; padding-bottom: 10px; }
    h2 { color: #555; margin-top: 30px; }
    .metric-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 20px; margin: 20px 0; }
    .metric-card { background: #f9f9f9; padding: 20px; border-radius: 5px; border-left: 4px solid ${themeColor}; }
    .metric-label { font-size: 14px; color: #666; margin-bottom: 5px; }
    .metric-value { font-size: 24px; font-weight: bold; color: #333; }
    .metric-unit { font-size: 16px; color: #888; }
    .good { color: #4CAF50; }
    .warning { color: #FF9800; }
    .bad { color: #F44336; }
    table { border-collapse: collapse; width: 100%; margin: 20px 0; }
    th, td { border: 1px solid #ddd; padding: 12px; text-align: left; }
    th { background-color: ${themeColor}; color: white; }
    tr:nth-child(even) { background-color: #f9f9f9; }
  `;
}

/**
 * 메트릭 추출 헬퍼
 */
export function extractMetrics(data) {
  const metrics = data.metrics;
  return {
    httpReqs: (metrics.http_reqs && metrics.http_reqs.values) || {},
    httpReqDuration: (metrics.http_req_duration && metrics.http_req_duration.values) || {},
    errors: (metrics.errors && metrics.errors.values) || {},
  };
}

/**
 * 메트릭 HTML 카드 생성
 */
export function generateMetricCards(metrics) {
  const { httpReqs, httpReqDuration, errors } = metrics;

  return `
    <div class="metric-card">
      <div class="metric-label">총 요청 수</div>
      <div class="metric-value">${httpReqs.count || 0}</div>
    </div>
    <div class="metric-card">
      <div class="metric-label">초당 요청 수 (TPS)</div>
      <div class="metric-value">${(httpReqs.rate || 0).toFixed(2)}</div>
    </div>
    <div class="metric-card">
      <div class="metric-label">평균 응답 시간</div>
      <div class="metric-value">${(httpReqDuration.avg || 0).toFixed(2)} <span class="metric-unit">ms</span></div>
    </div>
    <div class="metric-card">
      <div class="metric-label">P95 응답 시간</div>
      <div class="metric-value ${(httpReqDuration['p(95)'] || 0) > 5000 ? 'bad' : (httpReqDuration['p(95)'] || 0) > 3000 ? 'warning' : 'good'}">${(httpReqDuration['p(95)'] || 0).toFixed(2)} <span class="metric-unit">ms</span></div>
    </div>
    <div class="metric-card">
      <div class="metric-label">P99 응답 시간</div>
      <div class="metric-value ${(httpReqDuration['p(99)'] || 0) > 10000 ? 'bad' : (httpReqDuration['p(99)'] || 0) > 5000 ? 'warning' : 'good'}">${(httpReqDuration['p(99)'] || 0).toFixed(2)} <span class="metric-unit">ms</span></div>
    </div>
    <div class="metric-card">
      <div class="metric-label">최대 응답 시간</div>
      <div class="metric-value">${(httpReqDuration.max || 0).toFixed(2)} <span class="metric-unit">ms</span></div>
    </div>
    <div class="metric-card">
      <div class="metric-label">에러율</div>
      <div class="metric-value ${((errors.rate || 0) * 100) > 5 ? 'bad' : ((errors.rate || 0) * 100) > 1 ? 'warning' : 'good'}">${((errors.rate || 0) * 100).toFixed(2)} <span class="metric-unit">%</span></div>
    </div>
  `;
}

/**
 * 콘솔 로그 출력
 */
export function logMetrics(title, metrics) {
  const { httpReqs, httpReqDuration, errors } = metrics;

  console.log(`\n=== ${title} ===\n`);
  console.log('[전체 성능 메트릭]');
  console.log('  총 요청 수: ' + (httpReqs.count || 0));
  console.log('  평균 응답 시간: ' + (httpReqDuration.avg || 0).toFixed(2) + 'ms');
  console.log('  P95 응답 시간: ' + (httpReqDuration['p(95)'] || 0).toFixed(2) + 'ms');
  console.log('  P99 응답 시간: ' + (httpReqDuration['p(99)'] || 0).toFixed(2) + 'ms');
  console.log('  최대 응답 시간: ' + (httpReqDuration.max || 0).toFixed(2) + 'ms');
  console.log('  에러율: ' + ((errors.rate || 0) * 100).toFixed(2) + '%');
  console.log('  초당 요청 수(TPS): ' + (httpReqs.rate || 0).toFixed(2) + '\n');
}
