#!/usr/bin/env python3
"""예치금 부하 측정 결과를 3회 중앙값으로 정리하고 판정 기준(P1~P8, C)으로 판정한다.

  report.py summary <결과 폴더> <내보낼 폴더>  : <결과>/<LABEL>/rep<N>/ 를 모아 summary.md 를 만든다
  report.py selftest                          : 판정 계산 확인

판정 기준은 docs/measurements/2026-11-16-예치금-부하-판정기준.md 3장이다. 값을 바꾸려면 그 문서 「고친 기록」부터 남긴다.
나아짐(G1~G6)은 무엇을 고쳤는지에 따라 볼 줄이 달라 여기서 판정하지 않고, A·B 중앙값을 나란히 보여 준다.
"""
import json
import re
import statistics
import sys
from pathlib import Path

# 판정 기준 문서 3-1
NEED_PER_SEC = 48        # 1장 예상 트래픽 (동시 종목 12개 × 초당 4명)
PAY_P99_MS = 1000        # 주문이 예치금을 2초까지 기다리므로 그 절반 (근거 있음)
PAY_P95_ALARM_MS = 2000  # 기획 건강 지표 H7 경보선 (근거 있음)
PAYOUT_KEEP_RATIO = 0.8  # 추천값(미확정)
WITHDRAW_P95_MS = 1000   # 추천값(미확정)
SQL_GROWTH_MAX = 10      # 추천값(미확정)
CHARGE_P95_MS = 2000     # PG 1초 + 1초, 추천값(미확정)
FAIL_RATE_MAX = 0.01     # 추천값(미확정)


def load(path):
    return json.loads(path.read_text()) if path.exists() else None


def metric(summary, name, key):
    if not summary:
        return None
    return summary['metrics'].get(name, {}).get(key)


def checks(summary):
    """k6 요약의 확인 항목 {이름: (통과, 실패)}"""
    found = {}

    def walk(group):
        items = group.get('checks', {})
        for check in (items.values() if isinstance(items, dict) else items):
            found[check['name']] = (check['passes'], check['fails'])
        groups = group.get('groups', {})
        for sub in (groups.values() if isinstance(groups, dict) else groups):
            walk(sub)

    if summary:
        walk(summary['root_group'])
    return found


def params(rep_dir):
    values = {'STAGE_SEC': 120, 'PHASE_SEC': 60}
    path = rep_dir / 'params.txt'
    if path.exists():
        for line in path.read_text().splitlines():
            key, _, value = line.partition('=')
            if value.isdigit():
                values[key] = int(value)
    return values


def b4_sql(rep_dir):
    """대상 수 → 배치 한 번의 cash 표 SQL 수 (b4-notice.sh 출력)"""
    result = {}
    for path in rep_dir.glob('r-b4-*-1.txt'):
        text = path.read_text()
        if 'cash_문장_수' not in text:
            continue
        found = re.search(r'\|\s+(\d+) \|\s+[\d.]+ \|', text[text.index('cash_문장_수'):])
        if found:
            result[int(re.search(r'r-b4-(\d+)-', path.name).group(1))] = int(found.group(1))
    return result


def money_problems(rep_dir):
    """돈 확인(C1~C6)에서 0이 아닌 줄, 락 통계(C7)의 데드락·락 대기 시간 초과"""
    problems = []
    for path in sorted(rep_dir.glob('*.verify.txt')):
        for line in path.read_text().splitlines():
            found = re.search(r'\|\s*(.+?\(0이어야 함\))\s*\|\s*(-?\d+)\s*\|$', line)
            if found and int(found.group(2)) != 0:
                problems.append(f'{path.name.removesuffix(".verify.txt")}: {found.group(1)} = {found.group(2)}')
    lock = rep_dir / 'lock-stats.txt'
    if lock.exists():
        for line in lock.read_text().splitlines():
            found = re.search(r'\|\s*(lock_deadlocks|lock_timeouts)\s*\|\s*(\d+)\s*\|', line)
            if found and int(found.group(2)) != 0:
                problems.append(f'락 통계: {found.group(1)} = {found.group(2)}')
    elif any(rep_dir.glob('*.verify.txt')):
        problems.append('락 통계 파일 없음')
    return problems


