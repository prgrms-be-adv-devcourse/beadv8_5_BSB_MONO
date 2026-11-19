#!/usr/bin/env python3
# 부하 실행 시간창의 Prometheus 시계열을 통째로 받아 JSON으로 저장한다 (Grafana 프록시 query_range).
# 사용: GRAFANA_PW=... python promrange.py <start_utc> <end_utc> <out.json>   예: 2026-11-19T06:10:00Z
import os, sys, json, urllib.request, urllib.parse, base64, datetime

G = "https://grafana.bukang-devops.duckdns.org"
URL = G + "/api/datasources/proxy/uid/prometheus/api/v1/query_range"
PW = os.environ.get("GRAFANA_PW") or sys.exit("GRAFANA_PW 환경변수가 없습니다")
AUTH = "Basic " + base64.b64encode(("admin:" + PW).encode()).decode()
NODE = 'ip-10-0-1-145'
STEP = 15

QUERIES = {
    "node_cpu_pct": '100*(1-avg(rate(node_cpu_seconds_total{mode="idle",instance=~"%s.*"}[1m])))' % NODE,
    "node_cpu_iowait_pct": '100*avg(rate(node_cpu_seconds_total{mode="iowait",instance=~"%s.*"}[1m]))' % NODE,
    "node_load1": 'node_load1{instance=~"%s.*"}' % NODE,
    "node_mem_avail_mb": 'node_memory_MemAvailable_bytes{instance=~"%s.*"}/1048576' % NODE,
    "node_swap_used_mb": '(node_memory_SwapTotal_bytes{instance=~"%s.*"}-node_memory_SwapFree_bytes{instance=~"%s.*"})/1048576' % (NODE, NODE),
    "pod_cpu_cores": 'sum by (pod)(rate(container_cpu_usage_seconds_total{namespace="crewrun",container!=""}[1m]))',
    "pod_mem_ws_mb": 'sum by (pod)(container_memory_working_set_bytes{namespace="crewrun",container!=""})/1048576',
    "hikari_pending": 'hikaricp_connections_pending',
    "hikari_active": 'hikaricp_connections_active',
    "heap_pct": '100*sum by (application)(jvm_memory_used_bytes{area="heap"})/sum by (application)(jvm_memory_max_bytes{area="heap"})',
    "gc_pause_s_per_s": 'sum by (application)(rate(jvm_gc_pause_seconds_sum[1m]))',
    "jvm_threads_live": 'jvm_threads_live_threads',
    "server_rps_by_app": 'sum by (application)(rate(http_server_requests_seconds_count[1m]))',
    "server_5xx_rps_by_app": 'sum by (application)(rate(http_server_requests_seconds_count{status=~"5.."}[1m]))',
    "server_p95_s_by_app": 'histogram_quantile(0.95, sum by (application, le)(rate(http_server_requests_seconds_bucket[1m])))',
    "gateway_rps": 'sum(rate(spring_cloud_gateway_requests_seconds_count[1m]))',
}


def fetch(expr, start, end):
    qs = urllib.parse.urlencode({"query": expr, "start": start, "end": end, "step": STEP})
    req = urllib.request.Request(URL + "?" + qs, headers={"Authorization": AUTH})
    try:
        d = json.load(urllib.request.urlopen(req, timeout=30))
        return d.get("data", {}).get("result", [])
    except Exception as e:
        return {"error": str(e)}


def ts(s):
    return datetime.datetime.strptime(s, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=datetime.timezone.utc).timestamp()


start, end, out = ts(sys.argv[1]) - 30, ts(sys.argv[2]) + 60, sys.argv[3]
data = {"start_utc": sys.argv[1], "end_utc": sys.argv[2], "step_s": STEP, "queries": QUERIES, "series": {}}
for name, expr in QUERIES.items():
    r = fetch(expr, start, end)
    data["series"][name] = r
    print("%-24s %s" % (name, "ERROR " + r["error"] if isinstance(r, dict) else "%d series" % len(r)))
json.dump(data, open(out, "w", encoding="utf-8"))
