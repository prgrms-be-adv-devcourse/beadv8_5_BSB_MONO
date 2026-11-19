#!/usr/bin/env python3
"""시나리오 B 결과 그래프를 PNG로 만든다 (Prometheus와 결과 파일을 읽음)

필요한 것: matplotlib (python3 -m venv .venv && .venv/bin/pip install matplotlib)
실행(backend/에서):
  python3 loadtest/scenario-b/graph.py b0 "2026-11-17 10:00:00" <출력 폴더>   # B-0 시작 시각
  python3 loadtest/scenario-b/graph.py b1 "2026-11-16 15:48:31" <출력 폴더>   # B-1 한계(도착 속도 올림) 시작 시각
  python3 loadtest/scenario-b/graph.py b1c "2026-11-17 10:00:00" <출력 폴더>  # B-1 동시 4·8개 시작 시각
  python3 loadtest/scenario-b/graph.py b2 "2026-11-16 17:29:45" <출력 폴더>   # B-2 시작 시각
  python3 loadtest/scenario-b/graph.py b3 loadtest/scenario-b/results/2026-11-16 <출력 폴더>   # b3-*.json 앞부분
  python3 loadtest/scenario-b/graph.py b4 loadtest/scenario-b/results/2026-11-16 <출력 폴더>   # b4-*.txt 앞부분
  python3 loadtest/scenario-b/graph.py b5 "2026-11-17 10:30:00" <출력 폴더>   # B-5 충전 시작 시각
  python3 loadtest/scenario-b/graph.py b0again|b0split|b6|b7|b8 "<시작 시각>" <출력 폴더>   # 2차 (PHASE_SEC, EXT_STAGE_SEC)

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
REFUND = APP + ',uri="/internal/v1/cash/payments/{orderId}/refund"'
BALANCE = APP + ',uri="/api/v1/cash/wallets/balance"'
# 바쁜 요청 쓰레드 수 (server.tomcat.mbeanregistry.enabled, compose.load.yml)
# 지표 포트(8091)도 자기 쓰레드가 있어서 요청 포트(8081)만 센다
BUSY_THREADS = f'sum(tomcat_threads_busy_threads{{{APP},name="http-nio-8081"}})'
ROW_LOCK_WAITS = f"rate(mysql_global_status_innodb_row_lock_waits[{W}])"
DB_ACTIVE = f"hikaricp_connections_active{{{APP}}}"
DB_PENDING = f"hikaricp_connections_pending{{{APP}}}"
# DB 연결을 받기까지 걸린 시간. 히스토그램이 없어 평균(합÷횟수)과 가장 오래(최근 약 1~2분 안의 최댓값)만 그린다
DB_WAIT = ("DB 연결 받기까지 (ms, 게이트웨이는 10초에 끊음)", [
    (f"sum(rate(hikaricp_connections_acquire_seconds_sum{{{APP}}}[{W}])) / "
     f"sum(rate(hikaricp_connections_acquire_seconds_count{{{APP}}}[{W}])) * 1000", "평균"),
    (f"max(hikaricp_connections_acquire_seconds_max{{{APP}}}) * 1000", "가장 오래"),
])
# 처리 중인 요청 수. 퍼센트 히스토그램을 켜서 _active_count가 아니라 _gcount로 나온다
IN_FLIGHT = f"sum(http_server_requests_active_seconds_gcount{{{APP}}})"

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


def container_cpu_panel(out):
    """앱 밖(cAdvisor)에서 잰 예치금·MySQL CPU 칸. 측정 컨테이너(run.sh)가 남긴 container-ids.txt가 있을 때만 그린다
    앱이 바쁘면 예치금 /actuator/prometheus가 비는데, 이 값은 그때도 남는다. cAdvisor는 컨테이너 ID 경로로만 내보낸다"""
    path = os.path.join(out, "container-ids.txt")
    if not os.path.exists(path):
        return []
    # 예치금을 다시 띄우면(run.sh pg4·proxy·direct) 같은 이름에 ID가 여러 개 생긴다
    ids = {}
    for line in open(path).read().split("\n"):
        if "\t" in line:
            who, cid = line.split("\t")
            ids.setdefault(who, []).append(cid)
    lines = [(f'sum(rate(container_cpu_usage_seconds_total{{id=~".*({"|".join(ids[who])}).*"}}[{W}]))', label)
             for who, label in [("cash", "예치금 (제한 0.5)"), ("mysql", "MySQL (제한 1.0)"),
                                ("toxiproxy", "지연 컨테이너 (제한 0.25)")] if ids.get(who)]
    return [("CPU (앱 밖에서 잼, 코어 개수)", lines)] if lines else []


def timeline(start_text, edges, labels, panels, title, name, out, log_panels=(), from_zero=False):
    """panels: [(제목, [(쿼리, 선 이름), ...]), ...] 를 시작 시각부터 edges[-1]초까지 위아래로 그린다
    맨 아래에 앱 밖에서 잰 CPU 칸을 붙인다 (측정 컨테이너 결과일 때만)"""
    panels = panels + container_cpu_panel(out)
    start = datetime.strptime(start_text, "%Y-%m-%d %H:%M:%S").timestamp()
    end = start + edges[-1]
    fig, axes = plt.subplots(len(panels), 1, figsize=(10, 2.75 * len(panels)), sharex=True)
    for i, (ax, (panel_title, lines)) in enumerate(zip(axes, panels)):
        positive = False
        for query, label in lines:
            xs, ys = series(query, start, end)
            ax.plot(xs, ys, label=label)
            positive = positive or any(y > 0 for y in ys)
        ax.set_title(panel_title, loc="left", fontsize=10)
        mark(ax, edges, labels)
        if len(lines) > 1:
            ax.legend(fontsize=8, loc="upper left")
        # 앱이 바빠 지표가 비면 0보다 큰 값이 없어 로그 눈금을 못 그린다. 그때는 보통 눈금으로 둔다
        if i in log_panels and positive:
            ax.set_yscale("log")
        elif from_zero:
            ax.set_ylim(bottom=0)
    axes[-1].set_xlabel("시작 뒤 (초)")
    fig.suptitle(title, fontsize=12)
    fig.tight_layout()
    save(fig, out, name)


def bank_avg_ms():
    return (f"sum(rate(http_client_requests_seconds_sum{{{BANK}}}[{W}])) / "
            f"sum(rate(http_client_requests_seconds_count{{{BANK}}}[{W}])) * 1000")


def b0(start_text, out):
    # b0-integrity.js: 같은 주문·정산·충전 동시 요청은 0~30초, 결제→환불·정산 섞기는 20~80초
    timeline(start_text, [0, 20, 80], ["같은 요청 동시", "결제→환불 초당 30 + 정산 초당 10"], [
        ("처리 (초당)", [(rate(PAY), "결제"), (rate(REFUND), "환불"), (rate(PAYOUT), "정산 지급")]),
        ("서버 응답 p95 (ms)", [(quantile(PAY, 0.95), "결제"), (quantile(REFUND, 0.95), "환불"),
                              (quantile(PAYOUT, 0.95), "정산 지급")]),
        ("cash → 가짜 은행 호출 평균 (ms, 5초 넘으면 결과 모름)", [(bank_avg_ms(), "은행 호출")]),
        ("처리 중인 요청 (개, 스레드 200개까지)", [(IN_FLIGHT, "처리 중")]),
        ("DB 연결 (개)", [(DB_ACTIVE, "사용 중"), (DB_PENDING, "기다리는 요청")]),
        DB_WAIT,
        ("예치금 CPU (제한 0.5개 중 쓴 비율)", [(f"process_cpu_usage{{{APP}}}", "CPU")]),
        ("MySQL 행 락 대기 (초당)", [(ROW_LOCK_WAITS, "대기")]),
    ], "B-0 같은 요청이 동시에 몰릴 때", "b0-integrity.png", out, from_zero=True)


def b1(start_text, out):
    # b1-payment-ramp.js: 예열 30초 뒤 단계마다 STAGE_SEC (QUICK은 15초)
    stage = int(os.environ.get("STAGE_SEC", 120))
    timeline(start_text, [0] + [30 + stage * i for i in range(6)], ["워밍업", "초당 20", "50", "100", "200", "400"], [
        ("결제 처리 (초당)", [(rate(PAY), "처리")]),
        ("서버 결제 응답 p95 (ms, 로그)", [(quantile(PAY, 0.95), "p95")]),
        ("DB 연결 (개)", [(DB_ACTIVE, "사용 중"), (DB_PENDING, "기다리는 요청")]),
        DB_WAIT,
        ("MySQL 행 락 대기 (초당)", [(ROW_LOCK_WAITS, "대기")]),
    ], "B-1 한계: 초당 결제를 단계마다 올림", "b1-payment-ramp.png", out, log_panels=(1,))


def b1c(start_text, out):
    stage = int(os.environ.get("STAGE_SEC", 120))
    timeline(start_text, [0, stage, stage * 2], ["동시 4개 (주문 1대)", "동시 8개 (주문 2대)"], [
        ("결제 처리 (초당, 필요량 48)", [(rate(PAY), "처리")]),
        ("서버 결제 응답 (ms)", [(quantile(PAY, 0.95), "p95"), (quantile(PAY, 0.99), "p99")]),
        ("DB 연결 (개)", [(DB_ACTIVE, "사용 중"), (DB_PENDING, "기다리는 요청")]),
        DB_WAIT,
        ("MySQL 행 락 대기 (초당)", [(ROW_LOCK_WAITS, "대기")]),
    ], "B-1 주문이 보내는 모양 그대로 결제: 동시 N개가 쉬지 않고 부름", "b1-payment-concurrency.png", out,
        from_zero=True)


def b2(start_text, out):
    phase = int(os.environ.get("PHASE_SEC", 60))
    warmup = min(30, phase)
    timeline(start_text, [0, warmup, warmup + phase, warmup + phase * 2, warmup + phase * 3],
             ["워밍업", "앞", "가운데 (정산 섞음)", "뒤"], [
                 ("처리 (초당)", [(rate(PAY), "결제"), (rate(PAYOUT), "정산 지급")]),
                 ("서버 응답 p95 (ms)", [(quantile(PAY, 0.95), "결제"), (quantile(PAYOUT, 0.95), "정산 지급")]),
                 ("cash → 가짜 은행 호출 평균 (ms)", [(bank_avg_ms(), "은행 호출")]),
                 DB_WAIT,
                 ("MySQL 행 락 대기 (초당)", [(ROW_LOCK_WAITS, "대기")]),
             ], "B-2 결제 중 정산 지급 몰림: 가운데 구간만 정산 섞음", "b2-payment-with-payout.png", out, from_zero=True)


def b5(start_text, out):
    duration = int(os.environ.get("DURATION_SEC", 600))
    timeline(start_text, [0, duration], ["충전 승인 초당 48건 (가짜 PG 승인 1초)"], [
        ("충전 처리 (초당)", [(rate(CHARGE), "처리")]),
        ("서버 충전 응답 (ms)", [(quantile(CHARGE, 0.95), "p95"), (quantile(CHARGE, 0.99), "p99")]),
        ("처리 중인 요청 (개, 스레드 200개까지)", [(IN_FLIGHT, "처리 중")]),
        ("DB 연결 (개)", [(DB_ACTIVE, "사용 중"), (DB_PENDING, "기다리는 요청")]),
        DB_WAIT,
    ], "B-5 접수가 열렸을 때 충전 승인", "b5-charge.png", out, from_zero=True)


def b0_mix(start_text, out, edges, labels, title, name):
    timeline(start_text, edges, labels, [
        ("처리 (초당)", [(rate(PAY), "결제"), (rate(REFUND), "환불"), (rate(PAYOUT), "정산 지급")]),
        ("서버 응답 p95 (ms)", [(quantile(PAY, 0.95), "결제"), (quantile(REFUND, 0.95), "환불"),
                              (quantile(PAYOUT, 0.95), "정산 지급")]),
        ("cash → 가짜 은행 호출 평균 (ms, 5초 넘으면 결과 모름)", [(bank_avg_ms(), "은행 호출")]),
        ("요청 쓰레드 (개, 200개까지)", [(IN_FLIGHT, "처리 중인 요청"), (BUSY_THREADS, "바쁜 쓰레드")]),
        ("DB 연결 (개)", [(DB_ACTIVE, "사용 중"), (DB_PENDING, "기다리는 요청")]),
        ("MySQL 행 락 대기 (초당)", [(ROW_LOCK_WAITS, "대기")]),
    ], title, name, out, from_zero=True)


def b0again(start_text, out):
    mix = int(os.environ.get("PHASE_SEC", 60))
    b0_mix(start_text, out, [0, mix], ["결제→환불 초당 30 + 정산 초당 10 (환불이 이미 돌아 본 뒤)"],
           "B-0 다시: B-2 뒤에 섞인 구간만 다시", "b0-again.png")


def b0split(start_text, out):
    mix = int(os.environ.get("PHASE_SEC", 60))
    b0_mix(start_text, out, [0, mix, mix * 2], ["결제→환불만 초당 30", "정산 지급만 초당 10"],
           "B-0 나누기: 요청 종류를 나눠 다시", "b0-split.png")


def external(start_text, out, title, name):
    stage = int(os.environ.get("EXT_STAGE_SEC", 180))
    timeline(start_text, [0, stage, stage * 2, stage * 3], ["충전 초당 25", "50", "75"], [
        ("처리 (초당)", [(rate(CHARGE), "충전 승인"), (rate(PAY), "결제"), (rate(BALANCE), "잔액 조회")]),
        ("서버 응답 p99 (ms, 로그)", [(quantile(CHARGE, 0.99), "충전 승인"), (quantile(PAY, 0.99), "결제"),
                                   (quantile(BALANCE, 0.99), "잔액 조회")]),
        ("요청 쓰레드 (개, 200개까지)", [(IN_FLIGHT, "처리 중인 요청"), (BUSY_THREADS, "바쁜 쓰레드")]),
        ("DB 연결 (개, 10개까지)", [(DB_ACTIVE, "사용 중"), (DB_PENDING, "기다리는 요청")]),
        DB_WAIT,
    ], title, name, out, log_panels=(1,))


def b6(start_text, out):
    external(start_text, out, "B-6 토스(가짜 PG 승인)가 4초일 때", "b6-external.png")


def b7(start_text, out):
    external(start_text, out, "B-7 Redis가 명령마다 100ms 느릴 때", "b7-external.png")


def b8(start_text, out):
    external(start_text, out, "B-8 MySQL이 쿼리마다 100ms 느릴 때", "b8-external.png")


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
    if len(sys.argv) != 4 or sys.argv[1] not in ("b0", "b1", "b1c", "b2", "b3", "b4", "b5", "b0again", "b0split",
                                                  "b6", "b7", "b8"):
        print(__doc__)
        sys.exit(1)
    globals()[sys.argv[1]](sys.argv[2], sys.argv[3])
