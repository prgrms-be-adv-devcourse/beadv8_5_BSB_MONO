#!/usr/bin/env python3
# k6 원시 CSV(--out csv=)에서 초당 처리량(RPS·TPS)과 엔드포인트별 지연을 계산한다.
# 사용: python analyze.py <stage-raw.csv> <out.json>
# RPS = 보낸 HTTP 요청 수/초, 성공 RPS = 기대한 응답을 받은 요청 수/초,
# TPS = 끝난 사용자 흐름(iteration) 수/초 (시나리오별: enter 입장 / poll 순번 조회 / buy 예매)
import csv, sys, json, collections, statistics


def pct(sorted_vals, p):
    if not sorted_vals:
        return None
    i = min(len(sorted_vals) - 1, int(round(p / 100 * (len(sorted_vals) - 1))))
    return round(sorted_vals[i], 1)


src, out = sys.argv[1], sys.argv[2]
req_sec = collections.Counter()      # 초 -> 요청 수
ok_sec = collections.Counter()       # 초 -> 기대 응답 수
iter_sec = collections.defaultdict(collections.Counter)  # 시나리오 -> 초 -> 끝난 흐름 수
dur = collections.defaultdict(list)  # (kind, name) -> 지연(ms)
status = collections.defaultdict(collections.Counter)    # name -> 상태코드 수
fail = collections.Counter()
dropped = 0
kind_of = {}

with open(src, newline='', encoding='utf-8') as f:
    r = csv.DictReader(f)
    for row in r:
        m, t = row['metric_name'], int(float(row['timestamp']))
        if m == 'http_req_duration':
            name = row.get('name') or row.get('url')
            extra = row.get('extra_tags') or ''
            kind = next((p.split('=')[1] for p in extra.split('&') if p.startswith('kind=')), '?')
            kind_of[name] = kind
            req_sec[t] += 1
            dur[name].append(float(row['metric_value']))
            status[name][row.get('status')] += 1
            if row.get('expected_response') == 'true':
                ok_sec[t] += 1
            else:
                fail[name] += 1
        elif m == 'iteration_duration':
            iter_sec[row.get('scenario') or '?'][t] += 1
        elif m == 'dropped_iterations':
            dropped += int(float(row['metric_value']))

secs = sorted(req_sec)
t0, t1 = secs[0], secs[-1]
span = t1 - t0 + 1
total, ok = sum(req_sec.values()), sum(ok_sec.values())


def series(counter):
    return [counter.get(t, 0) for t in range(t0, t1 + 1)]


res = {
    'window_epoch': [t0, t1], 'seconds': span,
    'requests': total, 'ok_requests': ok, 'failed_requests': total - ok,
    'rps_avg': round(total / span, 1), 'rps_peak': max(req_sec.values()),
    'ok_rps_avg': round(ok / span, 1), 'ok_rps_peak': max(ok_sec.values()) if ok_sec else 0,
    'dropped_iterations': dropped,
    'tps_by_scenario': {}, 'endpoints': {},
    'per_second': {'rps': series(req_sec), 'ok_rps': series(ok_sec)},
}
all_iter = collections.Counter()
for sc, c in iter_sec.items():
    n = sum(c.values())
    active = max(c) - min(c) + 1
    res['tps_by_scenario'][sc] = {'completed': n, 'tps_avg_active': round(n / active, 2), 'tps_peak': max(c.values()), 'active_seconds': active}
    res['per_second']['tps_' + sc] = series(c)
    all_iter.update(c)
res['tps_total_avg'] = round(sum(all_iter.values()) / span, 1)
res['tps_total_peak'] = max(all_iter.values()) if all_iter else 0
res['per_second']['tps_total'] = series(all_iter)
for name, v in dur.items():
    v.sort()
    res['endpoints'][name] = {
        'kind': kind_of.get(name), 'count': len(v), 'failed': fail.get(name, 0),
        'avg_ms': round(statistics.fmean(v), 1), 'p50_ms': pct(v, 50), 'p95_ms': pct(v, 95), 'p99_ms': pct(v, 99), 'max_ms': round(v[-1], 1),
        'status': dict(status[name]),
    }
json.dump(res, open(out, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print('RPS avg %s peak %s | 성공 RPS avg %s | TPS(끝난 흐름) avg %s peak %s | dropped %s' % (
    res['rps_avg'], res['rps_peak'], res['ok_rps_avg'], res['tps_total_avg'], res['tps_total_peak'], dropped))
for sc, v in res['tps_by_scenario'].items():
    print('  %-6s 완료 %s건, TPS %s (peak %s)' % (sc, v['completed'], v['tps_avg_active'], v['tps_peak']))
for name, v in sorted(res['endpoints'].items()):
    print('  [%s] %s  n=%s fail=%s p95=%s p99=%s %s' % (v['kind'], name, v['count'], v['failed'], v['p95_ms'], v['p99_ms'], v['status']))
