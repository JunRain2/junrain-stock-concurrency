#!/usr/bin/env python3
"""
A/B 실행 결과(k6 summary JSON) 두 개를 읽어 비교 HTML 한 장을 만든다.

    python3 k6-tests/reserve/report.py

의존성 없음. 결과 파일이 하나만 있어도 그것만 그린다.
"""

import json
import math
import os
import re
import sys
from datetime import datetime

RESULTS_DIR = "k6-tests/results/reserve"
OUTPUT = os.path.join(RESULTS_DIR, "report.html")

SLO_P95_MS = 200
P99_MIN_SAMPLES = 10000

# common.js 의 STEP_SEC 과 같아야 한다. 구간별 기대 표본 수 = rate * STEP_SEC.
STEP_SEC = 40
# 기대 표본의 이 비율에 못 미치면 k6 가 부하를 못 낸 구간이다.
SAMPLE_TOLERANCE = 0.95

SERIES = [
    ("A", "단일 행 경합", "a-single-row.json", "#d1495b"),
    ("B", "경합 분산", "b-spread.json", "#2e86ab"),
]

RATE_KEY = re.compile(r"^http_req_duration\{rate:(\d+)\}$")


def load(path):
    """k6 summary JSON에서 구간별 값과 유효성 지표를 뽑는다."""
    if not os.path.exists(path):
        return None

    with open(path, encoding="utf-8") as f:
        data = json.load(f)

    metrics = data.get("metrics", {})

    steps = []
    for key, metric in metrics.items():
        m = RATE_KEY.match(key)
        if not m:
            continue
        v = metric.get("values", {})
        steps.append(
            {
                "rate": int(m.group(1)),
                "count": int(v.get("count", 0)),
                "p95": v.get("p(95)", 0.0),
                "p99": v.get("p(99)", 0.0),
                "med": v.get("med", 0.0),
                "max": v.get("max", 0.0),
            }
        )
    steps.sort(key=lambda s: s["rate"])

    for s in steps:
        s["p99_valid"] = s["count"] >= P99_MIN_SAMPLES
        # 표본이 기대치에 못 미치면 그 구간은 서버가 아니라 부하 생성기를 잰 것이다.
        s["complete"] = s["count"] >= s["rate"] * STEP_SEC * SAMPLE_TOLERANCE
        s["pass"] = s["complete"] and s["p95"] < SLO_P95_MS

    passed = [s["rate"] for s in steps if s["pass"]]
    dropped = int(metrics.get("dropped_iterations", {}).get("values", {}).get("count", 0))
    failed_rate = metrics.get("http_req_failed", {}).get("values", {}).get("rate", 0.0)

    incomplete_steps = [s for s in steps if not s["complete"]]

    warns = []
    # dropped_iterations 는 워밍업까지 포함한 전역 카운터다. 측정 구간이 모두 완결이면
    # 버려진 것은 워밍업 몫이고, 워밍업은 어차피 집계에서 제외한다.
    if dropped > 0 and incomplete_steps:
        warns.append(f"dropped_iterations={dropped} — k6가 부하를 못 냈다. 아래 구간을 다시 잰다")
    if failed_rate > 0.01:
        warns.append(f"http_req_failed={failed_rate * 100:.2f}% — 5xx/타임아웃 혼입. 지연이 좋아 보인다")
    incomplete = [s["rate"] for s in incomplete_steps]
    if incomplete:
        warns.append(
            f"표본 부족 구간 {incomplete} — 목표 유입량을 채우지 못했다. 그 구간은 서버가 아니라 "
            f"부하 생성기를 측정한 것이므로 값을 읽지 않는다"
        )
    if steps and all(s["pass"] for s in steps):
        warns.append("전 구간 통과 — 처리 한계가 최고 단계보다 위에 있다. 도착률을 올리고 다시 측정한다")
    if steps and not any(s["pass"] for s in steps):
        warns.append("전 구간 실패 — 처리 한계가 최저 단계보다 아래에 있다. 도착률을 내리고 다시 측정한다")

    status_counts = {}
    for status in (200, 400, 409):
        v = metrics.get(f"http_req_duration{{status:{status}}}", {}).get("values")
        if v:
            status_counts[status] = int(v.get("count", 0))

    total_status = sum(status_counts.values())
    if total_status and status_counts.get(409, 0) / total_status > 0.01:
        warns.append(
            f"409 응답 {status_counts[409]:,}건 ({status_counts[409] / total_status * 100:.1f}%) — "
            f"락 획득 실패는 빠르게 끝나므로 느린 요청이 빠져나가 p95가 실제보다 좋아 보인다"
        )

    return {
        "steps": steps,
        "status": status_counts,
        "limit": max(passed) if passed else None,
        "warns": warns,
        "mtime": datetime.fromtimestamp(os.path.getmtime(path)).strftime("%Y-%m-%d %H:%M"),
    }