def read_rep(rep_dir):
    """한 바퀴의 판정에 쓰는 숫자"""
    p = params(rep_dir)
    b0, b1, b2 = load(rep_dir / 'b0.json'), load(rep_dir / 'b1.json'), load(rep_dir / 'b2.json')
    b3, b5 = load(rep_dir / 'r-b3-1.json'), load(rep_dir / 'b5.json')
    sql = b4_sql(rep_dir)
    row = {}
    for vus in (4, 8):
        count = metric(b1, f'iterations{{stage:vus{vus}}}', 'count')
        row[f'b1_vus{vus}_per_sec'] = count / p['STAGE_SEC'] if count is not None else None
        row[f'b1_vus{vus}_p95'] = metric(b1, f'http_req_duration{{stage:vus{vus}}}', 'p(95)')
        row[f'b1_vus{vus}_p99'] = metric(b1, f'http_req_duration{{stage:vus{vus}}}', 'p(99)')
        row[f'b1_vus{vus}_fail'] = metric(b1, f'http_req_failed{{stage:vus{vus}}}', 'value')
    before = metric(b2, 'iterations{phase:before}', 'count')
    during = metric(b2, 'iterations{phase:during}', 'count')
    row['b2_during_ratio'] = during / before if before and during is not None else None
    for phase in ('before', 'during', 'after'):
        row[f'b2_{phase}_p95'] = metric(b2, f'http_req_duration{{api:pay,phase:{phase}}}', 'p(95)')
    row['b2_during_p99'] = metric(b2, 'http_req_duration{api:pay,phase:during}', 'p(99)')
    same_payout = checks(b0).get('같은 정산 지급 200 또는 503')
    row['b0_same_payout_bad'] = same_payout[1] if same_payout else None
    row['b0_pay_p95'] = metric(b0, 'http_req_duration{api:pay}', 'p(95)')
    for cards in (1, 3, 5):
        row[f'b3_cards{cards}_p95'] = metric(b3, f'http_req_duration{{cards:{cards}}}', 'p(95)')
    row['b4_sql_100'] = sql.get(100)
    row['b4_sql_10000'] = sql.get(10000)
    row['b4_growth'] = sql[10000] / sql[100] if 100 in sql and 10000 in sql else None
    row['b5_p95'] = metric(b5, 'http_req_duration', 'p(95)')
    row['b5_p99'] = metric(b5, 'http_req_duration', 'p(99)')
    row['b5_fail'] = metric(b5, 'http_req_failed', 'value')
    row['b5_dropped'] = metric(b5, 'dropped_iterations', 'count')
    limit = load(rep_dir / 'b5-limit.json')
    row['b5_limit_max'] = metric(limit, 'http_req_duration', 'max')
    row['b5_limit_fail'] = metric(limit, 'http_req_failed', 'value')
    row['money'] = money_problems(rep_dir)
    failed = rep_dir / 'failed.txt'
    row['failed'] = failed.read_text().split() if failed.exists() else []
    return row


def median(values):
    values = [v for v in values if v is not None]
    return statistics.median(values) if values else None


