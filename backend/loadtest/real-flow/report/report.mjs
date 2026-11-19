// 운영 real-flow X 실행 결과 HTML (외부 CDN 없음, 그래프는 SVG)
// 사용: node report.mjs <metrics2.json> <metrics1.json> <캡처 폴더> <출력.html>
import fs from 'node:fs';
const [, , M2, M1, CAP, OUT] = process.argv;
const d = JSON.parse(fs.readFileSync(M2, 'utf8'));
const d1 = JSON.parse(fs.readFileSync(M1, 'utf8'));
const img = (f) => `data:image/png;base64,${fs.readFileSync(`${CAP}/${f}`).toString('base64')}`;

const n0 = (x) => (x === undefined || x === null || Number.isNaN(x) ? '-' : Math.round(x).toLocaleString('ko-KR'));
const n1 = (x) => (x === undefined || Number.isNaN(x) ? '-' : (Math.round(x * 10) / 10).toLocaleString('ko-KR'));
const n2 = (x) => (x === undefined || Number.isNaN(x) ? '-' : x.toFixed(2));
const ms = (sec) => (sec === undefined ? '-' : n0(sec * 1000));
const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const S = d.stages;
const LIMIT = { read: 300, queue: 200, write: 1000 };
const KIND_KO = { page: '화면', read: '조회', queue: '대기열', write: '쓰기', auth: '로그인' };

// ── 숫자 뽑기 ───────────────────────────────────────────
const cnt = (name, stage, flow) => d.counters[name].filter((r) => (!stage || r.m.stage === stage) && (!flow || r.m.flow === flow)).reduce((a, r) => a + r.v, 0);
const stages = ['300x1', '300x2', '300x3'];
const xPeak = (win, st) => d.xPeak[win].find((r) => r.m.stage === st)?.v;
const kindP = (st, key, kind) => st[key].find((r) => r.m.kind === kind)?.v;
const inst = (arr, ip) => arr.find((r) => (r.m.instance || '').startsWith(ip))?.v;
const MAIN = 'ip-10-0-1-145';
const DATA = '10.0.2.222';
const sumReq = (st, page) => st.nameReqs.filter((r) => (page ? r.m.kind === 'page' : r.m.kind !== 'page')).reduce((a, r) => a + r.v, 0);
const sumFail = (st, page) => st.nameFail.filter((r) => (page ? r.m.kind === 'page' : r.m.kind !== 'page')).reduce((a, r) => a + r.v, 0);

// ── 요청 이름 → 담당 서비스 ─────────────────────────────
function serviceOf(name, kind) {
	if (kind === 'page') return 'web (Next.js)';
	const p = name.split(' ')[1] || '';
	const seg = p.split('/')[1];
	return { order: 'order', product: 'product', member: 'member', cash: 'cash', file: 'file', notification: 'notification', queue: 'queue', auth: 'member' }[seg] || seg;
}

// ── SVG 선 그래프 ──────────────────────────────────────
function lineChart({ series, height = 230, yMax, yFmt = (v) => v, title, marks = [] }) {
	const W = 860, H = height, L = 52, R = 12, T = 14, B = 30;
	const all = series.flatMap((s) => s.points);
	const xs = all.map((p) => p[0]);
	const x0 = RANGE[0], x1 = RANGE[1];
	const ymax = yMax ?? (Math.max(...all.map((p) => p[1])) * 1.1 || 1);
	const X = (t) => L + ((t - x0) / (x1 - x0)) * (W - L - R);
	const Y = (v) => T + (1 - Math.min(v, ymax) / ymax) * (H - T - B);
	let g = '';
	for (let i = 0; i <= 4; i++) {
		const v = (ymax * i) / 4;
		g += `<line x1="${L}" x2="${W - R}" y1="${Y(v)}" y2="${Y(v)}" class="gl"/><text x="${L - 6}" y="${Y(v) + 4}" class="yl">${yFmt(v)}</text>`;
	}
	for (let t = Math.ceil(x0 / 120) * 120; t <= x1; t += 120) {
		const kst = new Date((t + 9 * 3600) * 1000).toISOString().slice(11, 16);
		g += `<text x="${X(t)}" y="${H - 8}" class="xl">${kst}</text>`;
	}
	for (const m of marks) {
		g += `<line x1="${X(m.t)}" x2="${X(m.t)}" y1="${T}" y2="${H - B}" class="mk"/><text x="${X(m.t) + 4}" y="${T + 11}" class="mkl">${m.label}</text>`;
	}
	const clip = (pts) => pts.filter((p) => p[0] >= x0 && p[0] <= x1);
	const lines = series.map((s) => ({ ...s, points: clip(s.points) })).map((s) => `<polyline fill="none" stroke="${s.color}" stroke-width="${s.width || 2}" ${s.dash ? 'stroke-dasharray="5 4"' : ''} points="${s.points.map((p) => `${X(p[0]).toFixed(1)},${Y(p[1]).toFixed(1)}`).join(' ')}"/>`).join('');
	const legend = series.map((s) => `<span><i class="sw" style="background:${s.color}"></i>${s.label}</span>`).join('');
	return `${title ? `<p class="chcap">${title}</p>` : ''}<div class="legend">${legend}</div><svg viewBox="0 0 ${W} ${H}" class="chart" role="img">${g}${lines}</svg>`;
}
const RANGE = [Date.parse(d.open1) / 1000 - 90, Date.parse(d.end) / 1000];
const opens = [0, 1, 2].map((i) => ({ t: Date.parse(d.open1) / 1000 + i * 240, label: `구간 ${i + 1} 오픈 (${i + 1}쌍)` }));
const ser = d.series;
const nodeMain = ser.nodeCpu.find((s) => s.m.instance === MAIN).values.map(([t, v]) => [t, v * 100]);
const nodeData = ser.nodeCpu.find((s) => (s.m.instance || '').startsWith(DATA)).values.map(([t, v]) => [t, v * 100]);
const rps = ser.k6Rps[0].values;
const comp = ser.completedRate[0]?.values || [];
const p95k = (k) => (ser.k6P95ByKind.find((s) => s.m.kind === k)?.values || []).map(([t, v]) => [t, v]);
const podTop = ['bukang-web', 'bukang-order-service', 'bukang-product-service', 'traefik', 'bukang-gateway', 'bukang-file-service', 'bukang-member-service'];
const podColors = ['#d85a30', '#1d7a8c', '#3b6d11', '#854f0b', '#7a5bd0', '#9aa2a9', '#c2417a'];
const podSeries = podTop.map((p, i) => {
	const s = ser.podCpu.find((x) => x.m.pod.startsWith(p));
	return s ? { label: p.replace('bukang-', ''), color: podColors[i], points: s.values } : null;
}).filter(Boolean);

// ── 표: 사람 단위 결과 ──────────────────────────────────
const peopleRows = stages.map((st, i) => {
	const indiv = cnt('booking_completed', st, 'individual');
	const member = cnt('booking_completed', st, 'member');
	return `<tr><td>구간 ${i + 1} <span class="ssub">대회 ${i + 1}쌍 · ${n0(300 * (i + 1))}명 + 보기만 ${30 * (i + 1)}명</span></td>
	<td>${n0(240 * (i + 1))}</td><td>${n0(indiv)} <span class="ssub">/ 정원 ${n0(168 * (i + 1))}</span></td><td>${n0(cnt('booking_sold_out', st))}</td>
	<td>${n0(cnt('party_applied', st))} <span class="ssub">/ ${42 * (i + 1)}파티</span></td><td>${n0(cnt('party_seat_short', st))}</td><td>${n0(member)}</td>
	<td>${n0(cnt('pay_busy_first', st))}</td><td>${n0(cnt('booking_failed_final', st))}</td><td>${n0(cnt('flow_broken', st))}</td></tr>`;
}).join('');

// ── 표: X ──────────────────────────────────────────────
const xRows = stages.map((st, i) => {
	const a = xPeak('30s', st), base = xPeak('30s', '300x1');
	const ideal = base * (i + 1);
	return `<tr><td>구간 ${i + 1} (${i + 1}쌍)</td><td>${n2(xPeak('10s', st))}</td><td><b>${n2(a)}</b></td><td>${n2(xPeak('1m', st))}</td>
	<td>${n2(ideal)}</td><td>${n0((a / ideal) * 100)}%</td></tr>`;
}).join('');

// ── 표: 구간별 증상(k6) ─────────────────────────────────
const symRows = S.map((st, i) => {
	const cell = (k) => {
		const v = kindP(st, 'kindP95', k);
		const lim = LIMIT[k];
		const cls = lim ? (v * 1000 <= lim ? 'ok' : 'crit') : '';
		return `<td><span class="${cls ? 'b ' + cls : ''}">${ms(v)}</span></td>`;
	};
	const pageFail = sumFail(st, true), pageReq = sumReq(st, true), apiFail = sumFail(st, false), apiReq = sumReq(st, false);
	return `<tr><td>구간 ${i + 1}</td><td>${n0(st.rps[0]?.v)}</td>${cell('read')}${cell('queue')}${cell('write')}${cell('auth')}${cell('page')}
	<td>${n2((apiFail / apiReq) * 100)}% <span class="ssub">${n0(apiFail)}/${n0(apiReq)}</span></td>
	<td>${pageFail ? `<span class="b crit">${n1((pageFail / pageReq) * 100)}%</span>` : '0%'} <span class="ssub">${n0(pageFail)}/${n0(pageReq)}</span></td></tr>`;
}).join('');