def svg_chart(loaded, width=760, height=380):
    """rate 대비 p95. y축 로그 스케일 — 100ms와 5000ms가 한 화면에 들어와야 한다."""
    pad_l, pad_r, pad_t, pad_b = 62, 20, 20, 46
    plot_w = width - pad_l - pad_r
    plot_h = height - pad_t - pad_b

    rates = sorted({s["rate"] for d in loaded.values() for s in d["steps"]})
    values = [s["p95"] for d in loaded.values() for s in d["steps"]] + [SLO_P95_MS]
    if not rates or not values:
        return "<p class='muted'>그릴 데이터가 없다.</p>"

    lo = max(1.0, min(values) * 0.6)
    hi = max(values) * 1.6

    def x_of(rate):
        if len(rates) == 1:
            return pad_l + plot_w / 2
        return pad_l + plot_w * rates.index(rate) / (len(rates) - 1)

    def y_of(val):
        val = max(val, lo)
        t = (math.log10(val) - math.log10(lo)) / (math.log10(hi) - math.log10(lo))
        return pad_t + plot_h * (1 - t)

    out = [f'<svg viewBox="0 0 {width} {height}" class="chart" role="img">']

    # y 눈금 (10의 거듭제곱)
    e = math.floor(math.log10(lo))
    while 10**e <= hi:
        for mult in (1, 2, 5):
            tick = mult * 10**e
            if lo <= tick <= hi:
                y = y_of(tick)
                out.append(f'<line x1="{pad_l}" y1="{y:.1f}" x2="{width - pad_r}" y2="{y:.1f}" class="grid"/>')
                label = f"{tick / 1000:g}s" if tick >= 1000 else f"{tick:g}ms"
                out.append(f'<text x="{pad_l - 8}" y="{y + 4:.1f}" class="ylab">{label}</text>')
        e += 1

    # SLO 선
    y_slo = y_of(SLO_P95_MS)
    out.append(f'<line x1="{pad_l}" y1="{y_slo:.1f}" x2="{width - pad_r}" y2="{y_slo:.1f}" class="slo"/>')
    out.append(f'<text x="{width - pad_r}" y="{y_slo - 7:.1f}" class="slolab">SLO {SLO_P95_MS}ms</text>')

    # x 눈금
    for rate in rates:
        x = x_of(rate)
        out.append(f'<text x="{x:.1f}" y="{height - pad_b + 20}" class="xlab">{rate}</text>')
    out.append(
        f'<text x="{pad_l + plot_w / 2:.1f}" y="{height - 8}" class="axis">도착률 (RPS)</text>'
    )

    # 시리즈
    for key, label, _, color in SERIES:
        d = loaded.get(key)
        if not d or not d["steps"]:
            continue
        pts = [(x_of(s["rate"]), y_of(s["p95"])) for s in d["steps"]]
        path = " ".join(("M" if i == 0 else "L") + f"{x:.1f},{y:.1f}" for i, (x, y) in enumerate(pts))
        out.append(f'<path d="{path}" fill="none" stroke="{color}" stroke-width="2.5"/>')
        for (x, y), s in zip(pts, d["steps"]):
            fill = color if s["pass"] else "#fff"
            out.append(
                f'<circle cx="{x:.1f}" cy="{y:.1f}" r="5" fill="{fill}" stroke="{color}" stroke-width="2.5"/>'
            )
            out.append(f'<title>{key} {s["rate"]} RPS — p95 {s["p95"]:.0f}ms</title>')

    out.append("</svg>")
    return "\n".join(out)


