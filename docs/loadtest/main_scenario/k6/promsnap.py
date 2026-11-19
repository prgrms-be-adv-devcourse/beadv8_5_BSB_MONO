#!/usr/bin/env python3
# 운영 부하 테스트 지표 스냅샷: Grafana(프록시)->Prometheus 쿼리. 비번은 환경변수 GRAFANA_PW.
# 사용: GRAFANA_PW=... python promsnap.py            # 1회
#       GRAFANA_PW=... python promsnap.py 15 40       # 15초 간격 40회(=10분)
import os, sys, json, time, urllib.request, urllib.parse, base64, datetime
G = "https://grafana.bukang-devops.duckdns.org"
PROM = G + "/api/datasources/proxy/uid/prometheus/api/v1/query"
PW = os.environ.get("GRAFANA_PW")
if not PW:
    sys.exit("GRAFANA_PW 환경변수가 없습니다")
AUTH = "Basic " + base64.b64encode(("admin:" + PW).encode()).decode()
NODE = 'ip-10-0-1-145'  # team05 앱 노드(2 vCPU)

def q(expr):
    r = urllib.request.Request(PROM + "?query=" + urllib.parse.quote(expr), headers={"Authorization": AUTH})
    try:
        d = json.load(urllib.request.urlopen(r, timeout=20))
        return d.get("data", {}).get("result", [])
    except Exception as e:
        return []

def by_app(expr, prec=0):
    out = {}
    for s in q(expr):
        app = s["metric"].get("application") or s["metric"].get("job") or "?"
        try: out[app] = round(float(s["value"][1]), prec)
        except: pass
    return out

def scalar(expr):
    r = q(expr)
    try: return float(r[0]["value"][1])
    except: return None

SVC = ["gateway","order-service","product-service","queue-service","cash-service","member-service"]
def snap():
    ts = datetime.datetime.now().strftime("%H:%M:%S")
    pend = by_app('hikaricp_connections_pending')
    active = by_app('hikaricp_connections_active')
    heappct = by_app('100*jvm_memory_used_bytes{area="heap"}/jvm_memory_max_bytes{area="heap"}', 0)
    gc = by_app('rate(jvm_gc_pause_seconds_sum[1m])', 3)
    cpu = scalar('100*(1-avg(rate(node_cpu_seconds_total{mode="idle",instance=~"%s.*"}[1m])))' % NODE)
    avail = scalar('node_memory_MemAvailable_bytes{instance=~"%s.*"}/1048576' % NODE)
    swapt = scalar('node_memory_SwapTotal_bytes{instance=~"%s.*"}' % NODE)
    swapf = scalar('node_memory_SwapFree_bytes{instance=~"%s.*"}' % NODE)
    swapu = round((swapt-swapf)/1048576) if swapt and swapf is not None else None
    print("[%s] node CPU=%s%%  avail=%sMB  swap_used=%sMB" % (
        ts, round(cpu) if cpu is not None else "?", round(avail) if avail else "?", swapu))
    print("   svc            hikari(pend/active)  heap%  gc/s")
    for s in SVC:
        print("   %-14s %s/%s                heap=%s  gc=%s" % (
            s, pend.get(s,"-"), active.get(s,"-"), heappct.get(s,"-"), gc.get(s,"-")))

interval = int(sys.argv[1]) if len(sys.argv) > 1 else 0
count = int(sys.argv[2]) if len(sys.argv) > 2 else 1
for i in range(count):
    snap()
    if i < count-1: time.sleep(interval)
