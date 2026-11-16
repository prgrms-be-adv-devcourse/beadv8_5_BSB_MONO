#!/usr/bin/env python3
"""시나리오 B 결과 그래프를 PNG로 만든다 (Prometheus와 결과 파일을 읽음)

필요한 것: matplotlib (python3 -m venv .venv && .venv/bin/pip install matplotlib)
실행(backend/에서):
  python3 loadtest/scenario-b/graph.py b1 "2026-11-16 15:48:31" <출력 폴더>   # B-1 한계(도착 속도 올림) 시작 시각
  python3 loadtest/scenario-b/graph.py b1c "2026-11-17 10:00:00" <출력 폴더>  # B-1 동시 4·8개 시작 시각
  python3 loadtest/scenario-b/graph.py b2 "2026-11-16 17:29:45" <출력 폴더>   # B-2 시작 시각
  python3 loadtest/scenario-b/graph.py b3 loadtest/scenario-b/results/2026-11-16 <출력 폴더>   # b3-*.json 앞부분
  python3 loadtest/scenario-b/graph.py b4 loadtest/scenario-b/results/2026-11-16 <출력 폴더>   # b4-*.txt 앞부분
  python3 loadtest/scenario-b/graph.py b5 "2026-11-17 10:30:00" <출력 폴더>   # B-5 충전 시작 시각

환경 변수
  PROM_URL     Prometheus 주소 (기본 http://localhost:9095)
  PROM_WINDOW  rate 구간 (기본 30s. 15초마다 모으면 그 두 배는 돼야 값이 나온다. 1초마다 모으는 측정 컨테이너는 5s)
  STAGE_SEC    b1c 단계 길이 (기본 120), PHASE_SEC b2 구간 길이 (기본 60), DURATION_SEC b5 길이 (기본 600)
Prometheus 기본 보관 기간(15일)이 지나면 b1·b1c·b2·b5는 다시 그릴 수 없다.
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
W = os.environ.get("PROM_WINDOW", "30s")
APP = 'application="cash-service"'
PAY = APP + ',uri="/internal/v1/cash/payments/{orderId}"'
PAYOUT = APP + ',uri="/internal/v1/cash/payouts/{payoutId}"'
BANK = APP + ',uri="/fakebank/v1/transfers"'
CHARGE = APP + ',uri="/api/v1/cash/charges/confirm"'
ROW_LOCK_WAITS = f"rate(mysql_global_status_innodb_row_lock_waits[{W}])"
DB_ACTIVE = f"hikaricp_connections_active{{{APP}}}"
DB_PENDING = f"hikaricp_connections_pending{{{APP}}}"

# 맥 기본 한글 글꼴, 리눅스(Actions)는 Noto CJK. 없으면 기본 글꼴로 그린다(한글이 네모로 보일 수 있음)
for path in ["/System/Library/Fonts/AppleSDGothicNeo.ttc", "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"]:
    if os.path.exists(path):
        font_manager.fontManager.addfont(path)
        plt.rcParams["font.family"] = font_manager.FontProperties(fname=path).get_name()
        break
plt.rcParams["axes.unicode_minus"] = False


def rate(selector):
    return f"sum(rate(http_server_requests_seconds_count{{{selector}}}[{W}]))"


def quantile(selector, q):
    return f"histogram_quantile({q}, sum by (le) (rate(http_server_requests_seconds_bucket{{{selector}}}[{W}]))) * 1000"


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


def mark(ax, edges, labels):
    for x in edges:
        ax.axvline(x, color="gray", linewidth=0.6, linestyle=":")
    for (a, b), label in zip(zip(edges, edges[1:]), labels):
        ax.text((a + b) / 2, 0.97, label, transform=ax.get_xaxis_transform(), ha="center", va="top", fontsize=8,
                color="dimgray")


def timeline(start_text, edges, labels, panels, title, name, out, log_panels=(), from_zero=False):
    """panels: [(제목, [(쿼리, 선 이름), ...]), ...] 를 시작 시각부터 edges[-1]초까지 위아래로 그린다"""
    start = datetime.strptime(start_text, "%Y-%m-%d %H:%M:%S").timestamp()
    end = start + edges[-1]
    fig, axes = plt.subplots(len(panels), 1, figsize=(10, 2.75 * len(panels)), sharex=True)
    for i, (ax, (panel_title, lines)) in enumerate(zip(axes, panels)):
        for query, label in lines:
            xs, ys = series(query, start, end)
            ax.plot(xs, ys, label=label)
        ax.set_title(panel_title, loc="left", fontsize=10)
        mark(ax, edges, labels)
        if len(lines) > 1:
            ax.legend(fontsize=8, loc="upper left")
        if i in log_panels:
            ax.set_yscale("log")
        elif from_zero:
            ax.set_ylim(bottom=0)
    axes[-1].set_xlabel("시작 뒤 (초)")
    fig.suptitle(title, fontsize=12)
    fig.tight_layout()
    save(fig, out, name)


def b1(start_text, out):
    timeline(start_text, [0, 30, 150, 270, 390, 510, 630], ["워밍업", "초당 20", "50", "100", "200", "400"], [
        ("결제 처리 (초당)", [(rate(PAY), "처리")]),
        ("서버 결제 응답 p95 (ms, 로그)", [(quantile(PAY, 0.95), "p95")]),
        ("DB 연결 (개)", [(DB_ACTIVE, "사용 중"), (DB_PENDING, "기다리는 요청")]),
        ("MySQL 행 락 대기 (초당)", [(ROW_LOCK_WAITS, "대기")]),
    ], "B-1 한계: 초당 결제를 단계마다 올림", "b1-payment-ramp.png", out, log_panels=(1,))


def b1c(start_text, out):
    stage = int(os.environ.get("STAGE_SEC", 120))
    timeline(start_text, [0, stage, stage * 2], ["동시 4개 (주문 1대)", "동시 8개 (주문 2대)"], [
        ("결제 처리 (초당, 필요량 48)", [(rate(PAY), "처리")]),
        ("서버 결제 응답 (ms)", [(quantile(PAY, 0.95), "p95"), (quantile(PAY, 0.99), "p99")]),
        ("DB 연결 (개)", [(DB_ACTIVE, "사용 중"), (DB_PENDING, "기다리는 요청")]),
        ("MySQL 행 락 대기 (초당)", [(ROW_LOCK_WAITS, "대기")]),
    ], "B-1 주문이 보내는 모양 그대로 결제: 동시 N개가 쉬지 않고 부름", "b1-payment-concurrency.png", out,
        from_zero=True)


def b2(start_text, out):
    phase = int(os.environ.get("PHASE_SEC", 60))
    warmup = min(30, phase)
    avg_ms = (f"sum(rate(http_client_requests_seconds_sum{{{BANK}}}[{W}])) / "
              f"sum(rate(http_client_requests_seconds_count{{{BANK}}}[{W}])) * 1000")
    timeline(start_text, [0, warmup, warmup + phase, warmup + phase * 2, warmup + phase * 3],
             ["워밍업", "앞", "가운데 (정산 섞음)", "뒤"], [
                 ("처리 (초당)", [(rate(PAY), "결제"), (rate(PAYOUT), "정산 지급")]),
                 ("서버 응답 p95 (ms)", [(quantile(PAY, 0.95), "결제"), (quantile(PAYOUT, 0.95), "정산 지급")]),
                 ("cash → 가짜 은행 호출 평균 (ms)", [(avg_ms, "은행 호출")]),
                 ("MySQL 행 락 대기 (초당)", [(ROW_LOCK_WAITS, "대기")]),
             ], "B-2 결제 중 정산 지급 몰림: 가운데 구간만 정산 섞음", "b2-payment-with-payout.png", out, from_zero=True)


def b5(start_text, out):
    duration = int(os.environ.get("DURATION_SEC", 600))
    active = f"sum(http_server_requests_active_seconds_active_count{{{APP}}})"
    timeline(start_text, [0, duration], ["충전 승인 초당 48건 (가짜 PG 승인 1초)"], [
        ("충전 처리 (초당)", [(rate(CHARGE), "처리")]),
        ("서버 충전 응답 (ms)", [(quantile(CHARGE, 0.95), "p95"), (quantile(CHARGE, 0.99), "p99")]),
        ("처리 중인 요청 (개, 스레드 200개까지)", [(active, "처리 중")]),
        ("DB 연결 (개)", [(DB_ACTIVE, "사용 중"), (DB_PENDING, "기다리는 요청")]),
    ], "B-5 접수가 열렸을 때 충전 승인", "b5-charge.png", out, from_zero=True)


def b3(prefix, out):
    # k6 --summary-export 파일에서 장수별 p95를 읽는다 (회차마다 한 파일)
    runs = {}
    for path in sorted(glob.glob(prefix + "-b3-*.json")):
        metrics = json.load(open(path))["metrics"]
        for key, values in metrics.items():
            found = re.fullmatch(r"http_req_duration\{cards:(\d)\}", key)
            if found:
                runs.setdefault(int(found.group(1)), []).append(values["p(95)"])
    cards = sorted(runs)
    medians = [statistics.median(runs[c]) for c in cards]
    fig, ax = plt.subplots(figsize=(7, 4.5))
    ax.bar([str(c) + "장" for c in cards], medians, color="#4c72b0", label=f"p95 중앙값 ({len(runs[cards[0]])}회)")
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
    right.set_title("걸린 시간 (같은 대상 수를 여러 번 쟀으면 중앙값)", fontsize=10)
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
    if len(sys.argv) != 4 or sys.argv[1] not in ("b1", "b1c", "b2", "b3", "b4", "b5"):
        print(__doc__)
        sys.exit(1)
    globals()[sys.argv[1]](sys.argv[2], sys.argv[3])
