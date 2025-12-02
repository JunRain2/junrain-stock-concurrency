#!/usr/bin/env python3
"""
K6 JSON 결과 분석 및 리포트 생성
사용법: python analyze.py <json-file> [--html] [--csv]
"""

import json
import sys
import statistics
from datetime import datetime
from collections import defaultdict
import argparse

def parse_k6_json(filepath):
    """K6 JSON Lines 파일 파싱"""
    metrics = defaultdict(list)
    scenario_data = defaultdict(lambda: defaultdict(list))
    product_data = defaultdict(lambda: defaultdict(list))

    with open(filepath, 'r') as f:
        for line in f:
            if not line.strip():
                continue

            try:
                data = json.loads(line)

                if data.get('type') == 'Point':
                    metric_name = data.get('metric')
                    value = data['data'].get('value', 0)
                    tags = data['data'].get('tags', {})

                    # 전체 메트릭 수집
                    metrics[metric_name].append({
                        'value': value,
                        'time': data['data'].get('time'),
                        'tags': tags
                    })

                    # 시나리오별 분류
                    scenario = tags.get('scenario')
                    if scenario and metric_name == 'http_req_duration':
                        scenario_data[scenario]['durations'].append(value)
                        scenario_data[scenario]['count'] += 1

                    # 상품별 분류
                    product_id = tags.get('productId')
                    if product_id and metric_name == 'http_req_duration':
                        product_data[product_id]['durations'].append(value)
                        product_data[product_id]['count'] += 1

            except json.JSONDecodeError:
                continue

    return metrics, scenario_data, product_data

def calculate_percentile(data, percentile):
    """백분위수 계산"""
    if not data:
        return 0
    sorted_data = sorted(data)
    index = int(len(sorted_data) * percentile / 100)
    return sorted_data[min(index, len(sorted_data) - 1)]

def calculate_stats(values):
    """통계 계산"""
    if not values:
        return None

    return {
        'count': len(values),
        'min': min(values),
        'max': max(values),
        'avg': statistics.mean(values),
        'median': statistics.median(values),
        'p90': calculate_percentile(values, 90),
        'p95': calculate_percentile(values, 95),
        'p99': calculate_percentile(values, 99),
    }