def judge(m):
    """중앙값 m으로 P1~P8을 판정한다. (번호, 조건, 값 글, 문제 있음 여부 / None은 재지 않음)"""
    def over(value, limit):
        return None if value is None else value >= limit

    def under(value, limit):
        return None if value is None else value < limit

    def any_true(*flags):
        known = [f for f in flags if f is not None]
        return None if not known else any(known)

    results = []
    for no, vus, who in (('P1', 4, '주문 1대'), ('P2', 8, '주문 2대')):
        per_sec, p99 = m.get(f'b1_vus{vus}_per_sec'), m.get(f'b1_vus{vus}_p99')
        results.append((no, f'B-1 동시 {vus}개({who}): 처리량 < 초당 {NEED_PER_SEC}건 또는 p99 ≥ 1초',
                        f'초당 {fmt(per_sec)}건, p99 {fmt(p99)}ms',
                        any_true(under(per_sec, NEED_PER_SEC), over(p99, PAY_P99_MS))))
    ratio, p99 = m.get('b2_during_ratio'), m.get('b2_during_p99')
    results.append(('P3', 'B-2 정산 섞인 구간: 결제 p99 ≥ 1초 또는 처리량 < 앞 구간의 80%',
                    f'p99 {fmt(p99)}ms, 처리량 {fmt(ratio, 2, pct=True)}',
                    any_true(over(p99, PAY_P99_MS), under(ratio, PAYOUT_KEEP_RATIO))))
    bad = m.get('b0_same_payout_bad')
    results.append(('P4', 'B-0 같은 정산 동시 지급: 200·503이 아닌 응답 ≥ 1건', f'{fmt(bad)}건', over(bad, 1)))
    p95 = m.get('b3_cards5_p95')
    results.append(('P5', 'B-3 카드 5장 환급 p95 ≥ 1초', f'{fmt(p95)}ms', over(p95, WITHDRAW_P95_MS)))
    growth = m.get('b4_growth')
    results.append(('P6', 'B-4 대상 100 → 10,000명일 때 SQL 수가 10배 넘게 늘어남',
                    f'{fmt(growth, 1)}배 ({fmt(m.get("b4_sql_100"))} → {fmt(m.get("b4_sql_10000"))})',
                    None if growth is None else growth > SQL_GROWTH_MAX))
    p95, fail = m.get('b5_p95'), m.get('b5_fail')
    results.append(('P7', 'B-5 충전 p95 ≥ 2초 또는 실패 ≥ 1%', f'p95 {fmt(p95)}ms, 실패 {fmt(fail, 2, pct=True)}',
                    any_true(over(p95, CHARGE_P95_MS), over(fail, FAIL_RATE_MAX))))
    pay_p95 = [m.get(k) for k in ('b0_pay_p95', 'b1_vus4_p95', 'b1_vus8_p95', 'b2_before_p95', 'b2_during_p95',
                                  'b2_after_p95')]
    worst = max((v for v in pay_p95 if v is not None), default=None)
    results.append(('P8', '어느 시나리오든 결제 p95 ≥ 2초 (H7 경보선)', f'가장 큰 p95 {fmt(worst)}ms',
                    over(worst, PAY_P95_ALARM_MS)))
    return results


def fmt(value, digits=0, pct=False):
    if value is None:
        return '-'
    if pct:
        return f'{value * 100:.{digits}f}%'
    return f'{value:,.{digits}f}'


def verdict(flag):
    return {True: '**문제 있음**', False: '통과', None: '재지 않음'}[flag]


ROWS = [
    ('B-1 동시 4개 처리량 (초당)', 'b1_vus4_per_sec', 0), ('B-1 동시 4개 p95 (ms)', 'b1_vus4_p95', 0),
    ('B-1 동시 4개 p99 (ms)', 'b1_vus4_p99', 0), ('B-1 동시 8개 처리량 (초당)', 'b1_vus8_per_sec', 0),
    ('B-1 동시 8개 p95 (ms)', 'b1_vus8_p95', 0), ('B-1 동시 8개 p99 (ms)', 'b1_vus8_p99', 0),
    ('B-2 앞 결제 p95 (ms)', 'b2_before_p95', 0), ('B-2 가운데 결제 p95 (ms)', 'b2_during_p95', 0),
    ('B-2 가운데 결제 p99 (ms)', 'b2_during_p99', 0), ('B-2 가운데 처리량 ÷ 앞', 'b2_during_ratio', 2),
    ('B-0 같은 정산 동시 지급 200·503 아님 (건)', 'b0_same_payout_bad', 0), ('B-0 결제 p95 (ms)', 'b0_pay_p95', 0),
    ('B-3 카드 1장 p95 (ms)', 'b3_cards1_p95', 0), ('B-3 카드 3장 p95 (ms)', 'b3_cards3_p95', 0),
    ('B-3 카드 5장 p95 (ms)', 'b3_cards5_p95', 0), ('B-4 100명 SQL 수', 'b4_sql_100', 0),
    ('B-4 10,000명 SQL 수', 'b4_sql_10000', 0), ('B-5 충전 p95 (ms)', 'b5_p95', 0), ('B-5 충전 p99 (ms)', 'b5_p99', 0),
    ('B-5 충전 실패율', 'b5_fail', 4), ('B-5 버려진 반복', 'b5_dropped', 0),
    ('B-5 한계 가장 느린 응답 (ms, 기록만)', 'b5_limit_max', 0), ('B-5 한계 실패율 (기록만)', 'b5_limit_fail', 4),
]


