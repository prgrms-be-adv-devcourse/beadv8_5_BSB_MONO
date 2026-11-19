// real-flow 운영 실행 결과를 Prometheus에서 모은다 → JSON (아티펙트 재료)
// 사용: node collect.mjs <testid> <구간1 오픈 UTC ISO> <구간 간격초> <구간 수> <끝 UTC ISO>
const [, , TID, OPEN1, GAP, N, END] = process.argv;
const auth = 'Basic ' + Buffer.from('crewrun:' + process.env.RW).toString('base64');
const BASE = 'https://prom.bukang-devops.duckdns.org/api/v1';

async function q(query, time) {
	const u = new URL(BASE + '/query');
	u.searchParams.set('query', query);
	u.searchParams.set('time', String(time));
	const r = await (await fetch(u, { headers: { Authorization: auth } })).json();
	if (r.status !== 'success') throw new Error(query + ' ' + JSON.stringify(r));
	return r.data.result.map((s) => ({ m: s.metric, v: Number(s.value[1]) }));
}
async function qr(query, start, end, step) {
	const u = new URL(BASE + '/query_range');
	u.searchParams.set('query', query);
	u.searchParams.set('start', String(start));
	u.searchParams.set('end', String(end));
	u.searchParams.set('step', String(step));
	const r = await (await fetch(u, { headers: { Authorization: auth } })).json();
	if (r.status !== 'success') throw new Error(query + ' ' + JSON.stringify(r));
	return r.data.result.map((s) => ({ m: s.metric, values: s.values.map(([t, v]) => [t, Number(v)]) }));
}

const open1 = Date.parse(OPEN1) / 1000;
const gap = Number(GAP);
const end = Date.parse(END) / 1000;
const t0 = open1 - 120;
const runSec = Math.round(end - t0);
const k = `testid="${TID}"`;
const out = { testid: TID, open1: OPEN1, end: END, stages: [] };

// 구간마다: 오픈 T-60초 ~ T+240초(다음 구간 직전). 마지막 구간은 끝까지
for (let i = 0; i < Number(N); i++) {
	const from = open1 + i * gap - 60;
	const to = i === Number(N) - 1 ? end : open1 + (i + 1) * gap - 60;
	const w = Math.round(to - from);
	const at = to;
	const st = { no: i + 1, from: new Date(from * 1000).toISOString(), to: new Date(to * 1000).toISOString(), windowSec: w };
	// k6 쪽: 종류별·요청별 p95(구간 안 최대), 요청 수·실패
	st.kindP95 = await q(`max by (kind) (max_over_time(k6_http_req_duration_p95{${k},kind!="setup"}[${w}s]))`, at);
	st.kindP50 = await q(`max by (kind) (max_over_time(k6_http_req_duration_p50{${k},kind!="setup"}[${w}s]))`, at);
	st.kindWaitP95 = await q(`max by (kind) (max_over_time(k6_http_req_waiting_p95{${k},kind!="setup"}[${w}s]))`, at);
	st.nameP95 = await q(`max by (name, kind) (max_over_time(k6_http_req_duration_p95{${k},kind!="setup"}[${w}s]))`, at);
	st.nameReqs = await q(`sum by (name, kind) (increase(k6_http_reqs_total{${k},kind!="setup"}[${w}s]))`, at);
	st.nameFail = await q(`sum by (name, kind, status) (increase(k6_http_reqs_total{${k},kind!="setup",expected_response="false"}[${w}s]))`, at);
	st.rps = await q(`max_over_time(sum(rate(k6_http_reqs_total{${k},kind!="setup"}[30s]))[${w}s:5s])`, at);
	st.vus = await q(`max_over_time(k6_vus{${k}}[${w}s])`, at);
	// 결과 카운터(사람 단위): 구간 태그 기준이라 끝 시각에 누적 최대를 본다
	// 서버 쪽 (같은 시간)
	st.nodeCpuMax = await q(`max_over_time((1 - avg by (instance) (rate(node_cpu_seconds_total{mode="idle"}[1m])))[${w}s:15s])`, at);
	st.nodeCpuAvg = await q(`avg_over_time((1 - avg by (instance) (rate(node_cpu_seconds_total{mode="idle"}[1m])))[${w}s:15s])`, at);
	st.load1 = await q(`max_over_time(node_load1[${w}s])`, at);
	st.podCpuMax = await q(`max_over_time((sum by (pod) (rate(container_cpu_usage_seconds_total{container!="",pod!=""}[1m])))[${w}s:15s])`, at);
	st.podCpuAvg = await q(`avg_over_time((sum by (pod) (rate(container_cpu_usage_seconds_total{container!="",pod!=""}[1m])))[${w}s:15s])`, at);
	st.svcReqs = await q(`sum by (job) (increase(http_server_requests_seconds_count{uri!~"/actuator.*"}[${w}s]))`, at);
	st.svcAvgMs = await q(`sum by (job) (increase(http_server_requests_seconds_sum{uri!~"/actuator.*"}[${w}s])) / sum by (job) (increase(http_server_requests_seconds_count{uri!~"/actuator.*"}[${w}s])) * 1000`, at);
	st.svcMaxMs = await q(`max by (job) (max_over_time(http_server_requests_seconds_max{uri!~"/actuator.*"}[${w}s])) * 1000`, at);
	st.uriAvgMs = await q(`sum by (job, method, uri) (increase(http_server_requests_seconds_sum{uri!~"/actuator.*"}[${w}s])) / sum by (job, method, uri) (increase(http_server_requests_seconds_count{uri!~"/actuator.*"}[${w}s])) * 1000`, at);
	st.uriCount = await q(`sum by (job, method, uri) (increase(http_server_requests_seconds_count{uri!~"/actuator.*"}[${w}s]))`, at);
	st.uriMaxMs = await q(`max by (job, method, uri) (max_over_time(http_server_requests_seconds_max{uri!~"/actuator.*"}[${w}s])) * 1000`, at);
	st.svc5xx = await q(`sum by (job) (increase(http_server_requests_seconds_count{status=~"5.."}[${w}s]))`, at);
	st.hikariActiveMax = await q(`max by (job) (max_over_time(hikaricp_connections_active[${w}s]))`, at);
	st.hikariPendingMax = await q(`max by (job) (max_over_time(hikaricp_connections_pending[${w}s]))`, at);
	st.hikariMax = await q(`max by (job) (hikaricp_connections_max)`, at);
	st.hikariAcquireMs = await q(`sum by (job) (increase(hikaricp_connections_acquire_seconds_sum[${w}s])) / sum by (job) (increase(hikaricp_connections_acquire_seconds_count[${w}s])) * 1000`, at);
	st.heapPct = await q(`max by (job) (max_over_time((sum by (job, instance) (jvm_memory_used_bytes{area="heap"}) / sum by (job, instance) (jvm_memory_max_bytes{area="heap"}))[${w}s:15s]))`, at);
	st.gcPause = await q(`sum by (job) (increase(jvm_gc_pause_seconds_sum[${w}s]))`, at);
	st.threadsMax = await q(`max by (job) (max_over_time(jvm_threads_live_threads[${w}s]))`, at);
	st.mysqlThreadsRunning = await q(`max_over_time(mysql_global_status_threads_running[${w}s])`, at);
	st.mysqlQps = await q(`max_over_time(rate(mysql_global_status_queries[1m])[${w}s:15s])`, at);
	st.mysqlRowLockWaits = await q(`increase(mysql_global_status_innodb_row_lock_waits[${w}s])`, at);
	st.nodeMemAvailMin = await q(`min_over_time(node_memory_MemAvailable_bytes[${w}s]) / 1048576`, at);
	out.stages.push(st);
}

