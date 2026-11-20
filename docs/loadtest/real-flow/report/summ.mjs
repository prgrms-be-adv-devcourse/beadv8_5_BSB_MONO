// collect.mjs 결과 JSON을 사람이 읽을 요약으로 찍는다
import fs from 'node:fs';
const d = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const f = (x, n = 0) => (x === undefined || Number.isNaN(x) ? '-' : Number(x).toFixed(n));
const by = (arr, key) => Object.fromEntries(arr.map((r) => [r.m[key] ?? '', r.v]));
for (const st of d.stages) {
	console.log(`\n===== 구간 ${st.no} ${st.from} ~ ${st.to} (${st.windowSec}s)`);
	console.log('k6 rps max', f(st.rps[0]?.v, 1), 'vus max', st.vus.map((r) => r.v).join(','));
	console.log('kind p95(ms)', Object.entries(by(st.kindP95, 'kind')).map(([k, v]) => `${k}=${f(v * 1000)}`).join(' '));
	console.log('kind p50(ms)', Object.entries(by(st.kindP50, 'kind')).map(([k, v]) => `${k}=${f(v * 1000)}`).join(' '));
	console.log('node cpu max', st.nodeCpuMax.map((r) => `${r.m.instance}=${f(r.v * 100)}%`).join(' '), 'avg', st.nodeCpuAvg.map((r) => `${r.m.instance}=${f(r.v * 100)}%`).join(' '), 'load1', st.load1.map((r) => `${r.m.instance}=${f(r.v, 1)}`).join(' '));
	console.log('mem avail min MB', st.nodeMemAvailMin.map((r) => `${r.m.instance}=${f(r.v)}`).join(' '));
	console.log('pod cpu max/avg:', st.podCpuMax.sort((a, b) => b.v - a.v).slice(0, 10).map((r) => `${r.m.pod.replace(/-[a-z0-9]+-[a-z0-9]+$/, '')}=${f(r.v, 2)}/${f(st.podCpuAvg.find((x) => x.m.pod === r.m.pod)?.v, 2)}`).join(' '));
	console.log('svc reqs/avg/max ms:', st.svcReqs.sort((a, b) => b.v - a.v).map((r) => `${r.m.job}=${f(r.v)}/${f(st.svcAvgMs.find((x) => x.m.job === r.m.job)?.v)}/${f(st.svcMaxMs.find((x) => x.m.job === r.m.job)?.v)}`).join(' '));
	console.log('5xx', st.svc5xx.filter((r) => r.v > 0).map((r) => `${r.m.job}=${f(r.v)}`).join(' '));
	console.log('hikari active/pending/max/acq:', st.hikariMax.map((r) => `${r.m.job}=${f(st.hikariActiveMax.find((x) => x.m.job === r.m.job)?.v)}/${f(st.hikariPendingMax.find((x) => x.m.job === r.m.job)?.v)}/${f(r.v)}/${f(st.hikariAcquireMs.find((x) => x.m.job === r.m.job)?.v, 1)}ms`).join(' '));
	console.log('heap% max', st.heapPct.map((r) => `${r.m.job}=${f(r.v * 100)}`).join(' '));
	console.log('gc pause s', st.gcPause.filter((r) => r.v > 0.05).map((r) => `${r.m.job}=${f(r.v, 2)}`).join(' '));
	console.log('mysql threads running max', st.mysqlThreadsRunning.map((r) => f(r.v)).join(','), 'qps max', st.mysqlQps.map((r) => f(r.v)).join(','), 'row lock waits', st.mysqlRowLockWaits.map((r) => f(r.v)).join(','));
	const uris = st.uriAvgMs.filter((r) => !Number.isNaN(r.v)).map((r) => ({ ...r.m, avg: r.v, n: st.uriCount.find((x) => x.m.job === r.m.job && x.m.uri === r.m.uri && x.m.method === r.m.method)?.v, max: st.uriMaxMs.find((x) => x.m.job === r.m.job && x.m.uri === r.m.uri && x.m.method === r.m.method)?.v }));
	console.log('-- server uri avg ms top 15 (n>=20)');
	uris.filter((u) => u.n >= 20).sort((a, b) => b.avg - a.avg).slice(0, 15).forEach((u) => console.log(`  ${u.job} ${u.method} ${u.uri} n=${f(u.n)} avg=${f(u.avg, 1)} max=${f(u.max)}`));
	console.log('-- server uri by count top 12');
	uris.sort((a, b) => b.n - a.n).slice(0, 12).forEach((u) => console.log(`  ${u.job} ${u.method} ${u.uri} n=${f(u.n)} avg=${f(u.avg, 1)}`));
	const reqs = st.nameReqs.map((r) => ({ ...r.m, n: r.v, p95: st.nameP95.find((x) => x.m.name === r.m.name)?.v }));
	console.log('-- k6 request p95 top 15 (n>=20)');
	reqs.filter((r) => r.n >= 20).sort((a, b) => b.p95 - a.p95).slice(0, 15).forEach((r) => console.log(`  [${r.kind}] ${r.name} n=${f(r.n)} p95=${f(r.p95 * 1000)}ms`));
	console.log('-- k6 failures', st.nameFail.filter((r) => r.v >= 1).map((r) => `${r.m.name} ${r.m.status}=${f(r.v)}`).join(' | '));
}
console.log('\n===== counters');
for (const [c, rows] of Object.entries(d.counters)) console.log(c, rows.map((r) => `${r.m.stage}/${r.m.flow}=${r.v}`).join(' '));
console.log('X peak', Object.entries(d.xPeak).map(([w, rows]) => `${w}: ` + rows.map((r) => `${r.m.stage}=${f(r.v, 2)}`).join(' ')).join(' | '));