def summary(result_dir, export_dir):
    result_dir, export_dir = Path(result_dir), Path(export_dir)
    labels = sorted(p.name for p in result_dir.iterdir() if p.is_dir() and any(p.glob('rep*')))
    reps = {label: {r.name: read_rep(r) for r in sorted((result_dir / label).glob('rep*'))} for label in labels}
    medians = {label: {key: median([row[key] for row in reps[label].values()]) for _, key, _ in ROWS}
               for label in labels}
    for label in labels:
        medians[label]['b4_growth'] = median([row['b4_growth'] for row in reps[label].values()])

    lines = ['## 예치금 부하 측정 결과 (3회 중앙값)', '',
             '판정 기준: `docs/measurements/2026-11-16-예치금-부하-판정기준.md` 3장. 값은 중앙값, 돈 확인(C)은 모든 회차', '']
    refs = result_dir / 'refs.txt'
    if refs.exists():
        lines += ['```', refs.read_text().strip(), '```', '']

    for label in labels:
        lines += [f'### {label.upper()}: 문제 있음 판정 (P1~P8)', '', '| 번호 | 조건 | 중앙값 | 판정 |', '|---|---|---|---|']
        for no, condition, value, flag in judge(medians[label]):
            lines.append(f'| {no} | {condition} | {value} | {verdict(flag)} |')
        money = [f'{rep}: {p}' for rep, row in reps[label].items() for p in row['money']]
        failed = [f'{rep}: {name}' for rep, row in reps[label].items() for name in row['failed']]
        lines += ['', f'**돈 확인 (C1~C7)**: ' + ('모든 회차 통과' if not money else '**실패**'), '']
        lines += [f'- {p}' for p in money]
        if failed:
            lines += ['', '**k6 요약을 못 남긴 시나리오**: ' + ', '.join(failed)]
        lines.append('')

    header = ['항목'] + [f'{label.upper()} {rep}' for label in labels for rep in reps[label]] + \
             [f'{label.upper()} 중앙값' for label in labels]
    lines += ['### 시나리오별 숫자', '', '| ' + ' | '.join(header) + ' |', '|' + '---|' * len(header)]
    for title, key, digits in ROWS:
        cells = [fmt(row[key], digits) for label in labels for row in reps[label].values()]
        cells += [fmt(medians[label][key], digits) for label in labels]
        lines.append(f'| {title} | ' + ' | '.join(cells) + ' |')
    lines += ['', '회차별 그래프(PNG)와 원본(k6 요약, 돈 확인 SQL, 락 통계, 예치금 로그)은 아티팩트의 `<라벨>/rep<N>/`에 있어요.']

    export_dir.mkdir(parents=True, exist_ok=True)
    (export_dir / 'summary.md').write_text('\n'.join(lines) + '\n')
    print('만듦', export_dir / 'summary.md')


def selftest():
    good = {'b1_vus4_per_sec': 200, 'b1_vus4_p99': 90, 'b1_vus8_per_sec': 400, 'b1_vus8_p99': 110,
            'b2_during_ratio': 0.95, 'b2_during_p99': 80, 'b0_same_payout_bad': 0, 'b3_cards5_p95': 500,
            'b4_growth': 9.9, 'b4_sql_100': 204, 'b4_sql_10000': 20004, 'b5_p95': 1400, 'b5_fail': 0,
            'b1_vus4_p95': 70, 'b1_vus8_p95': 60, 'b2_during_p95': 20}
    assert all(flag is False for *_, flag in judge(good)), judge(good)
    bad = dict(good, b1_vus4_per_sec=40, b2_during_ratio=0.7, b0_same_payout_bad=9, b3_cards5_p95=1600,
               b4_growth=98, b5_fail=0.02, b1_vus8_p95=2100)
    flags = {no: flag for no, _, _, flag in judge(bad)}
    assert flags == {'P1': True, 'P2': False, 'P3': True, 'P4': True, 'P5': True, 'P6': True, 'P7': True,
                     'P8': True}, flags
    assert {no: flag for no, _, _, flag in judge({})} == {f'P{i}': None for i in range(1, 9)}
    assert median([3, None, 1, 2]) == 2 and median([None]) is None
    print('selftest 통과')


if __name__ == '__main__':
    if len(sys.argv) == 4 and sys.argv[1] == 'summary':
        summary(sys.argv[2], sys.argv[3])
    elif len(sys.argv) == 2 and sys.argv[1] == 'selftest':
        selftest()
    else:
        print(__doc__)
        sys.exit(1)