def print_report(metrics, scenario_data, product_data):
    """콘솔 리포트 출력"""
    print("\n" + "="*60)
    print("           K6 성능 테스트 결과 분석")
    print("="*60 + "\n")

    # HTTP 요청 통계
    http_durations = [m['value'] for m in metrics.get('http_req_duration', [])]
    http_waiting = [m['value'] for m in metrics.get('http_req_waiting', [])]
    http_reqs_count = len(metrics.get('http_reqs', []))
    http_failed_count = sum(1 for m in metrics.get('http_req_failed', []) if m['value'] == 1)
    errors_count = sum(1 for m in metrics.get('errors', []) if m['value'] == 1)

    # 시간 범위
    times = [datetime.fromisoformat(m['time'].replace('Z', '+00:00')) for m in metrics.get('http_req_duration', []) if m.get('time')]
    if times:
        start_time = min(times)
        end_time = max(times)
        duration_sec = (end_time - start_time).total_seconds()
    else:
        start_time = end_time = datetime.now()
        duration_sec = 0

    # TPS 계산
    tps = http_reqs_count / duration_sec if duration_sec > 0 else 0
    error_rate = (errors_count / http_reqs_count * 100) if http_reqs_count > 0 else 0

    print("📊 전체 통계")
    print("-" * 60)
    print(f"테스트 시작: {start_time.strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"테스트 종료: {end_time.strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"총 소요 시간: {duration_sec:.2f}초 ({duration_sec/60:.2f}분)")
    print(f"총 요청 수: {http_reqs_count:,}")
    print(f"실패한 요청: {http_failed_count} ({http_failed_count/http_reqs_count*100:.2f}%)" if http_reqs_count > 0 else "실패한 요청: 0")
    print(f"에러 수: {errors_count} ({error_rate:.2f}%)")
    print(f"TPS (초당 요청): {tps:.2f}")
    print()

    # 응답 시간 통계
    duration_stats = calculate_stats(http_durations)
    if duration_stats:
        print("⏱️  HTTP 요청 응답 시간 (http_req_duration)")
        print("-" * 60)
        print(f"{'':15} {'값':>12} {'단위':>8}")
        print(f"{'평균':15} {duration_stats['avg']:>12.2f} {'ms':>8}")
        print(f"{'중앙값':15} {duration_stats['median']:>12.2f} {'ms':>8}")
        print(f"{'최소':15} {duration_stats['min']:>12.2f} {'ms':>8}")
        print(f"{'최대':15} {duration_stats['max']:>12.2f} {'ms':>8}")
        print(f"{'P90':15} {duration_stats['p90']:>12.2f} {'ms':>8}")
        print(f"{'P95':15} {duration_stats['p95']:>12.2f} {'ms':>8}")
        print(f"{'P99':15} {duration_stats['p99']:>12.2f} {'ms':>8}")
        print()

    # 대기 시간 통계
    waiting_stats = calculate_stats(http_waiting)
    if waiting_stats:
        print("⏳ HTTP 대기 시간 (http_req_waiting)")
        print("-" * 60)
        print(f"평균: {waiting_stats['avg']:.2f}ms")
        print(f"P95: {waiting_stats['p95']:.2f}ms")
        print(f"P99: {waiting_stats['p99']:.2f}ms")
        print()

    # 시나리오별 통계
    if scenario_data:
        print("📋 시나리오별 응답 시간")
        print("-" * 60)
        print(f"{'시나리오':<20} {'요청수':>10} {'평균(ms)':>12} {'P95(ms)':>12} {'P99(ms)':>12}")
        print("-" * 60)

        for scenario, data in sorted(scenario_data.items()):
            stats = calculate_stats(data['durations'])
            if stats:
                print(f"{scenario:<20} {stats['count']:>10,} {stats['avg']:>12.2f} {stats['p95']:>12.2f} {stats['p99']:>12.2f}")
        print()

    # 상품별 통계
    if product_data:
        print("🛍️  상품별 응답 시간")
        print("-" * 60)
        print(f"{'상품ID':<10} {'요청수':>10} {'평균(ms)':>12} {'P95(ms)':>12} {'P99(ms)':>12}")
        print("-" * 60)

        for product_id, data in sorted(product_data.items(), key=lambda x: int(x[0]) if x[0].isdigit() else 0):
            stats = calculate_stats(data['durations'])
            if stats:
                print(f"{product_id:<10} {stats['count']:>10,} {stats['avg']:>12.2f} {stats['p95']:>12.2f} {stats['p99']:>12.2f}")
        print()

    print("="*60 + "\n")

def generate_csv(metrics, output_file):
    """CSV 파일 생성"""
    import csv

    with open(output_file, 'w', newline='') as f:
        writer = csv.writer(f)
        writer.writerow(['timestamp', 'metric', 'value', 'scenario', 'productId'])

        for metric_name, data_points in metrics.items():
            for point in data_points:
                tags = point.get('tags', {})
                writer.writerow([
                    point.get('time', ''),
                    metric_name,
                    point.get('value', 0),
                    tags.get('scenario', ''),
                    tags.get('productId', '')
                ])

    print(f"✅ CSV 파일 생성: {output_file}")

def generate_html(metrics, scenario_data, product_data, output_file):
    """간단한 HTML 리포트 생성"""
    http_durations = [m['value'] for m in metrics.get('http_req_duration', [])]
    duration_stats = calculate_stats(http_durations)

    html = f"""
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>K6 테스트 결과</title>
    <style>
        body {{ font-family: Arial, sans-serif; margin: 20px; background: #f5f5f5; }}
        .container {{ max-width: 1200px; margin: 0 auto; background: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }}
        h1 {{ color: #333; border-bottom: 3px solid #667eea; padding-bottom: 10px; }}
        table {{ width: 100%; border-collapse: collapse; margin: 20px 0; }}
        th {{ background: #667eea; color: white; padding: 12px; text-align: left; }}
        td {{ padding: 10px; border-bottom: 1px solid #ddd; }}
        .metric {{ display: inline-block; margin: 10px 20px 10px 0; padding: 15px 20px; background: #f0f0f0; border-radius: 5px; }}
        .metric-label {{ font-size: 12px; color: #666; }}
        .metric-value {{ font-size: 24px; font-weight: bold; color: #333; }}
    </style>
</head>
<body>
    <div class="container">
        <h1>K6 성능 테스트 결과</h1>

        <div>
            <div class="metric">
                <div class="metric-label">총 요청 수</div>
                <div class="metric-value">{len(metrics.get('http_reqs', [])):,}</div>
            </div>
            <div class="metric">
                <div class="metric-label">평균 응답시간</div>
                <div class="metric-value">{duration_stats['avg']:.2f}ms</div>
            </div>
            <div class="metric">
                <div class="metric-label">P95 응답시간</div>
                <div class="metric-value">{duration_stats['p95']:.2f}ms</div>
            </div>
        </div>

        <h2>시나리오별 성능</h2>
        <table>
            <thead>
                <tr>
                    <th>시나리오</th>
                    <th>요청 수</th>
                    <th>평균 (ms)</th>
                    <th>P95 (ms)</th>
                    <th>P99 (ms)</th>
                </tr>
            </thead>
            <tbody>
"""

    for scenario, data in sorted(scenario_data.items()):
        stats = calculate_stats(data['durations'])
        if stats:
            html += f"""
                <tr>
                    <td>{scenario}</td>
                    <td>{stats['count']:,}</td>
                    <td>{stats['avg']:.2f}</td>
                    <td>{stats['p95']:.2f}</td>
                    <td>{stats['p99']:.2f}</td>
                </tr>
"""

    html += """
            </tbody>
        </table>
    </div>
</body>
</html>
"""

    with open(output_file, 'w') as f:
        f.write(html)

    print(f"✅ HTML 리포트 생성: {output_file}")

def main():
    parser = argparse.ArgumentParser(description='K6 JSON 결과 분석')
    parser.add_argument('json_file', help='K6 JSON 결과 파일')
    parser.add_argument('--csv', action='store_true', help='CSV 파일 생성')
    parser.add_argument('--html', action='store_true', help='HTML 리포트 생성')

    args = parser.parse_args()

    if not args.json_file:
        print("사용법: python analyze.py <json-file> [--html] [--csv]")
        sys.exit(1)

    # JSON 파싱
    metrics, scenario_data, product_data = parse_k6_json(args.json_file)

    # 콘솔 리포트 출력
    print_report(metrics, scenario_data, product_data)

    # CSV 생성
    if args.csv:
        csv_file = args.json_file.replace('.json', '.csv')
        generate_csv(metrics, csv_file)

    # HTML 생성
    if args.html:
        html_file = args.json_file.replace('.json', '.html')
        generate_html(metrics, scenario_data, product_data, html_file)

if __name__ == '__main__':
    main()
