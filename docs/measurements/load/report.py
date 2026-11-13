#!/usr/bin/env python3
"""결제 잠금 부하 측정 결과 정리.

  report.py prom <prometheus 주소> <rep 폴더>   : windows.tsv의 시나리오 구간마다 주문 연결 지표를 받아 <이름>.prom.json으로 둔다
  report.py summary <결과 폴더> <내보낼 폴더>   : <결과>/<LABEL>/rep<N>/ 를 모아 summary.md 와 그래프 PNG를 만든다
  report.py selftest                           : 계산 함수 확인

그래프는 matplotlib이 있을 때만 그린다 (pip install matplotlib).
"""
import json
import statistics
import sys
import urllib.parse
import urllib.request
from pathlib import Path

QUERIES = {
    'pending': 'max(hikaricp_connections_pending{job="order"})',
    'active': 'max(hikaricp_connections_active{job="order"})',
}
# 판정 기준 문서 1장의 추천값(미확정)
P1_PENDING_SECONDS = 30
P2_P95_MS = 2000
P3_FAIL_RATE = 0.01
P4_RATIO = 3


def prom(url, run_dir):
    run_dir = Path(run_dir)
    for line in (run_dir / 'windows.tsv').read_text().splitlines():
        name, start, end = line.split('\t')
        series = {}
        for key, query in QUERIES.items():
            params = urllib.parse.urlencode({'query': query, 'start': start, 'end': end, 'step': '1'})
            with urllib.request.urlopen(f'{url}/api/v1/query_range?{params}', timeout=30) as res:
                result = json.load(res)['data']['result']
            series[key] = [[float(t) - float(start), float(v)] for t, v in (result[0]['values'] if result else [])]
        (run_dir / f'{name}.prom.json').write_text(json.dumps(series))


def pending_stats(points):
    """연결 대기 최대·평균과, 대기가 0보다 컸던 초 수 (1초 간격 표본)"""
    values = [v for _, v in points]
    if not values:
        return {'max': None, 'avg': None, 'seconds': None}
    return {'max': max(values), 'avg': sum(values) / len(values), 'seconds': sum(1 for v in values if v > 0)}


def read_scenario(rep_dir, name):
    k6_file = rep_dir / f'{name}.k6.json'
    if not k6_file.exists():
        return None
    metrics = json.loads(k6_file.read_text())['metrics']
    pay = metrics.get('pay_ms', {}).get('values', {})
    failed = metrics.get('flow_failed') or metrics.get('pay_failed') or {}
    dropped = metrics.get('dropped_iterations', {}).get('values', {}).get('count', 0)
    prom_file = rep_dir / f'{name}.prom.json'
    series = json.loads(prom_file.read_text()) if prom_file.exists() else {'pending': [], 'active': []}
    pending = pending_stats(series.get('pending', []))
    active = [v for _, v in series.get('active', [])]
    return {
        'p50': pay.get('med'), 'p95': pay.get('p(95)'), 'p99': pay.get('p(99)'), 'count': pay.get('count'),
        'fail': failed.get('values', {}).get('rate'), 'dropped': dropped,
        # 판정 P3·G3는 결제 요청만 센다 (흐름의 fail은 입장~주문 실패도 포함)
        'pay_fail': metrics.get('pay_failed', {}).get('values', {}).get('rate'),
        'pending_max': pending['max'], 'pending_avg': pending['avg'], 'pending_seconds': pending['seconds'],
        'active_max': max(active) if active else None, 'series': series,
    }


def median(values):
    values = [v for v in values if v is not None]
    return statistics.median(values) if values else None


def fmt(value, digits=0):
    if value is None:
        return '-'
    return f'{value:,.{digits}f}'


def judge(med):
    """변경 전(A)을 「문제 있음」으로 보는 조건 P1~P4. med = 시나리오 이름 → 지표 중앙값"""
    s4 = med.get('s4-300-flow') or {}
    s2, s3 = med.get('s2') or {}, med.get('s3') or {}
    ratio = s3['p95'] / s2['p95'] if s2.get('p95') and s3.get('p95') else None
    return [
        ('P1', f'S4 0.3초 흐름에서 연결 대기 > 0이 {P1_PENDING_SECONDS}초 넘게', s4.get('pending_seconds'),
         s4.get('pending_seconds') is not None and s4['pending_seconds'] > P1_PENDING_SECONDS),
        ('P2', f'S4 0.3초 흐름에서 결제 p95 > {P2_P95_MS}ms', s4.get('p95'),
         s4.get('p95') is not None and s4['p95'] > P2_P95_MS),
        ('P3', f'S4 0.3초 흐름에서 결제 실패 > {P3_FAIL_RATE:.0%}', s4.get('pay_fail'),
         s4.get('pay_fail') is not None and s4['pay_fail'] > P3_FAIL_RATE),
        ('P4', f'S3 p95 ≥ S2 p95 × {P4_RATIO}', ratio, ratio is not None and ratio >= P4_RATIO),
    ]


def failed_names(rep_dir):
    path = rep_dir / 'failed.txt'
    return set(path.read_text().split()) if path.exists() else set()


def read_check(rep_dir):
    path = rep_dir / 'check.txt'
    if not path.exists():
        return {}
    return dict(line.split('=', 1) for line in path.read_text().splitlines() if '=' in line)