// ── 표: 구간별 원인(서버) ───────────────────────────────
const causeRows = S.map((st, i) => {
	const web = st.podCpuMax.find((r) => r.m.pod.startsWith('bukang-web'))?.v;
	return `<tr><td>구간 ${i + 1}</td><td><b class="${inst(st.nodeCpuMax, MAIN) > 0.9 ? 'up' : ''}">${n0(inst(st.nodeCpuMax, MAIN) * 100)}%</b> <span class="ssub">평균 ${n0(inst(st.nodeCpuAvg, MAIN) * 100)}%</span></td>
	<td>${n1(inst(st.load1, MAIN))} <span class="ssub">(코어당 ${n1(inst(st.load1, MAIN) / 2)})</span></td><td>${n0(inst(st.nodeMemAvailMin, MAIN))}MB</td>
	<td>${n0(inst(st.nodeCpuMax, DATA) * 100)}%</td><td>${n2(web)}</td>
	<td>${n0(st.mysqlThreadsRunning[0]?.v)}</td><td>${n0(st.mysqlQps[0]?.v)}</td>
	<td>${n0(Math.max(...st.hikariPendingMax.map((r) => r.v)))}</td></tr>`;
}).join('');

// ── 표: 파드 CPU ───────────────────────────────────────
const pods = [...new Set(S.flatMap((st) => st.podCpuMax.map((r) => r.m.pod.replace(/-[a-z0-9]{8,10}-[a-z0-9]{5}$/, ''))))];
const podCpuOf = (st, p, key) => st[key].filter((r) => r.m.pod.replace(/-[a-z0-9]{8,10}-[a-z0-9]{5}$/, '') === p).reduce((a, r) => Math.max(a, r.v), 0);
const podRows = pods.map((p) => ({ p, v: podCpuOf(S[2], p, 'podCpuMax') })).sort((a, b) => b.v - a.v).slice(0, 11).map(({ p }) => {
	const cells = S.map((st) => `<td>${n2(podCpuOf(st, p, 'podCpuMax'))} <span class="ssub">${n2(podCpuOf(st, p, 'podCpuAvg'))}</span></td>`).join('');
	const max3 = podCpuOf(S[2], p, 'podCpuMax');
	return `<tr><td>${esc(p.replace('bukang-', ''))}</td>${cells}<td><span class="bar" style="width:${Math.round(max3 / 0.8 * 120)}px"></span></td></tr>`;
}).join('');

// ── 표: API별 (k6 사용자 쪽) ────────────────────────────
const names = new Map();
S.forEach((st, i) => st.nameReqs.forEach((r) => {
	const key = r.m.name;
	if (!names.has(key)) names.set(key, { name: key, kind: r.m.kind, n: [0, 0, 0], p95: [], fail: [0, 0, 0] });
	names.get(key).n[i] = r.v;
}));
S.forEach((st, i) => st.nameP95.forEach((r) => { if (names.has(r.m.name)) names.get(r.m.name).p95[i] = r.v; }));
S.forEach((st, i) => st.nameFail.forEach((r) => { if (names.has(r.m.name)) names.get(r.m.name).fail[i] += r.v; }));
const kindOrder = ['write', 'queue', 'read', 'auth', 'page'];
const apiRows = [...names.values()].filter((r) => r.n.reduce((a, b) => a + b) >= 10)
	.sort((a, b) => kindOrder.indexOf(a.kind) - kindOrder.indexOf(b.kind) || (b.p95[2] || 0) - (a.p95[2] || 0))
	.map((r) => {
		const lim = LIMIT[r.kind];
		const p = [0, 1, 2].map((i) => r.p95[i]).map((v) => {
			const cls = lim && v !== undefined ? (v * 1000 <= lim ? 'ok' : 'crit') : '';
			return `<td><span class="${cls ? 'b ' + cls : ''}">${ms(v)}</span></td>`;
		}).join('');
		const failSum = r.fail.reduce((a, b) => a + b);
		const verdict = !lim ? (failSum ? `<span class="pill crit">오류 ${n0(failSum)}</span>` : '<span class="pill warn">-</span>')
			: r.p95[0] * 1000 <= lim ? '<span class="pill ok">통과</span>' : r.p95[0] * 1000 <= lim * 3 ? '<span class="pill warn">초과</span>' : '<span class="pill crit">3배↑</span>';
		return `<tr class="k-${r.kind}"><td>${KIND_KO[r.kind]}</td><td class="l">${esc(r.name)}</td><td class="l">${esc(serviceOf(r.name, r.kind))}</td>
		<td>${n0(r.n[0])}</td><td>${n0(r.n[1])}</td><td>${n0(r.n[2])}</td>${p}<td>${verdict}</td></tr>`;
	}).join('');

// ── 표: 서버 안 API 처리 시간 (Spring 지표) ─────────────
const uriMap = new Map();
S.forEach((st, i) => st.uriAvgMs.forEach((r) => {
	if (Number.isNaN(r.v) || r.m.job === 'gateway') return;
	const key = `${r.m.job} ${r.m.method} ${r.m.uri}`;
	if (!uriMap.has(key)) uriMap.set(key, { ...r.m, avg: [], n: [0, 0, 0], max: [] });
	uriMap.get(key).avg[i] = r.v;
}));
S.forEach((st, i) => st.uriCount.forEach((r) => { const k = `${r.m.job} ${r.m.method} ${r.m.uri}`; if (uriMap.has(k)) uriMap.get(k).n[i] = r.v; }));
S.forEach((st, i) => st.uriMaxMs.forEach((r) => { const k = `${r.m.job} ${r.m.method} ${r.m.uri}`; if (uriMap.has(k)) uriMap.get(k).max[i] = r.v; }));
const uris = [...uriMap.values()].filter((u) => u.n[2] >= 50);
const uriRows = uris.sort((a, b) => (b.avg[2] || 0) * b.n[2] - (a.avg[2] || 0) * a.n[2]).slice(0, 22).map((u) => {
	const busy = ((u.avg[2] || 0) * u.n[2]) / 1000;
	return `<tr><td class="l">${esc(u.job.replace('-service', ''))}</td><td class="l">${esc(u.method)} ${esc(u.uri)}</td><td>${n0(u.n[2])}</td>
	<td>${n1(u.avg[0])}</td><td>${n1(u.avg[1])}</td><td><b>${n1(u.avg[2])}</b></td><td>${n0(u.max[2])}</td><td>${n1(busy)}초 <span class="bar" style="width:${Math.round(Math.min(busy, 170) / 170 * 110)}px"></span></td></tr>`;
}).join('');

// ── 표: 서비스 합계 ─────────────────────────────────────
const jobs = [...new Set(S[2].svcReqs.map((r) => r.m.job))];
const svcRows = jobs.map((j) => ({ j, n: S[2].svcReqs.find((r) => r.m.job === j)?.v || 0 })).sort((a, b) => b.n - a.n).map(({ j }) => {
	const f = (st, key) => st[key].find((r) => r.m.job === j)?.v;
	return `<tr><td class="l">${esc(j)}</td>${S.map((st) => `<td>${n0(f(st, 'svcReqs'))}</td>`).join('')}${S.map((st) => `<td>${n1(f(st, 'svcAvgMs'))}</td>`).join('')}
	<td>${n0(f(S[2], 'svcMaxMs'))}</td><td>${n0(Math.max(...S.map((st) => f(st, 'hikariActiveMax') || 0)))}/${n0(f(S[2], 'hikariMax'))}</td><td>${n0(Math.max(...S.map((st) => f(st, 'hikariPendingMax') || 0)))}</td>
	<td>${n0(Math.max(...S.map((st) => (f(st, 'heapPct') || 0) * 100)))}%</td><td>${n2(f(S[2], 'gcPause'))}</td></tr>`;
}).join('');

// 1차 실행(쿠키 비움 오류) 큐 풀 포화 근거
const q1 = d1.stages[1];
const qPend1 = q1.hikariPendingMax.find((r) => r.m.job === 'queue-service')?.v;
const qAcq1 = q1.hikariAcquireMs.find((r) => r.m.job === 'queue-service')?.v;
const qParty1 = q1.uriAvgMs.find((r) => r.m.job === 'queue-service' && r.m.uri === '/api/v1/queue/parties');
const qPartyMax1 = q1.uriMaxMs.find((r) => r.m.job === 'queue-service' && r.m.uri === '/api/v1/queue/parties');

const s3 = S[2], s1 = S[0];
const X30 = xPeak('30s', '300x3');

