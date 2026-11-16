#!/usr/bin/env python3
"""시나리오 B 결과 그래프를 PNG로 만든다 (로컬 Prometheus와 results/ 파일을 읽음)

필요한 것: matplotlib (python3 -m venv .venv && .venv/bin/pip install matplotlib)
실행(backend/에서):
  python3 loadtest/scenario-b/graph.py b1 "2026-11-16 15:48:31" <출력 폴더>   # B-1 시작 시각
  python3 loadtest/scenario-b/graph.py b2 "2026-11-16 17:29:45" <출력 폴더>   # B-2 시작 시각
  python3 loadtest/scenario-b/graph.py b3 loadtest/scenario-b/results/2026-11-16 <출력 폴더>   # b3-1·2·3.txt 앞부분
  python3 loadtest/scenario-b/graph.py b4 loadtest/scenario-b/results/2026-11-16 <출력 폴더>   # b4-*.txt 앞부분

Prometheus 기본 보관 기간(15일)이 지나면 b1·b2는 다시 그릴 수 없다.
"""
import glob
import json
import os
import re
import statistics
import sys
import urllib.parse
import urllib.request
from datetime import datetime

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
from matplotlib import font_manager  # noqa: E402

PROM = os.environ.get("PROM_URL", "http://localhost:9095")
APP = 'application="cash-service"'
PAY = APP + ',uri="/internal/v1/cash/payments/{orderId}"'
PAYOUT = APP + ',uri="/internal/v1/cash/payouts/{payoutId}"'
BANK = APP + ',uri="/fakebank/v1/transfers"'

# 맥 기본 한글 글꼴. 없으면 기본 글꼴로 그린다(한글이 네모로 보일 수 있음)
for path in ["/System/Library/Fonts/AppleSDGothicNeo.ttc"]:
    if os.path.exists(path):
        font_manager.fontManager.addfont(path)
        plt.rcParams["font.family"] = font_manager.FontProperties(fname=path).get_name()
plt.rcParams["axes.unicode_minus"] = False


def series(query, start, end, step=5):
    url = PROM + "/api/v1/query_range?" + urllib.parse.urlencode(
        {"query": query, "start": start, "end": end, "step": step})
    result = json.load(urllib.request.urlopen(url))["data"]["result"]
    if not result:
        return [], []
    values = result[0]["values"]
    xs = [float(t) - start for t, _ in values]
    ys = [float(v) if v not in ("NaN", "+Inf") else float("nan") for _, v in values]
    return xs, ys


def p95(selector):
    return f"histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{{{selector}}}[30s]))) * 1000"


def mark(ax, edges, labels):
    for x in edges:
        ax.axvline(x, color="gray", linewidth=0.6, linestyle=":")
    for (a, b), label in zip(zip(edges, edges[1:]), labels):
        ax.text((a + b) / 2, 0.97, label, transform=ax.get_xaxis_transform(), ha="center", va="top", fontsize=8,
                color="dimgray")


def b1(start_text, out):
    start = datetime.strptime(start_text, "%Y-%m-%d %H:%M:%S").timestamp()
    edges = [0, 30, 150, 270, 390, 510, 630]
    labels = ["워밍업", "초당 20", "50", "100", "200", "400"]
    end = start + edges[-1]
    fig, axes = plt.subplots(4, 1, figsize=(10, 11), sharex=True)
    panels = [
        ("결제 처리 (초당)", [(f"sum(rate(http_server_requests_seconds_count{{{PAY}}}[30s]))", "처리", None)]),
        ("서버 결제 응답 p95 (ms, 로그)", [(p95(PAY), "p95", None)]),
        ("DB 연결 (개)", [(f"hikaricp_connections_active{{{APP}}}", "사용 중", None),
                         (f"hikaricp_connections_pending{{{APP}}}", "기다리는 요청", None)]),
        ("MySQL 행 락 대기 (초당)", [("rate(mysql_global_status_innodb_row_lock_waits[30s])", "대기", None)]),
    ]
    for ax, (title, lines) in zip(axes, panels):
        for query, label, _ in lines:
            xs, ys = series(query, start, end)
            ax.plot(xs, ys, label=label)
        ax.set_title(title, loc="left", fontsize=10)
        mark(ax, edges, labels)
        if len(lines) > 1:
            ax.legend(fontsize=8, loc="upper left")
    axes[1].set_yscale("log")
    axes[-1].set_xlabel("시작 뒤 (초)")
    fig.suptitle("B-1 결제 처리량 한계: 초당 결제를 단계마다 올림", fontsize=12)
    fig.tight_layout()
    save(fig, out, "b1-payment-ramp.png")