def shared_rows(a, b):
    """A와 B에 모두 있는 도착률에서 p95를 나란히 놓는다.

    처리 한계는 단계 간격만큼의 해상도밖에 없어 두 시나리오가 같은 값으로 나오기 쉽다.
    같은 유입량에서의 p95 비율은 연속적이므로 경합 비용에 훨씬 민감하다.
    """
    if not a or not b:
        return []

    by_rate = {s["rate"]: s for s in b["steps"]}
    out = []
    for s in a["steps"]:
        t = by_rate.get(s["rate"])
        # 표본이 모자란 구간은 서버가 아니라 부하 생성기를 잰 것이라 비교에서 뺀다.
        if not t or not s["complete"] or not t["complete"] or t["p95"] <= 0:
            continue
        out.append({"rate": s["rate"], "a": s["p95"], "b": t["p95"], "ratio": s["p95"] / t["p95"]})
    return out


def rows(key, d):
    html = []
    for s in d["steps"]:
        p99 = f'{s["p99"]:.0f}ms' if s["p99_valid"] else '<span class="muted">표본부족</span>'
        if not s["complete"]:
            verdict = '<span class="fail">무효</span>'
        else:
            verdict = '<span class="pass">PASS</span>' if s["pass"] else '<span class="fail">FAIL</span>'
        html.append(
            f"<tr><td>{key}</td><td class='num'>{s['rate']}</td><td class='num'>{s['count']:,}</td>"
            f"<td class='num'>{s['med']:.0f}ms</td><td class='num'>{s['p95']:.0f}ms</td>"
            f"<td class='num'>{p99}</td><td class='num'>{s['max']:.0f}ms</td><td>{verdict}</td></tr>"
        )
    return "".join(html)