// 실행 전체
out.counters = {};
for (const c of ['booking_completed', 'booking_sold_out', 'booking_failed_final', 'pay_busy_first', 'pay_retry_success', 'party_applied', 'party_seat_short', 'flow_broken']) {
	out.counters[c] = await q(`sum by (stage, flow) (max_over_time(k6_${c}_total{${k}}[${runSec}s]))`, end);
}
// 결제 완료 예매의 초당 처리 (X): 10초·30초·1분 이동 창의 최대
out.xPeak = {};
for (const win of ['10s', '30s', '1m']) {
	out.xPeak[win] = await q(`max_over_time(sum by (stage) (rate(k6_booking_completed_total{${k}}[${win}]))[${runSec}s:5s])`, end);
}
// 그래프용 시계열 (10초 간격)
out.series = {
	nodeCpu: await qr(`1 - avg by (instance) (rate(node_cpu_seconds_total{mode="idle"}[1m]))`, t0, end, 10),
	podCpu: await qr(`sum by (pod) (rate(container_cpu_usage_seconds_total{container!="",pod!=""}[1m]))`, t0, end, 10),
	k6Rps: await qr(`sum(rate(k6_http_reqs_total{${k},kind!="setup"}[30s]))`, t0, end, 10),
	k6P95ByKind: await qr(`max by (kind) (k6_http_req_duration_p95{${k},kind!="setup"})`, t0, end, 10),
	completedRate: await qr(`sum(rate(k6_booking_completed_total{${k}}[30s]))`, t0, end, 10),
	vus: await qr(`k6_vus{${k}}`, t0, end, 10),
	svcAvgMs: await qr(`sum by (job) (rate(http_server_requests_seconds_sum{uri!~"/actuator.*"}[1m])) / sum by (job) (rate(http_server_requests_seconds_count{uri!~"/actuator.*"}[1m])) * 1000`, t0, end, 10),
};
console.log(JSON.stringify(out));