def b2(start_text, out):
    start = datetime.strptime(start_text, "%Y-%m-%d %H:%M:%S").timestamp()
    edges = [0, 30, 90, 150, 210]
    labels = ["워밍업", "앞", "가운데 (정산 섞음)", "뒤"]
    end = start + edges[-1]
    fig, axes = plt.subplots(4, 1, figsize=(10, 11), sharex=True)
    rate = "sum(rate(http_server_requests_seconds_count{{{0}}}[30s]))"
    avg_ms = ("sum(rate(http_client_requests_seconds_sum{{{0}}}[30s])) / "
              "sum(rate(http_client_requests_seconds_count{{{0}}}[30s])) * 1000")
    panels = [
        ("처리 (초당)", [(rate.format(PAY), "결제"), (rate.format(PAYOUT), "정산 지급")]),
        ("서버 응답 p95 (ms)", [(p95(PAY), "결제"), (p95(PAYOUT), "정산 지급")]),
        ("cash → 가짜 은행 호출 평균 (ms)", [(avg_ms.format(BANK), "은행 호출")]),
        ("MySQL 행 락 대기 (초당)", [("rate(mysql_global_status_innodb_row_lock_waits[30s])", "대기")]),
    ]
    for ax, (title, lines) in zip(axes, panels):
        for query, label in lines:
            xs, ys = series(query, start, end)
            ax.plot(xs, ys, label=label)
        ax.set_title(title, loc="left", fontsize=10)
        mark(ax, edges, labels)
        if len(lines) > 1:
            ax.legend(fontsize=8, loc="upper left")
    for ax in axes:
        ax.set_ylim(bottom=0)
    axes[-1].set_xlabel("시작 뒤 (초)")
    fig.suptitle("B-2 결제 중 정산 지급 몰림: 가운데 1분만 정산 초당 20건", fontsize=12)
    fig.tight_layout()
    save(fig, out, "b2-payment-with-payout.png")


def to_ms(text):
    return float(text[:-2]) if text.endswith("ms") else float(text[:-1]) * 1000


def b3(prefix, out):
    runs = {}
    for path in sorted(glob.glob(prefix + "-b3-*.txt")):
        for cards, value in re.findall(r"\{ cards:(\d) \}\.+: .*?p\(95\)=([\d.]+m?s)", open(path).read()):
            runs.setdefault(int(cards), []).append(to_ms(value))
    cards = sorted(runs)
    medians = [statistics.median(runs[c]) for c in cards]
    fig, ax = plt.subplots(figsize=(7, 4.5))
    ax.bar([str(c) + "장" for c in cards], medians, color="#4c72b0", label="p95 중앙값 (3회)")
    for i, c in enumerate(cards):
        ax.scatter([i] * len(runs[c]), runs[c], color="black", s=12, zorder=3,
                   label="회차별 p95" if i == 0 else None)
        ax.text(i, medians[i] + 30, f"{medians[i]:,.0f}ms", ha="center", fontsize=9)
    ax.axhline(1000, color="red", linestyle="--", linewidth=1, label="기준 1초 (추천값)")
    ax.set_ylabel("환급 응답 p95 (ms)")
    ax.set_title("B-3 카드 장수별 전액 환급 응답 시간 (PG 결제취소 300ms 가정)", fontsize=11)
    ax.legend(fontsize=8)
    fig.tight_layout()
    save(fig, out, "b3-withdrawal.png")


def b4(prefix, out):
    rows = {}
    for path in sorted(glob.glob(prefix + "-b4-*.txt")):
        n = int(re.search(r"-b4-(\d+)-", path).group(1))
        text = open(path).read()
        took = float(re.search(r"걸린 시간: ([\d.]+)초", text).group(1))
        # 시드 표(대상 수) 뒤에 나오는 「cash_문장_수 | DB_시간_ms」 표에서 읽는다
        totals = text[text.index("cash_문장_수"):]
        sql, db_ms = re.search(r"\|\s+(\d+) \|\s+([\d.]+) \|", totals).groups()
        rows.setdefault(n, []).append((took, int(sql), float(db_ms) / 1000))
    ns = sorted(rows)
    took = [statistics.median(r[0] for r in rows[n]) for n in ns]
    sql = [statistics.median(r[1] for r in rows[n]) for n in ns]
    db = [statistics.median(r[2] for r in rows[n]) for n in ns]
    labels = [f"{n:,}명" for n in ns]
    fig, (left, right) = plt.subplots(1, 2, figsize=(11, 4.5))
    left.bar(labels, sql, color="#4c72b0")
    for i, v in enumerate(sql):
        left.text(i, v, f"{v:,.0f}", ha="center", va="bottom", fontsize=9)
    left.set_title("cash 표 SQL 수 (= 대상 × 2 + 4)", fontsize=10)
    right.bar(labels, db, color="#55a868", label="DB 시간")
    right.bar(labels, [t - d for t, d in zip(took, db)], bottom=db, color="#c44e52", label="DB 밖 시간")
    for i, v in enumerate(took):
        right.text(i, v, f"{v:.2f}초", ha="center", va="bottom", fontsize=9)
    right.set_title("걸린 시간 (10,000명은 3회 중앙값)", fontsize=10)
    right.legend(fontsize=8)
    fig.suptitle("B-4 카드 취소 기한 알림 배치", fontsize=12)
    fig.tight_layout()
    save(fig, out, "b4-notice.png")


def save(fig, out, name):
    os.makedirs(out, exist_ok=True)
    path = os.path.join(out, name)
    fig.savefig(path, dpi=110)
    print("만듦", path)


if __name__ == "__main__":
    if len(sys.argv) != 4 or sys.argv[1] not in ("b1", "b2", "b3", "b4"):
        print(__doc__)
        sys.exit(1)
    globals()[sys.argv[1]](sys.argv[2], sys.argv[3])