def main():
    loaded = {}
    for key, _, filename, _ in SERIES:
        d = load(os.path.join(RESULTS_DIR, filename))
        if d:
            loaded[key] = d

    if not loaded:
        print(f"결과 JSON이 없다: {RESULTS_DIR}/  — 먼저 bash k6-tests/reserve/run.sh", file=sys.stderr)
        return 1

    a, b = loaded.get("A"), loaded.get("B")

    shared = shared_rows(a, b)
    if shared:
        top = shared[-1]
        cost = f"{top['ratio']:.1f}배"
        cost_note = f"{top['rate']:,} RPS에서 A {top['a']:.0f}ms vs B {top['b']:.0f}ms"
    else:
        cost = "—"
        cost_note = "A와 B에 공통인 유효 구간이 있어야 계산된다."

    cards = []
    for key, label, _, color in SERIES:
        d = loaded.get(key)
        if not d:
            continue
        limit = f"{d['limit']:,} RPS" if d["limit"] else "없음"
        cards.append(
            f'<div class="card" style="border-left-color:{color}">'
            f'<div class="card-label">{key} · {label}</div>'
            f'<div class="card-value">{limit}</div>'
            f'<div class="card-note">p95 &lt; {SLO_P95_MS}ms 통과 최대 rate · {d["mtime"]}</div></div>'
        )

    cards.append(
        f'<div class="card cost"><div class="card-label">경합 비용</div>'
        f'<div class="card-value">{cost}</div><div class="card-note">{cost_note}</div></div>'
    )

    if shared:
        srows = "".join(
            f"<tr><td class='num'>{r['rate']:,}</td><td class='num'>{r['a']:.0f}ms</td>"
            f"<td class='num'>{r['b']:.0f}ms</td><td class='num'><strong>{r['ratio']:.1f}배</strong></td></tr>"
            for r in shared
        )
        shared_html = (
            "<h2>같은 유입량에서의 경합 비용</h2>"
            "<table><thead><tr><th class='num'>rate</th><th class='num'>A p95</th>"
            "<th class='num'>B p95</th><th class='num'>A / B</th></tr></thead>"
            f"<tbody>{srows}</tbody></table>"
            "<p class='muted' style='font-size:13px'>처리 한계는 단계 간격만큼의 해상도밖에 없어 "
            "두 시나리오가 같은 값으로 나올 수 있다. 같은 유입량에서의 p95 비율이 경합 비용을 더 민감하게 드러낸다. "
            "표본이 모자란 구간은 제외했다.</p>"
        )
    else:
        shared_html = ""

    status_html = ""
    srows = []
    for key, label, _, _ in SERIES:
        d = loaded.get(key)
        if not d or not d.get("status"):
            continue
        total = sum(d["status"].values()) or 1
        cells = "".join(
            f"<td class='num'>{d['status'].get(st, 0):,}<br><span class='muted' style='font-size:11px'>"
            f"{d['status'].get(st, 0) / total * 100:.1f}%</span></td>"
            for st in (200, 400, 409)
        )
        srows.append(f"<tr><td>{key} · {label}</td>{cells}</tr>")
    if srows:
        status_html = (
            "<h2>응답 상태</h2><table><thead><tr><th></th><th class='num'>200</th>"
            "<th class='num'>400 재고부족</th><th class='num'>409 락 실패</th></tr></thead>"
            f"<tbody>{''.join(srows)}</tbody></table>"
            "<p class='muted' style='font-size:13px'>워밍업 포함 전체 집계. 400과 409는 정상 응답이라 "
            "실패율에서 제외되지만, 이 경로는 정상 경로보다 빨리 끝나므로 비중이 커지면 p95가 좋아 보인다.</p>"
        )

    warn_html = ""
    for key, _, _, _ in SERIES:
        d = loaded.get(key)
        if d and d["warns"]:
            items = "".join(f"<li>{w}</li>" for w in d["warns"])
            warn_html += f"<div class='warn'><strong>{key}</strong><ul>{items}</ul></div>"
    if warn_html:
        warn_html = (
            "<h2>유효성 경고</h2><p class='muted'>하나라도 뜨면 그 실행은 버린다.</p>" + warn_html
        )

    table = "".join(rows(k, loaded[k]) for k, _, _, _ in SERIES if k in loaded)

    html = f"""<!doctype html>
<html lang="ko"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>재고 점유 부하테스트 결과</title>
<style>
  :root {{ --fg:#1a1a1a; --muted:#6b7280; --line:#e5e7eb; --bg:#fff; --panel:#f9fafb; }}
  * {{ box-sizing:border-box; }}
  body {{ margin:0; padding:32px 20px; background:var(--panel); color:var(--fg);
    font:15px/1.6 -apple-system,BlinkMacSystemFont,"Apple SD Gothic Neo",sans-serif; }}
  .wrap {{ max-width:860px; margin:0 auto; background:var(--bg); padding:36px;
    border-radius:10px; box-shadow:0 1px 3px rgba(0,0,0,.08); }}
  h1 {{ margin:0 0 4px; font-size:24px; }}
  h2 {{ margin:36px 0 12px; font-size:17px; }}
  .sub {{ color:var(--muted); margin:0 0 28px; font-size:14px; }}
  .cards {{ display:grid; grid-template-columns:repeat(auto-fit,minmax(200px,1fr)); gap:14px; }}
  .card {{ background:var(--panel); padding:16px 18px; border-radius:6px; border-left:4px solid var(--muted); }}
  .card.cost {{ border-left-color:#1a1a1a; }}
  .card-label {{ font-size:13px; color:var(--muted); }}
  .card-value {{ font-size:26px; font-weight:700; margin:4px 0; }}
  .card-note {{ font-size:12px; color:var(--muted); }}
  .chart-box {{ overflow-x:auto; }}
  .chart {{ width:100%; min-width:600px; height:auto; display:block; }}
  .grid {{ stroke:var(--line); stroke-width:1; }}
  .slo {{ stroke:#16a34a; stroke-width:1.5; stroke-dasharray:5 4; }}
  .slolab {{ fill:#16a34a; font-size:11px; text-anchor:end; }}
  .ylab {{ fill:var(--muted); font-size:11px; text-anchor:end; }}
  .xlab {{ fill:var(--muted); font-size:12px; text-anchor:middle; }}
  .axis {{ fill:var(--muted); font-size:12px; text-anchor:middle; }}
  .legend {{ display:flex; gap:20px; margin-top:10px; font-size:13px; color:var(--muted); }}
  .swatch {{ display:inline-block; width:11px; height:11px; border-radius:50%; margin-right:6px; }}
  table {{ border-collapse:collapse; width:100%; font-size:14px; }}
  th, td {{ border-bottom:1px solid var(--line); padding:9px 10px; text-align:left; }}
  th {{ font-size:12px; color:var(--muted); font-weight:600; }}
  td.num {{ text-align:right; font-variant-numeric:tabular-nums; }}
  .pass {{ color:#16a34a; font-weight:600; }}
  .fail {{ color:#dc2626; font-weight:600; }}
  .muted {{ color:var(--muted); }}
  .warn {{ background:#fef2f2; border-left:4px solid #dc2626; padding:12px 16px;
    border-radius:4px; margin-bottom:10px; font-size:14px; }}
  .warn ul {{ margin:6px 0 0; padding-left:18px; }}
  footer {{ margin-top:32px; padding-top:16px; border-top:1px solid var(--line);
    font-size:13px; color:var(--muted); }}
  code {{ background:var(--panel); padding:1px 5px; border-radius:3px; font-size:13px; }}
</style></head>
<body><div class="wrap">
  <h1>재고 점유 부하테스트 결과</h1>
  <p class="sub">생성 {datetime.now().strftime("%Y-%m-%d %H:%M")} · 설계 근거는 <code>docs/load_test/재고점유.md</code></p>

  <div class="cards">{"".join(cards)}</div>

  {shared_html}

  {status_html}

  <h2>도착률 대비 p95</h2>
  <div class="chart-box">{svg_chart(loaded)}</div>
  <div class="legend">
    <span><span class="swatch" style="background:#d1495b"></span>A 단일 행 경합</span>
    <span><span class="swatch" style="background:#2e86ab"></span>B 경합 분산</span>
    <span class="muted">속 빈 점 = SLO 초과</span>
  </div>

  <h2>구간별</h2>
  <table>
    <thead><tr><th></th><th class="num">rate</th><th class="num">요청수</th><th class="num">중앙값</th>
    <th class="num">p95</th><th class="num">p99</th><th class="num">최대</th><th>판정</th></tr></thead>
    <tbody>{table}</tbody>
  </table>
  <p class="muted" style="font-size:13px">p99는 표본 10,000건 이상인 구간에서만 읽는다. 그 아래는 상위 몇 건이 값을 정한다.</p>

  {warn_html}

  <footer>
    이 장비는 앱·부하 생성기·DB가 한 대에 있다. 절대 처리량은 인용할 수 없고,
    동일 조건에서의 비율만 유효하다. 결과 옆에 환경 스탬프를 남길 것.
  </footer>
</div></body></html>
"""

    os.makedirs(RESULTS_DIR, exist_ok=True)
    with open(OUTPUT, "w", encoding="utf-8") as f:
        f.write(html)

    print(f"✓ {OUTPUT}")
    for key, _, _, _ in SERIES:
        d = loaded.get(key)
        if d:
            print(f"  {key}: 처리 한계 {d['limit'] or '없음'} RPS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