const html = `<!doctype html>
<html lang="ko"><head><meta charset="utf8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<title>크루런 운영 부하 테스트 — 예매 오픈 몰림 X 측정 (real-flow)</title>
<style>
:root{--bg:#f6f7f8;--surface:#fff;--fg:#1b1f23;--muted:#5c646c;--border:#e4e7ea;--accent:#1d7a8c;--bar:#cfd6da;--navbg:#eef1f3;--navactive:#dceaed;
--ok:#3b6d11;--ok-bg:#eaf3de;--warn:#854f0b;--warn-bg:#faeeda;--crit:#a32d2d;--crit-bg:#fcebeb;--d19:#d85a30;--sans:system-ui,-apple-system,sans-serif;--head:"Space Grotesk",system-ui,sans-serif}
@media (prefers-color-scheme:dark){:root{--bg:#15181b;--surface:#1e2226;--fg:#e8eaec;--muted:#9aa2a9;--border:#2d3338;--accent:#5dcaba;--bar:#3a424a;--navbg:#1a1e22;--navactive:#243036;
--ok:#97c459;--ok-bg:#223016;--warn:#f0b24a;--warn-bg:#36280f;--crit:#f09595;--crit-bg:#3a1a1a;--d19:#f0997b;color-scheme:dark}}
*{box-sizing:border-box}body{background:var(--bg);color:var(--fg);font-family:var(--sans);line-height:1.6;margin:0;font-feature-settings:"tnum"}
.shell{display:flex;min-height:100vh;max-width:1280px;margin:0 auto}
nav{flex:0 0 240px;background:var(--navbg);border-right:1px solid var(--border);padding:26px 16px;position:sticky;top:0;height:100vh;overflow-y:auto}
nav .brand{font-family:var(--head);font-weight:600;font-size:16px;line-height:1.35;margin:0 0 4px}nav .brandsub{color:var(--muted);font-size:12px;margin:0 0 22px}
nav .grp{color:var(--muted);font-size:11px;font-weight:600;letter-spacing:.04em;margin:18px 6px 7px}
nav a{display:block;padding:8px 11px;border-radius:9px;color:var(--fg);text-decoration:none;font-size:13.5px;cursor:pointer;margin-bottom:2px}
nav a:hover,nav a.active{background:var(--navactive)}nav a.active{font-weight:600}nav a .tag{font-size:10.5px;color:var(--muted);float:right;font-weight:400}
main{flex:1;min-width:0;padding:34px 22px 56px}.page{display:none}.page.show{display:block}
h1{font-family:var(--head);font-weight:600;font-size:25px;margin:0 0 4px}h2{font-family:var(--head);font-weight:500;font-size:17px;margin:0 0 6px}
h3{font-size:14.5px;margin:16px 0 6px;color:var(--accent)}
.sub{color:var(--muted);font-size:13.5px;margin:0 0 18px}
.card{background:var(--surface);border:1px solid var(--border);border-radius:14px;padding:20px 18px;margin-bottom:16px}
.verdict{border-left:4px solid var(--crit);border-radius:0 14px 14px 0}.verdict .big{font-family:var(--head);font-size:19px;font-weight:600;margin:0 0 6px}.verdict .big b{color:var(--crit)}
.good{border-left:4px solid var(--ok);border-radius:0 14px 14px 0}
.chcap{color:var(--muted);font-size:12.5px;margin:0 0 6px}
table{width:100%;border-collapse:collapse;font-size:12.8px}th,td{text-align:right;padding:7px 7px;border-bottom:1px solid var(--border);white-space:nowrap;vertical-align:top}
th:first-child,td:first-child,td.l,th.l{text-align:left}th{color:var(--muted);font-weight:500;font-size:11.5px}td.wrap{white-space:normal;text-align:left}
.tscroll{overflow-x:auto}.b{font-weight:600}.b.up,.up{color:var(--crit)}.b.ok{color:var(--ok)}.b.crit{color:var(--crit)}
.pill{display:inline-block;font-size:11px;font-weight:500;padding:2px 9px;border-radius:999px}.pill.ok{background:var(--ok-bg);color:var(--ok)}.pill.warn{background:var(--warn-bg);color:var(--warn)}.pill.crit{background:var(--crit-bg);color:var(--crit)}
ul,ol{margin:8px 0 0;padding-left:20px}li{margin:5px 0;font-size:13.5px}.mut{color:var(--muted);font-size:13px;margin:12px 0 0}.note{font-size:12px;color:var(--muted);margin-top:10px}
b.hi{color:var(--d19)}.ssub{color:var(--muted);font-size:11px;margin-left:4px;font-weight:400}
.defn{background:var(--navbg);border:1px solid var(--border);border-radius:11px;padding:13px 15px;font-size:13px;margin-bottom:14px}.defn b{color:var(--accent)}
.kpis{display:grid;grid-template-columns:repeat(4,1fr);gap:10px;margin-bottom:16px}.kpi{background:var(--surface);border:1px solid var(--border);border-radius:12px;padding:12px 14px}
.kpi .v{font-family:var(--head);font-size:22px;font-weight:600}.kpi .k{color:var(--muted);font-size:12px}.kpi .s{color:var(--muted);font-size:11.5px}
.flow{display:flex;flex-wrap:wrap;align-items:center;gap:5px;margin:8px 0 12px}.step{display:inline-block;background:var(--navbg);border:1px solid var(--border);border-radius:7px;padding:3px 9px;font-size:12.5px}.flow .sep{color:var(--muted)}
.bar{display:inline-block;height:9px;background:var(--accent);border-radius:3px;vertical-align:middle;opacity:.75}
.legend{display:flex;flex-wrap:wrap;gap:12px;font-size:12px;color:var(--muted);margin:2px 0 4px}.legend span{display:inline-flex;align-items:center;gap:6px}.sw{width:12px;height:12px;border-radius:3px;display:inline-block}
svg.chart{width:100%;height:auto}svg .gl{stroke:var(--border)}svg .yl{fill:var(--muted);font-size:11px;text-anchor:end}svg .xl{fill:var(--muted);font-size:11px;text-anchor:middle}
svg .mk{stroke:var(--crit);stroke-dasharray:3 3;opacity:.6}svg .mkl{fill:var(--crit);font-size:10.5px}
.imp{border:1px solid var(--border);border-radius:11px;padding:12px 14px;margin:10px 0}.imp .t{font-weight:600;font-size:14px}.imp .row{font-size:13px;margin:4px 0}.imp .row b{color:var(--accent);display:inline-block;min-width:74px}
tr.k-page td{color:var(--muted)}figure{margin:0 0 14px}figure img{width:100%;border:1px solid var(--border);border-radius:10px}figcaption{font-size:12.5px;color:var(--muted);margin-top:4px}
code{font-size:12px;background:var(--navbg);padding:1px 5px;border-radius:5px}
@media (max-width:820px){.shell{display:block}nav{position:static;height:auto}.kpis{grid-template-columns:repeat(2,1fr)}}
</style></head><body><div class="shell">
<nav>
<p class="brand">크루런 예매 오픈 몰림<br>운영 부하 테스트 (real-flow)</p>
<p class="brandsub">2026-11-20 03:59~04:15 KST · 운영 crewrun.duckdns.org · CRU-84</p>
<div class="grp">요약</div>
<a data-page="overview" class="active">결론 한눈에</a>
<a data-page="design">테스트 설계 <span class="tag">무엇을 어떻게</span></a>
<div class="grp">결과</div>
<a data-page="x">X — 초당 예매 처리</a>
<a data-page="api">API별 응답 시간</a>
<a data-page="server">서비스·자원 — 병목 순서</a>
<a data-page="improve">개선 제안 <span class="tag">서비스별</span></a>
<div class="grp">읽는 법 · 근거</div>
<a data-page="opinion">해석과 견해</a>
<a data-page="grafana">Grafana 캡처</a>
<a data-page="appendix">부록 <span class="tag">1차 실행·작업 기록</span></a>
</nav>
<main>

<!-- ===== 결론 ===== -->
<section class="page show" id="page-overview">
<h1>결론 한눈에</h1>
<p class="sub">화면을 누를 때와 같은 요청 순서로, 인기 대회 예매 오픈 순간을 대회 1쌍 → 2쌍 → 3쌍으로 겹쳐 운영 서버에 보냈다</p>
<div class="kpis">
<div class="kpi"><div class="k">X — 결제까지 끝난 예매 (30초 창 최대)</div><div class="v">${n2(X30)}건/초</div><div class="s">1쌍 ${n2(xPeak('30s', '300x1'))} → 2쌍 ${n2(xPeak('30s', '300x2'))} → 3쌍 ${n2(X30)}</div></div>
<div class="kpi"><div class="k">결제까지 끝난 예매 (3구간 합)</div><div class="v">${n0(cnt('booking_completed'))}건</div><div class="s">결제 503 ${n0(cnt('pay_busy_first'))} · 결제 못 함 ${n0(cnt('booking_failed_final'))}</div></div>
<div class="kpi"><div class="k">정합성 어긋남</div><div class="v" style="color:var(--ok)">0건</div><div class="s">주문 결제 = 예치금 결제 54,050,000원 · 팔린 자리 = 결제 참가권 1,254</div></div>
<div class="kpi"><div class="k">메인 EC2 CPU (2코어)</div><div class="v" style="color:var(--crit)">${n0(inst(s3.nodeCpuMax, MAIN) * 100)}%</div><div class="s">1쌍부터 최대 ${n0(inst(s1.nodeCpuMax, MAIN) * 100)}% · 보조 EC2는 최대 ${n0(inst(s3.nodeCpuMax, DATA) * 100)}%</div></div>
</div>
<div class="card verdict">
<p class="big">X는 약 <b>초당 10건</b>에서 멈춘다. 막는 곳은 <b>메인 EC2의 CPU 2코어</b>다</p>
<p>대회를 1쌍에서 2쌍으로 늘리면 초당 처리가 ${n2(xPeak('30s', '300x1'))} → ${n2(xPeak('30s', '300x2'))}건으로 늘지만, 3쌍에서는 ${n2(X30)}건으로 거의 늘지 않는다(몰린 사람 3배 → 처리 ${n1(X30 / xPeak('30s', '300x1'))}배).
같은 시간 메인 EC2 CPU는 2구간부터 99~100%였고, 1분 부하는 코어당 ${n1(inst(s3.load1, MAIN) / 2)}까지 올라갔다. 반면 서비스 안에서 API 하나를 처리하는 시간은 평균 수십 ms로 짧았다.
→ 사용자가 기다린 시간의 대부분은 <b>CPU 차례를 기다린 시간</b>이고, 서비스 코드나 DB가 느려서가 아니다.</p>
</div>
<div class="card">
<h2>무엇이 먼저 무너졌나 (먼저 닿은 순서)</h2>
<ol>
<li><b>메인 EC2 CPU</b> — 1쌍(300명)에서 이미 최대 ${n0(inst(s1.nodeCpuMax, MAIN) * 100)}%. 2쌍부터 100%에 붙는다. 가장 많이 쓰는 곳은 <b>화면을 그리는 web(Next.js)</b> 최대 ${n2(s3.podCpuMax.find((r) => r.m.pod.startsWith('bukang-web')).v)}코어.</li>
<li><b>web Pod 강제 재시작</b> — 04:09:04, CPU를 기다리느라 생존 확인(TCP, 1초 제한)에 3번 답하지 못해 쿠버네티스가 끄고 다시 띄웠다(종료 코드 143). → 3구간 화면 요청 ${n1((sumFail(s3, true) / sumReq(s3, true)) * 100)}%가 502·504.</li>
<li><b>상품 자리 확보 교착(데드락)</b> — 부하와 상관없이 나는 데이터 문제. 운영 표에 <code>participation_id</code> 유니크 인덱스가 없어 결제 때 자리 행 8,746개 전체를 잠근다 → 11번 500 → 주문 503.</li>
<li><b>(1번의 증상) 대기열 등록의 DB 커넥션</b> — 대기열 줄은 Redis지만, 줄 등록은 대회·종목 복제본을 MySQL에서 <code>findById</code> 2번으로 확인한다. 1차 실행 2구간 오픈 순간(03:30:00~15) 한 번, 등록이 초당 2.7 → 15.8건으로 뛰고 CPU가 99%라 커넥션을 쥔 스레드가 CPU를 기다리며 풀 10개가 차고 ${n0(qPend1)}건이 기다렸다. 2차에서는 없음 → 풀 크기 문제가 아니라 CPU 포화의 증상.</li>
</ol>
<p class="mut">반대로 <b>보조 EC2(MySQL·Redis·Kafka)</b>는 최대 ${n0(inst(s3.nodeCpuMax, DATA) * 100)}%, MySQL 실행 중 스레드 최대 ${n0(Math.max(...S.map((s) => s.mysqlThreadsRunning[0]?.v || 0)))}개, 2차 실행 커넥션 풀 대기 0으로 여유가 있었다. 결제 예치금 동시 처리 제한(503)도 한 번도 걸리지 않았다.</p>
</div>
<div class="card">
<h2>개선 방향 (효과 순)</h2>
<ul>
<li><b>web(Next.js)을 메인 EC2 밖으로 빼거나 화면 렌더링 줄이기</b> — CPU 1위. 대회 목록·상세처럼 사람마다 같지 않은 부분이 적은 화면은 캐시(재검증 주기)로 그리지 않고 돌려준다.</li>
<li><b>web 생존 확인 기준 늘리기</b> — 1초 제한 ×3이면 CPU가 찰 때 멀쩡한 web을 죽인다. 제한 3~5초, 실패 횟수 늘리기.</li>
<li><b>상품: <code>participation_hold.participation_id</code> 유니크 인덱스 넣기</b> (내 서비스) — 교착 제거, 결제 때 잠금 범위를 행 1개로.</li>
<li><b>메인 EC2 코어 늘리기 또는 일을 보조 EC2로 나누기</b> — 지금 구조에서 X를 올리는 가장 직접적인 방법.</li>
</ul>
<p class="mut">늘리지 않아도 되는 것: DB 커넥션 풀(대기 0), 힙(최대 40%), MySQL(스레드 최대 7). 병목이 아닌 곳을 늘리면 전체 처리량은 오르지 않는다.</p>
</div>
</section>

<!-- ===== 설계 ===== -->
<section class="page" id="page-design">
<h1>테스트 설계</h1>
<p class="sub">어떤 사람을, 어떤 순서로, 얼마나 몰았는가 — 「부하 시나리오 작성 팁」(2026-11-19) 결정대로</p>
<div class="card">
<h2>무엇을 재는가</h2>
<div class="defn"><b>X</b> 초당 처리하는 예매 건수 = 결제까지 끝난 예매 ÷ 시간. 대기열 설정(3초마다 종목별 20명, 파티 5개)은 그대로 두고, <b>같은 시각에 여는 대회 쌍을 1 → 2 → 3으로 늘려</b> 처리량이 더 늘지 않는 지점을 찾는다.</div>
<table><thead><tr><th>항목</th><th class="l">값</th></tr></thead><tbody>
<tr><td>대상</td><td class="l">운영 crewrun.duckdns.org — 메인 EC2 t3.large(2 vCPU · 7.6GB, k3s, 앱 Pod 15개) + 보조 EC2(MySQL·Redis·Kafka·ES·게이트웨이2)</td></tr>
<tr><td>부하 PC</td><td class="l">개인 PC 1대, Docker k6 1.2.3, 가상 사용자 2,310 (계정 하나 = 사람 하나). 실행 중 PC CPU 평균 19%·최대 47% → 부하 PC는 병목 아님</td></tr>
<tr><td>구간</td><td class="l">구간 1: 대회 1쌍(300명) 03:59 오픈 → 구간 2: 2쌍(600명) 04:03 → 구간 3: 3쌍(900명) 04:07. 각 구간 + 보기만 10%</td></tr>
<tr><td>한 쌍</td><td class="l">크루 대회 A(크루 예매만) + 개인 대회 B(개인 예매만), 같은 시각 오픈, 종목 4개(10K 40% · 하프 30% · 5K 20% · 풀 10%), 정원은 몰린 인원의 70%</td></tr>
<tr><td>통과 기준 (8절)</td><td class="l">p95 조회 300ms · 대기열 200ms · 쓰기 1초, 실패율 1% 미만, 정합성 어긋남 항상 0. 매진(409)은 실패 아님. 화면 요청은 기준에서 뺌</td></tr>
<tr><td>준비</td><td class="l">계정 2,311(판매자 1), 크루 300개(가입일 30일 전으로), 예치금 1,000,000원, 로그인 제한 스위치 잠시 끔 → 끝나고 다시 켬</td></tr>
</tbody></table>
</div>
<div class="card">
<h2>한 사람이 하는 일 (시트 「부하 시나리오」 순서 그대로)</h2>
<h3>개인 예매 — 80% (대회 B)</h3>
<div class="flow"><span class="step">앱 열기 (재발급·내 정보·알림·내 주문)</span><span class="sep">→</span><span class="step">대회 목록</span><span class="sep">→</span><span class="step">상세</span><span class="sep">→</span><span class="step">신청하기 (대기열 등록)</span><span class="sep">→</span><span class="step">순서 기다림 (3초마다)</span><span class="sep">→</span><span class="step">신청서 30초</span><span class="sep">→</span><span class="step">주문 (자리 확보)</span><span class="sep">→</span><span class="step">결제 5초</span><span class="sep">→</span><span class="step">완료 화면</span></div>
<h3>크루 파티장 — 20% (대회 A, 파티 5명)</h3>
<div class="flow"><span class="step">앱 열기</span><span class="sep">→</span><span class="step">목록 · 상세</span><span class="sep">→</span><span class="step">크루 탭 · 크루 홈</span><span class="sep">→</span><span class="step">파티</span><span class="sep">→</span><span class="step">5명 파티 신청 (파티 대기열)</span><span class="sep">→</span><span class="step">파티 신청 (크루 자리 5석)</span></div>
<h3>파티원 결제 — 배경 (정원 안 파티원의 25%, 오픈 1~5분 뒤)</h3>
<div class="flow"><span class="step">앱 열기</span><span class="sep">→</span><span class="step">알림 · 결제 알림</span><span class="sep">→</span><span class="step">파티</span><span class="sep">→</span><span class="step">내 몫 주문</span><span class="sep">→</span><span class="step">결제</span></div>
<h3>보기만 — 10%</h3>
<div class="flow"><span class="step">앱 열기</span><span class="sep">→</span><span class="step">목록</span><span class="sep">→</span><span class="step">상세</span><span class="sep">→</span><span class="step">나감</span></div>
<ul>
<li>도착: 90%가 오픈 1분 전부터 들어와 기다리다 오픈 뒤 5초 안에 누르고, 10%는 오픈 뒤 1분 동안 들어온다. 10%는 쿠키가 없어 로그인 화면에서 로그인한다.</li>
<li>화면 이동(<code>?_rsc=</code>)도 보낸다. 운영에서는 Next 서버가 이 요청을 그리면서 백엔드를 다시 부르므로, <b>화면 요청 하나가 web CPU + API 여러 개</b>가 된다.</li>
<li>결제 503이면 실패 화면 3초 → 「다시 결제하기」 → 2초 → 다시 누름(최대 5번). 이번에는 한 번도 일어나지 않았다.</li>
</ul>
</div>
</section>

<!-- ===== X ===== -->
<section class="page" id="page-x">
<h1>X — 초당 예매 처리</h1>
<p class="sub">결제까지 끝난 예매(<code>booking_completed</code>)가 초당 몇 건씩 쌓였는가</p>
<div class="card">
<h2>쌍을 늘릴수록 처리가 늘었나</h2>
<div class="tscroll"><table><thead><tr><th>구간</th><th>10초 창 최대</th><th>30초 창 최대 (X)</th><th>1분 창 최대</th><th>1쌍 × 쌍 수 (늘었어야 할 값)</th><th>달성</th></tr></thead><tbody>${xRows}</tbody></table></div>
<p class="note">X는 30초 창으로 읽는다. 10초 창은 같은 순간 완료가 겹친 순간값이라 출렁이고, 1분 창은 몰림 앞뒤의 빈 시간이 섞여 낮게 나온다. k6가 5초마다 Prometheus로 보낸 누적 수에 rate를 적용한 값.</p>
<ul>
<li><b>1쌍 → 2쌍</b>: 처리 ${n1(xPeak('30s', '300x2') / xPeak('30s', '300x1'))}배. 사람이 2배인데 1.5배 → 이미 CPU가 막기 시작했다(2구간 CPU 99%).</li>
<li><b>2쌍 → 3쌍</b>: 처리 ${n2(X30 / xPeak('30s', '300x2'))}배. 사람이 1.5배 늘어도 처리는 거의 그대로 → <b>X ≈ 초당 10건이 이 구성의 한계</b>.</li>
<li>대기열은 한계가 아니다: 개인 대회 하나가 3초마다 종목 4개 × 20명 = 초당 약 26.7명을 들여보내므로 3쌍이면 초당 80명까지 들어온다. 들어온 뒤 신청서·주문·결제를 처리하는 쪽이 초당 10건에서 막혔다.</li>
</ul>
</div>
<div class="card">
<h2>사람 단위 결과</h2>
<div class="tscroll"><table><thead><tr><th>구간</th><th>개인 예매</th><th>결제 완료 (개인)</th><th>매진</th><th>파티 신청 성공</th><th>크루 자리 부족</th><th>파티원 결제</th><th>결제 503</th><th>결제 못 함</th><th>흐름 끊김</th></tr></thead><tbody>${peopleRows}</tbody></table></div>
<p class="note">정원은 몰린 인원의 70%라 30%는 매진·자리 부족이 정상이다. 세 구간 모두 <b>개인 정원과 크루 정원이 끝까지 다 팔렸다</b> — 느려졌지만 예매 자체는 끝났다. 흐름 끊김 ${n0(cnt('flow_broken'))}건은 모두 주문 단계의 503(상품 자리 확보 교착, 「개선 제안」 참고).</p>
</div>
<div class="card">
<h2>시간에 따라 — 몰림, CPU, 처리</h2>
${lineChart({ series: [{ label: '메인 EC2 CPU %', color: '#d85a30', points: nodeMain }, { label: '보조 EC2 CPU %', color: '#9aa2a9', points: nodeData }, { label: 'k6 초당 요청 (÷2)', color: '#1d7a8c', points: rps.map(([t, v]) => [t, v / 2]), dash: true }], yMax: 110, yFmt: (v) => Math.round(v), marks: opens, title: '메인 EC2는 오픈마다 100%에 붙고, 보조 EC2는 50%를 넘지 않는다' })}
${lineChart({ series: [{ label: '결제 완료 예매 / 초 (30초 창)', color: '#3b6d11', points: comp }], yFmt: (v) => v.toFixed(1), marks: opens, title: '쌍이 늘어도 처리 꼭대기는 초당 10건 근처에서 멈춘다' })}
</div>
<div class="card">
<h2>N·M과의 관계</h2>
<ul>
<li><b>N (쾌적 최대 인원)</b>: 1쌍 300명에서도 p95 조회 ${ms(kindP(s1, 'kindP95', 'read'))}ms · 대기열 ${ms(kindP(s1, 'kindP95', 'queue'))}ms · 쓰기 ${ms(kindP(s1, 'kindP95', 'write'))}ms로 기준을 넘었다 → <b>N &lt; 300</b> (화면 요청까지 함께 보낼 때). N은 100 → 300 구간으로 다시 재야 한다.</li>
<li><b>M (다시 시도해서라도 모두 결제)</b>: 900명이 몰린 3쌍까지 결제 못 한 사람 0, 결제 503 0 → API 기준으로는 M ≥ 900. 다만 3구간은 화면 ${n1((sumFail(s3, true) / sumReq(s3, true)) * 100)}%가 502·504라 실제 브라우저에서는 오류 화면을 본 사람이 있다.</li>
</ul>
</div>
</section>

<!-- ===== API ===== -->
<section class="page" id="page-api">
<h1>API별 응답 시간</h1>
<p class="sub">사용자 쪽(k6)에서 잰 시간 — 요청을 보낸 순간부터 응답을 다 받을 때까지. 네트워크 + 입구(Traefik·nginx) + 게이트웨이 + 서비스 + CPU 차례 기다림이 모두 들어 있다</p>
<div class="card">
<h2>구간별 증상 (황금 신호: 트래픽 · 지연 · 오류)</h2>
<div class="tscroll"><table><thead><tr><th>구간</th><th>k6 초당 요청 최대</th><th>조회 p95</th><th>대기열 p95</th><th>쓰기 p95</th><th>로그인 p95</th><th>화면 p95</th><th>API 실패율</th><th>화면 실패율</th></tr></thead><tbody>${symRows}</tbody></table></div>
<p class="note">p95는 구간 안에서 k6가 5초마다 계산한 p95의 최대값(ms). API는 3구간에서도 실패가 거의 없다(주문 503 2건). 실패는 모두 화면 요청이고, web Pod가 재시작된 04:09 전후에 몰린다. 3구간 화면 p95 6만ms는 k6 기본 제한 시간(60초)에 걸린 값이다.</p>
</div>
<div class="card">
<h2>사용자 쪽 API별 p95 (요청 이름별)</h2>
<div class="tscroll"><table><thead><tr><th>종류</th><th class="l">요청</th><th class="l">서비스</th><th>요청 수 1</th><th>2</th><th>3</th><th>p95 1쌍</th><th>2쌍</th><th>3쌍</th><th>판정</th></tr></thead><tbody>${apiRows}</tbody></table></div>
<p class="note">단위 ms. 판정은 1쌍(300명) 기준(통과 · 초과 · 기준의 3배 넘음). 회색 줄은 화면(Next) 요청이라 기준이 없고, 「오류」는 502·504 건수. 같은 요청이 여러 화면에서 나가면 합산.
결제·신청처럼 1쌍 p95가 2쌍보다 높은 요청이 있다. 1구간이 준비(계정 2,310 로그인·대회·파티 만들기) 직후 첫 몰림이라 서비스가 덜 데워진 상태(JIT·커넥션·캐시)였던 영향으로 보인다 — 가설이며 같은 조건으로 다시 돌려 확인할 것.</p>
</div>
<div class="card">
<h2>서버 안에서 걸린 시간 (Spring 지표, 서비스별 API)</h2>
<p class="chcap">사용자 쪽 p95가 수 초인데 서버 안은 수십 ms다 → 차이는 CPU 차례 기다림과 입구 줄 서기. 「일한 시간」 = 평균 × 요청 수(3구간), 이 API가 서비스 스레드를 붙잡은 총 시간이라 개선 우선순위로 쓴다</p>
<div class="tscroll"><table><thead><tr><th class="l">서비스</th><th class="l">API</th><th>요청 수 (3쌍)</th><th>평균 1쌍</th><th>2쌍</th><th>3쌍</th><th>최대 (3쌍)</th><th>일한 시간 (3쌍)</th></tr></thead><tbody>${uriRows}</tbody></table></div>
<p class="note">평균·최대는 ms. <code>http_server_requests_seconds</code>의 sum/count(평균)와 max. 운영에는 백분위 구간(histogram)이 없어 서버 쪽 p95는 없다. 게이트웨이 줄은 경로가 UNKNOWN으로 묶여 빼고 「서비스·자원」에 합계로 둔다.</p>
</div>
</section>

<!-- ===== 서버 ===== -->
<section class="page" id="page-server">
<h1>서비스·자원 — 병목 순서</h1>
<p class="sub">증상(지연·오류)과 같은 시간에 원인 지표(CPU·메모리·풀·DB)를 겹쳐 본다. 대기가 쌓이는 곳 앞은 붐비고 뒤는 한가하다</p>
<div class="card verdict">
<p class="big">대기가 쌓인 곳은 <b>메인 EC2 CPU</b> — 그 뒤(서비스·DB)는 한가했다</p>
<p>CPU 사용률 100% + CPU 기다림(PSI) 약 90% + 1분 부하 코어당 최대 ${n1(inst(s3.load1, MAIN) / 2)} ↔ 서비스 처리 평균 수십 ms, 커넥션 풀 대기 0, MySQL 실행 중 스레드 최대 ${n0(Math.max(...S.map((s) => s.mysqlThreadsRunning[0]?.v || 0)))}.
「응답이 느림 + CPU 높음」은 트래픽 초과 쪽이고, 「응답이 느림 + CPU 한가」(잠금·I/O·풀 대기)가 아니다.</p>
</div>
<div class="card">
<h2>구간별 원인 지표</h2>
<div class="tscroll"><table><thead><tr><th>구간</th><th>메인 CPU 최대</th><th>메인 1분 부하</th><th>메인 메모리 여유 최저</th><th>보조 CPU 최대</th><th>web CPU 최대 (코어)</th><th>MySQL 실행 중 스레드</th><th>MySQL 초당 쿼리</th><th>커넥션 풀 대기 최대</th></tr></thead><tbody>${causeRows}</tbody></table></div>
<p class="note">메인 CPU는 모드별로 user 1.55 + system 0.27 + softirq 0.17코어, steal 0(1차 실행 같은 시각) → t3 크레딧 부족이 아니라 일이 많아서 찼다. 메모리 여유 최저 ${n0(inst(s3.nodeMemAvailMin, MAIN))}MB로 아직 버텼지만 여유가 적다.</p>
</div>
<div class="card">
<h2>누가 CPU를 썼나 (메인 EC2 Pod별, 코어)</h2>
<div class="tscroll"><table><thead><tr><th>Pod</th><th>1쌍 최대 <span class="ssub">평균</span></th><th>2쌍</th><th>3쌍</th><th class="l">3쌍 최대</th></tr></thead><tbody>${podRows}</tbody></table></div>
${lineChart({ series: podSeries, yFmt: (v) => v.toFixed(2), marks: opens, title: 'web(Next.js)이 오픈마다 가장 먼저, 가장 높이 오른다' })}
<p class="note">Pod 합은 약 1.6코어, 나머지는 커널(네트워크·TLS)과 k3s. 2코어를 서비스 15개 + Traefik + nginx + web이 나눠 쓴다.</p>
</div>
<div class="card">
<h2>서비스별 합계 (Spring 지표)</h2>
<div class="tscroll"><table><thead><tr><th class="l">서비스</th><th>요청 1쌍</th><th>2쌍</th><th>3쌍</th><th>평균 ms 1쌍</th><th>2쌍</th><th>3쌍</th><th>최대 ms (3쌍)</th><th>풀 사용 최대/크기</th><th>풀 대기 최대</th><th>힙 최대</th><th>GC 멈춤 합 (3쌍, 초)</th></tr></thead><tbody>${svcRows}</tbody></table></div>
<p class="note">gateway 요청 수가 가장 많은 것은 모든 API가 지나기 때문. 게이트웨이는 메인(k3s)과 보조 EC2에 하나씩 있고 nginx가 번갈아 보낸다. GC 멈춤은 SerialGC + CPU 경합으로 늘었지만 합계 수 초로 원인 수준은 아니다.</p>
</div>
<div class="card">
<h2>병목에 닿은 순서와 근거</h2>
<div class="tscroll"><table><thead><tr><th>순서</th><th class="l">어디</th><th class="l">언제</th><th class="l">근거</th><th class="l">그래서</th></tr></thead><tbody>
<tr><td>1</td><td class="l">메인 EC2 CPU (2코어)</td><td class="l">1쌍 최대 ${n0(inst(s1.nodeCpuMax, MAIN) * 100)}%, 2쌍부터 100%</td><td class="wrap">CPU 기다림(PSI) ~90%, 1분 부하 코어당 ${n1(inst(s3.load1, MAIN) / 2)}, web 최대 ${n2(s3.podCpuMax.find((r) => r.m.pod.startsWith('bukang-web')).v)}코어</td><td class="wrap">모든 요청이 CPU 차례를 기다린다 → API p95가 서버 처리 시간의 수십 배</td></tr>
<tr><td>2</td><td class="l">web(Next.js) Pod</td><td class="l">04:09:04 (3쌍 오픈 2분 뒤)</td><td class="wrap">생존 확인 TCP 1초 × 3번 실패 → 종료 코드 143으로 재시작 1번, 메모리 한도 256Mi</td><td class="wrap">화면 요청 502·504 ${n0(sumFail(s3, true))}건 → 사용자는 오류 화면</td></tr>
<tr><td>3</td><td class="l">상품 자리 확보 (product)</td><td class="l">오픈 직후 결제가 몰릴 때</td><td class="wrap">MySQL 교착 11건: 결제(판매)가 자리 행 전체를 잠근 채 종목 행을 기다리고, 확보는 종목 행을 잡은 채 자리 행 삽입을 기다림</td><td class="wrap">주문 503 → 그 사람은 예매 실패(흐름 끊김 ${n0(cnt('flow_broken'))})</td></tr>
<tr><td>(1의 증상)</td><td class="l">queue-service 등록의 DB 커넥션</td><td class="l">1차 실행 2구간 오픈 03:30:00~15</td><td class="wrap">줄은 Redis, 등록만 복제본 <code>findById</code> 2번(Facade에 트랜잭션 없음, 호출마다 짧게 잡고 놓음). 등록 초당 15.8건 + CPU 99% → 풀 10/10, 대기 ${n0(qPend1)}, 연결 받는 데 평균 ${n1(qAcq1)}ms, 파티 등록 최대 ${n0(qPartyMax1?.v)}ms</td><td class="wrap">한 번뿐, 2차에는 없음 → 쿼리가 느린 게 아니라 CPU를 기다리는 동안 커넥션을 쥐고 있었다</td></tr>
</tbody></table></div>
</div>
</section>

<!-- ===== 개선 ===== -->
<section class="page" id="page-improve">
<h1>개선 제안 — 어느 서비스의 무엇을</h1>
<p class="sub">지금 방식 → 개선 방향 → 기대 효과 → 주의할 점. 효과가 큰 순서</p>
<div class="card">
<div class="imp"><div class="t"><span class="pill crit">1순위</span> web(Next.js) — 메인 CPU 1위, 화면 실패 전부</div>
<div class="row"><b>지금</b> 화면 이동마다 Next 서버가 화면을 새로 그리고(SSR·RSC), 그때 백엔드 API를 다시 부른다. 3쌍에서 화면 요청 ${n0(sumReq(s3, true))}건, web 한 Pod가 CPU 최대 ${n2(s3.podCpuMax.find((r) => r.m.pod.startsWith('bukang-web')).v)}코어.</div>
<div class="row"><b>개선</b> ① 대회 목록·상세처럼 사람마다 같은 화면은 캐시(재검증 주기)로 돌려줌 ② web을 보조 EC2나 다른 노드로 옮기거나 복제본을 늘림 ③ 생존 확인 제한 1초 → 3~5초, 실패 횟수 3 → 6.</div>
<div class="row"><b>기대</b> 메인 CPU의 약 1/3을 비우면 같은 2코어에서 API 쪽 처리가 늘어 X가 오른다. ③만으로도 「CPU가 찼다고 web이 죽는」 연쇄를 끊는다.</div>
<div class="row"><b>주의</b> 프론트 코드는 지훈님 담당이라 제안으로 넘긴다. 캐시는 남은 자리 같은 실시간 값이 들어간 부분을 빼고.</div></div>

<div class="imp"><div class="t"><span class="pill crit">1순위</span> product(내 서비스) — 자리 확보 교착, 운영 표 인덱스 빠짐</div>
<div class="row"><b>지금</b> 운영 <code>crewrun_product.participation_hold</code>에 <code>uk_participation_hold_participation(participation_id)</code>가 없다. 표는 Flyway 전부터 있었고 V1이 <code>create table if not exists</code>라 건너뛰었다. 옛 칸 <code>application_id</code>(유니크)·<code>crew_application_id</code>만 남았다.
결제 판매 <code>findForUpdate(participationId)</code>(<code>SELECT … FOR UPDATE</code>)가 전체 훑기(type=ALL)로 <b>8,746행 모두에 배타 잠금</b>을 건다. 그동안 자리 확보는 종목 행을 잡고 자리 행을 넣으려다 서로 기다려 교착 → 500 11건 → 주문 503.</div>
<div class="row"><b>개선</b> Flyway SQL 하나: ① <code>participation_id = 0</code>인 옛 행 35개를 <code>application_id</code> 값으로 채움 ② <code>participation_id</code> 유니크 인덱스 추가 ③ 옛 칸 2개 지움. 로컬은 이미 인덱스가 있어 이 문제가 안 보였다.</div>
<div class="row"><b>기대</b> 판매 잠금이 행 1개로 → 교착 0. 같은 신청으로 두 번 확보되는 것도 DB가 막는다(지금 운영은 코드 확인만 있음). 자리 행이 늘수록 느려지는 문제도 사라진다.</div>
<div class="row"><b>주의</b> 운영 DB 변경이라 배포 전 확인. 인덱스 추가 중 잠금은 8천여 행이라 짧다. 종목 행 → 자리 행 순서(확보)와 자리 행 → 종목 행 순서(판매)가 반대인 것도 같이 맞추면 더 안전하다.</div></div>

<div class="imp"><div class="t"><span class="pill warn">2순위</span> 메인 EC2 — 코어 수 또는 일 나누기</div>
<div class="row"><b>지금</b> 앱 Pod 15개 + Traefik + nginx + web이 2코어를 나눠 쓴다. 보조 EC2는 50% 이하로 남는다.</div>
<div class="row"><b>개선</b> 메인을 4코어로 올리거나, 보조 EC2의 게이트웨이2처럼 CPU를 많이 쓰는 Pod(web, product 등)를 보조 쪽으로 나눈다.</div>
<div class="row"><b>기대</b> CPU가 벽이라 가장 직접적. 다만 web 캐시·인덱스를 먼저 하면 같은 비용으로 더 멀리 간다.</div></div>

<div class="imp"><div class="t"><span class="pill warn">2순위</span> order — 쓰기 API 처리 시간이 가장 길다</div>
<div class="row"><b>지금</b> 결제 평균 ${n0(uris.find((u) => u.uri === '/api/v1/order/orders/{orderId}/payment')?.avg[2])}ms, 파티 신청 ${n0(uris.find((u) => u.uri === '/api/v1/order/parties/{partyId}/apply')?.avg[2])}ms, 주문 ${n0(uris.find((u) => u.uri === '/api/v1/order/orders' && u.method === 'POST')?.avg[2])}ms(3쌍). 주문 → 상품(자리) → 예치금을 차례로 동기 호출한다.</div>
<div class="row"><b>개선</b> 결제 뒤 「다시 조회」 화면 요청(주문·잔액·참가자)이 결제 1번에 3~6번 따라온다 → 결제 응답에 필요한 값을 담아 다시 묻지 않게.</div>
<div class="row"><b>주의</b> 다윗님 담당. 결제 503 동시 제한(4)은 이번에 한 번도 걸리지 않아 지금은 늘릴 필요가 없다.</div></div>

<div class="imp"><div class="t"><span class="pill warn">3순위</span> file — 가장 많이 불리는 백엔드 API</div>
<div class="row"><b>지금</b> <code>GET /file/files</code>가 3쌍에서 ${n0(uris.find((u) => u.job === 'file-service')?.n[2])}번. 대기열·신청 화면이 종목마다 이미지를 따로 묻는다(대회 1 + 종목 4).</div>
<div class="row"><b>개선</b> 대회 하나의 이미지를 한 번에 주는 API, 또는 대회 상세 응답에 이미지 주소를 담기. 건당 7ms라 빠르지만 횟수가 많아 게이트웨이·nginx·Traefik CPU를 함께 쓴다.</div></div>

<div class="imp"><div class="t"><span class="pill ok">관찰</span> queue — 등록 때 복제본 조회</div>
<div class="row"><b>지금</b> 순번·입장 확인·승격은 Redis만 쓴다. 줄 등록(개인·파티)만 대회·종목 복제본을 MySQL에서 <code>findById</code> 2번 읽는다(Facade 트랜잭션 없음, Spring Data가 호출마다 짧은 읽기 트랜잭션). 1차 실행 2구간 오픈 15초 동안 CPU 포화로 풀 10개가 찼고, 2차에서는 없었다.</div>
<div class="row"><b>개선</b> 풀은 늘리지 않는다. 다시 나오면 복제본(판매 기간·삭제 여부, 거의 안 바뀜)을 메모리나 Redis에 두어 등록 경로에서 DB를 빼는 쪽을 본다. 그러면 오픈 순간 몰림이 DB 커넥션과 무관해진다.</div></div>
</div>
<div class="card">
<h2>다른 담당에게 넘길 것</h2>
<table><thead><tr><th class="l">받는 사람</th><th class="l">내용</th></tr></thead><tbody>
<tr><td class="l">옥지훈 (팀장·프론트)</td><td class="l wrap">web Pod CPU 1위·생존 확인 1초로 재시작·메모리 한도 256Mi. 화면 캐시 검토. 이미지 묻는 횟수.</td></tr>
<tr><td class="l">이다윗 (주문)</td><td class="l wrap">결제 뒤 다시 조회 요청 수. 상품 503(교착)이 주문 503으로 보인 건은 상품 쪽에서 고침.</td></tr>
<tr><td class="l">인프라 (팀)</td><td class="l wrap">메인 2코어 포화, 보조 EC2 여유. 노드 증설 또는 Pod 배치 나누기.</td></tr>
</tbody></table>
</div>
</section>

<!-- ===== 견해 ===== -->
<section class="page" id="page-opinion">
<h1>해석과 견해</h1>
<p class="sub">숫자를 어떻게 읽었고, 무엇을 믿고 무엇을 조심해야 하는지</p>
<div class="card">
<h2>1. 「서버가 느리다」가 아니라 「서버가 줄 서 있다」</h2>
<p>사용자 쪽 p95는 수 초인데 서비스 안 처리는 수십 ms다. 이 차이가 이번 결과의 핵심이다.
Little의 법칙(동시에 처리 중인 요청 = 초당 들어오는 요청 × 한 건 걸리는 시간)으로 보면, 3구간 초당 약 ${n0(s3.rps[0]?.v)}건 × 중간값 약 ${n1(kindP(s3, 'kindP50', 'read'))}초 ≈ <b>${n0(s3.rps[0]?.v * kindP(s3, 'kindP50', 'read'))}건이 동시에 서버 안에 머물렀다</b>. 서비스가 한 건을 수십 ms에 끝내도 CPU 2개를 차례로 기다리면 그만큼 밀린다.
그래서 서비스 코드를 몇 ms 줄이는 것보다 <b>CPU를 덜 쓰게 하거나(화면 렌더링·호출 수) CPU를 늘리는 것</b>이 X를 올린다.</p>
</div>
<div class="card">
<h2>2. 사용률 80%를 넘으면 기다림이 급하게 는다</h2>
<p>1쌍에서 메인 CPU 최대 ${n0(inst(s1.nodeCpuMax, MAIN) * 100)}%일 때 조회 p95 ${ms(kindP(s1, 'kindP95', 'read'))}ms였고, 100%에 붙은 3쌍에서 ${ms(kindP(s3, 'kindP95', 'read'))}ms로 ${n1(kindP(s3, 'kindP95', 'read') / kindP(s1, 'kindP95', 'read'))}배가 됐다. 사람은 3배인데 기다림은 그 이상으로 는다.
대기 이론에서 사용률 50% 대비 기다림이 80%에서 약 4배, 90%에서 약 9배로 꺾이는 모양과 같다. 운영 목표선을 CPU 80%로 두고, X는 「CPU 80% 안에서 나오는 처리량」으로 다시 정하는 것을 권한다.</p>
</div>
<div class="card">
<h2>3. 이 결과에서 믿어도 되는 것</h2>
<ul>
<li><b>정합성</b>: 2,000건 넘게 몰려도 주문 결제 금액 = 예치금 결제 금액, 팔린 자리 = 결제 참가권, 정원 초과 0. 자리·돈 쪽 설계(조건부 UPDATE, 같은 순서 잠금, 결제 동시 제한)는 운영에서도 맞게 동작했다.</li>
<li><b>병목 위치</b>: 메인 CPU라는 결론은 CPU 사용률·PSI·부하·Pod별 CPU·서비스 처리 시간·풀 대기가 모두 같은 방향을 가리킨다. 그래프 하나가 아니라 여러 원인 지표가 겹친다.</li>
<li><b>부하 PC는 원인이 아님</b>: PC CPU 평균 19%. 받는 데 걸린 시간보다 첫 바이트를 기다린 시간(waiting)이 대부분이다.</li>
</ul>
</div>
<div class="card">
<h2>4. 조심해서 읽을 것 (측정 한계)</h2>
<ul>
<li><b>화면 요청은 실제 브라우저와 다르다</b>: k6는 <code>?_rsc=</code> 요청만 보내고 JS·이미지·CSS는 받지 않는다. 실제 사용자는 web·S3에 더 많이 보내므로 web 부담은 이보다 크다.</li>
<li><b>IP 하나에서 보냈다</b>: 로그인 제한을 잠시 껐다. 실제로는 IP가 모두 다르다. TLS 연결도 실제보다 적게 맺는다(가상 사용자가 연결을 다시 쓴다).</li>
<li><b>X는 이 시나리오의 X</b>: 신청서 30초·결제 5초를 사람처럼 기다리므로, X는 「서버가 끝낼 수 있는 최대」가 아니라 「사람이 이렇게 움직일 때 끝나는 속도」다. 그래도 쌍을 늘려도 처리가 늘지 않은 것은 서버 한계라서다.</li>
<li><b>서버 p95가 없다</b>: 운영 Spring 지표에 백분위 구간이 없어 서버 쪽은 평균·최대만 있다. 서버 p95가 필요하면 <code>percentiles-histogram</code>을 켠다.</li>
<li><b>새벽(03:59~04:15)에 한 번</b>: 다른 사람의 트래픽은 거의 없었다. 같은 조건으로 한 번 더 돌려 차이를 보는 것이 좋다.</li>
</ul>
</div>
<div class="card">
<h2>5. 다음에 할 것</h2>
<ol>
<li>product 인덱스 Flyway를 넣고(교착 제거), web 생존 확인 제한을 늘린 뒤 <b>같은 300×1~3을 다시</b> → 개선 전후 비교.</li>
<li>N을 찾으려면 100 → 200 → 300(1쌍)으로 다시. 지금 기준(조회 300ms)은 화면 요청이 함께 몰리는 운영 2코어에서 300명도 못 넘는다.</li>
<li>Spring에 서버 쪽 히스토그램을 켜서 서비스별 p95를 함께 본다.</li>
</ol>
</div>
</section>

<!-- ===== 캡처 ===== -->
<section class="page" id="page-grafana">
<h1>Grafana 캡처</h1>
<p class="sub">운영 Grafana (grafana.bukang-devops.duckdns.org) · 2026-11-20 03:56~04:16 KST · k6 테스트 <code>${d.testid}</code></p>
<figure><img src="${img('운영-X-그라파나-1-부하테스트.png')}" alt="크루런 부하 테스트 대시보드"><figcaption>① 크루런 · 부하 테스트 — k6 응답 시간은 12초까지 오르는데 서비스 p99는 700ms 아래. 서버 CPU(main)는 오픈마다 100%</figcaption></figure>
<figure><img src="${img('운영-X-그라파나-2-서버.png')}" alt="크루런 서버 대시보드"><figcaption>② 크루런 · 서버 — 메인 1분 부하 ÷ 코어 최대 9, CPU 기다림(PSI) 90%. 보조 EC2는 여유</figcaption></figure>
<figure><img src="${img('운영-X-그라파나-3-통합.png')}" alt="크루런 통합 대시보드"><figcaption>③ 크루런 통합 — 서비스별 초당 요청·p99·5xx, 커넥션 풀 대기 0, 게이트웨이 1·2</figcaption></figure>
<figure><img src="${img('운영-X-그라파나-4-MySQL.png')}" alt="크루런 MySQL 대시보드"><figcaption>④ 크루런 · MySQL — 초당 쿼리는 늘지만 실행 중 스레드·풀 대기는 낮다</figcaption></figure>
<figure><img src="${img('운영-X-그라파나-5-JVM.png')}" alt="크루런 JVM 대시보드"><figcaption>⑤ 크루런 · JVM — 힙·GC는 원인 수준이 아니다</figcaption></figure>
</section>

<!-- ===== 부록 ===== -->
<section class="page" id="page-appendix">
<h1>부록</h1>
<div class="card">
<h2>1차 실행 (03:26~03:42) — 스크립트 오류로 버림</h2>
<p>k6는 기본으로 반복(구간)마다 가상 사용자의 쿠키를 비운다. 그래서 앞 구간에 나온 계정이 다음 구간에서 Refresh Token 없이 재발급을 불러 401이 났고, 그 사람은 아무 기록 없이 끝났다(2구간 개인 480명 중 약 212명, 3구간 720명 중 약 443명).
→ <code>rush.js</code>에 <code>noCookiesReset: true</code>를 넣고, 앱 열기 실패도 흐름 끊김으로 세게 고친 뒤 2차를 돌렸다. 1차 숫자는 X에 쓰지 않았다(1구간만 유효: 30초 창 ${n2(d1.xPeak['30s'].find((r) => r.m.stage === '300x1')?.v)}건/초, 2차 1구간 ${n2(xPeak('30s', '300x1'))}과 비슷). queue 커넥션 풀 포화는 1차 2구간에서만 봤다.</p>
</div>
<div class="card">
<h2>정합성 (2차, 대회 110~121)</h2>
<table><tbody>
<tr><td>1. 결제 완료 금액 (주문 = 예치금)</td><td>54,050,000 = 54,050,000</td><td><span class="pill ok">어긋남 0</span></td></tr>
<tr><td>2. 두 번 결제된 주문</td><td>0</td><td><span class="pill ok">0</span></td></tr>
<tr><td>3. 결제 확인 중으로 남은 주문</td><td>0</td><td><span class="pill ok">0</span></td></tr>
<tr><td>4. 종목 48개: 정원 ≥ 판매 = 결제 참가권</td><td>48개 모두 일치</td><td><span class="pill ok">0</span></td></tr>
<tr><td>5. 팔린 자리 = 결제 완료 참가권</td><td>1,254 = 1,254</td><td><span class="pill ok">0</span></td></tr>
</tbody></table>
<p class="note">1차(대회 98~109)도 33,050,000원 · 765석으로 어긋남 0.</p>
</div>
<div class="card">
<h2>운영에서 한 일 (되돌림 포함)</h2>
<ul>
<li>nginx 로그인 제한 스위치: ConfigMap의 <code>map "false"</code> → <code>"true"</code>로 바꾸고 nginx만 다시 띄움(03:03·03:46) → 끝나고 <code>"false"</code>로 되돌림(03:43·04:16). 되돌린 뒤 같은 IP 30건 중 버스트 20건 뒤 429 확인, 사이트 200. git의 설정과 같다.</li>
<li>본인인증 결과 2,311건을 운영 Redis에(1시간 뒤 사라짐), 계정 realflow0~2310 가입, 크루 300개(이름 「실흐름-…」).</li>
<li>보조 MySQL: realflow 지갑 2,311개 잔액 1,000,000원, 크루 가입일 30일 전으로(1,500명), 상품 판매자 표에 realflow0 승인(id 900002 · 상품 쪽만, 회원 서비스에는 판매자 아님 → 대회일 60일 뒤, 정산 대상이 되기 전에 정리 필요).</li>
<li>대회 24개(98~121, 제목 「[실흐름] …」), 판매 1,254+765석이 운영에 남아 있다. 운영 DB는 발표 전에 비우기로 했다(2026-11-16).</li>
</ul>
</div>
<div class="card">
<h2>자료 위치</h2>
<ul>
<li>스크립트: <code>backend/loadtest/real-flow/</code> (rush.js, lib/flows.js, seed/, check.sql)</li>
<li>결과: <code>real-flow/results/prod-x2.*</code> (요약 json·로그·k6 리포트 html·Prometheus 지표 json·정합성), 1차는 <code>prod-x.*</code></li>
<li>Grafana: 크루런 · 부하 테스트 / 서버 / 통합 / MySQL / JVM, k6 테스트 <code>${d.testid}</code></li>
<li>요청 순서 원본: 구글 시트 「크루런 시나리오 URL 정리」의 「부하 시나리오」 탭, 결정: 「부하 시나리오 작성 팁」(2026-11-19)</li>
<li>읽는 틀: 「서버 모니터링 결과 읽기」(kciter.so) — 황금 신호, 증상 → 원인 순서, Little의 법칙, 사용률과 기다림</li>
</ul>
</div>
</section>
</main></div>
<script>
document.querySelectorAll('nav a[data-page]').forEach(function(a){a.addEventListener('click',function(){
document.querySelectorAll('nav a').forEach(function(x){x.classList.remove('active')});a.classList.add('active');
document.querySelectorAll('.page').forEach(function(p){p.classList.remove('show')});document.getElementById('page-'+a.dataset.page).classList.add('show');window.scrollTo(0,0);});});
</script></body></html>`;
fs.writeFileSync(OUT, html);
console.log('written', OUT, html.length);