def summary(results, out):
    results, out = Path(results), Path(out)
    out.mkdir(parents=True, exist_ok=True)
    lines = ['# 결제 잠금 부하 측정 결과 (자동 정리)', '',
             '숫자는 반복마다 값과 중앙값. 결제 시간 단위 ms, 실패율은 흐름 시나리오는 한 사람의 입장~결제 중 하나라도 실패한 비율,'
             ' 몰림 시나리오는 결제 실패 비율. 연결 대기는 주문 Hikari pending(1초 표본).', '']
    for label_dir in sorted(p for p in results.iterdir() if p.is_dir()):
        reps = sorted(p for p in label_dir.iterdir() if p.is_dir() and p.name.startswith('rep'))
        if not reps:
            continue
        names = []
        for rep in reps:
            for line in (rep / 'windows.tsv').read_text().splitlines() if (rep / 'windows.tsv').exists() else []:
                name = line.split('\t')[0]
                if name not in names:
                    names.append(name)
        lines += [f'## {label_dir.name}', '',
                  '| 시나리오 | 결제 p50 | 결제 p95 | 결제 p99 | 건수 | 실패율 | 결제 실패율 | 건너뜀 | 연결 대기 최대 | 대기 평균 | 대기>0 초 | 활성 연결 최대 |',
                  '|---|---|---|---|---|---|---|---|---|---|---|---|']
        med = {}
        for name in names:
            rows = [read_scenario(rep, name) for rep in reps]
            failed_reps = [rep.name for rep in reps if name in failed_names(rep)]
            rows = [r for r in rows if r]
            if not rows:
                lines.append(f'| {name} | 측정 실패 ({", ".join(failed_reps)}) |' + ' |' * 10)
                continue
            keys = ['p50', 'p95', 'p99', 'count', 'fail', 'pay_fail', 'dropped', 'pending_max', 'pending_avg', 'pending_seconds',
                    'active_max']
            med[name] = {k: median([r[k] for r in rows]) for k in keys}

            def cell(key, digits=0, percent=False):
                each = [r[key] for r in rows]
                show = (lambda v: '-' if v is None else f'{v:.2%}') if percent else (lambda v: fmt(v, digits))
                return f'{show(med[name][key])} ({", ".join(show(v) for v in each)})'
            lines.append(f'| {name} | {cell("p50")} | {cell("p95")} | {cell("p99")} | {cell("count")} | '
                         f'{cell("fail", percent=True)} | {cell("pay_fail", percent=True)} | {cell("dropped")} | {cell("pending_max")} | {cell("pending_avg", 1)} | '
                         f'{cell("pending_seconds")} | {cell("active_max")} |')
            plot(label_dir.name, name, rows, out)
        lines += ['', '판정(변경 전 기준, 중앙값):', '', '| 번호 | 조건 | 값 | 맞음 |', '|---|---|---|---|']
        for code, text, value, hit in judge(med):
            lines.append(f'| {code} | {text} | {fmt(value, 2) if isinstance(value, float) else fmt(value)} | '
                         f'{"예" if hit else "아니오"} |')
        lines += ['', '건너뜀은 k6가 VU가 모자라 시작하지 못한 사람 수예요. 0이 아니면 명목 부하보다 덜 걸린 것이에요.',
                  '', '정확성(반복마다):', '']
        for rep in reps:
            check = read_check(rep)
            failed = failed_names(rep)
            lines.append(f'- {rep.name}: ' + ', '.join(f'{k}={v}' for k, v in check.items())
                         + (f' / 측정 실패: {", ".join(sorted(failed))}' if failed else ''))
        lines.append('')
    (out / 'summary.md').write_text('\n'.join(lines))
    print('\n'.join(lines))


def plot(label, name, rows, out):
    try:
        import matplotlib
        matplotlib.use('Agg')
        import matplotlib.pyplot as plt
    except ImportError:
        return
    fig, ax = plt.subplots(figsize=(8, 3.2))
    for i, row in enumerate(rows, 1):
        for key, style in (('pending', '-'), ('active', ':')):
            points = row['series'].get(key, [])
            if points:
                ax.plot([t for t, _ in points], [v for _, v in points], style,
                        label=f'rep{i} {"wait" if key == "pending" else "active"}')
    ax.set_title(f'{label} {name}: order DB connections (wait = pending, active)')
    ax.set_xlabel('seconds')
    ax.set_ylabel('connections')
    ax.legend(fontsize=7, ncol=3)
    fig.tight_layout()
    fig.savefig(out / f'{label}-{name}.png', dpi=110)
    plt.close(fig)


def selftest():
    assert pending_stats([[0, 0], [1, 2], [2, 4]]) == {'max': 4, 'avg': 2, 'seconds': 2}
    assert pending_stats([]) == {'max': None, 'avg': None, 'seconds': None}
    verdict = judge({'s4-300-flow': {'pending_seconds': 31, 'p95': 2500, 'fail': 0.5, 'pay_fail': 0.0},
                     's2': {'p95': 100}, 's3': {'p95': 300}})
    assert [hit for *_, hit in verdict] == [True, True, False, True]
    assert judge({})[0][3] is False
    print('selftest ok')


if __name__ == '__main__':
    command = sys.argv[1] if len(sys.argv) > 1 else ''
    if command == 'prom':
        prom(sys.argv[2], sys.argv[3])
    elif command == 'summary':
        summary(sys.argv[2], sys.argv[3])
    elif command == 'selftest':
        selftest()
    else:
        print(__doc__)
        sys.exit(1)
