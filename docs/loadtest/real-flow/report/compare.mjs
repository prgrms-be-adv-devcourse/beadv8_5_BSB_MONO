// 2차(AS-IS) vs 3차(TO-BE) 비교 HTML. 사용: node compare.mjs <x2.json> <x3.json> <out.html>
import fs from 'node:fs';
const [, , A, B, OUT] = process.argv;
const a = JSON.parse(fs.readFileSync(A, 'utf8'));
const b = JSON.parse(fs.readFileSync(B, 'utf8'));
const n0 = (x) => (x === undefined || x === null || Number.isNaN(x) ? '-' : Math.round(x).toLocaleString('ko-KR'));
const n2 = (x) => (x === undefined || Number.isNaN(x) ? '-' : x.toFixed(2));
const MAIN = 'ip-10-0-1-145';
const inst = (arr) => arr.find((r) => r.m.instance === MAIN)?.v;
const kind = (st, key, k) => st[key].find((r) => r.m.kind === k)?.v;
const svcP = (st, job) => st.svcP95Ms.find((r) => r.m.job === job)?.v;
const reqs = (st, name) => st.nameReqs.filter((r) => r.m.name === name).reduce((s, r) => s + r.v, 0);
const apiReqs = (st) => st.nameReqs.filter((r) => r.m.kind !== 'page').reduce((s, r) => s + r.v, 0);
const fails = (st, page) => st.nameFail.filter((r) => (page ? r.m.kind === 'page' : r.m.kind !== 'page')).reduce((s, r) => s + r.v, 0);
const pageReqs = (st) => st.nameReqs.filter((r) => r.m.kind === 'page').reduce((s, r) => s + r.v, 0);
const cnt = (d, name, stage) => d.counters[name].filter((r) => !stage || r.m.stage === stage).reduce((s, r) => s + r.v, 0);
const brokenWhere = (d, where) => d.counters.flow_broken.filter((r) => r.m.where === where).reduce((s, r) => s + r.v, 0);
const x = (d, st) => d.xPeak['30s'].find((r) => r.m.stage === st)?.v;
const stages = ['300x1', '300x2', '300x3'];

// 변화 칸: 낮을수록 좋은 지표(lowerBetter) 기준으로 좋아짐·나빠짐 색
function delta(av, bv, lowerBetter, unit = '') {
	if (unit === '%') { const d = bv - av; return `<td><span class="pill ${(lowerBetter ? d < 0 : d > 0) ? 'ok' : 'crit'}">${d > 0 ? '+' : ''}${n0(d)}%p</span></td>`; }
	if (av === undefined || bv === undefined) return '<td>-</td>';
	const diff = bv - av;
	if (Math.abs(diff) < 1e-9) return '<td><span class="pill">같음</span></td>';
	const better = lowerBetter ? diff < 0 : diff > 0;
	const pct = av ? ` (${diff > 0 ? '+' : ''}${Math.round((diff / av) * 100)}%)` : '';
	return `<td><span class="pill ${better ? 'ok' : 'crit'}">${diff > 0 ? '+' : ''}${n0(diff)}${unit}${pct}</span></td>`;
}
const row = (label, av, bv, lowerBetter, fmt = n0, unit = '', note = '') =>
	`<tr><td class="l">${label}</td><td>${fmt(av)}${unit}</td><td>${fmt(bv)}${unit}</td>${delta(av, bv, lowerBetter, unit)}<td class="l wrap note2">${note}</td></tr>`;

// 직접 효과
const direct = [
	row('상품 자리 확보 교착 (MySQL deadlock)', 11, 0, true, n0, '건', '상품 로그 <code>Deadlock found</code>. TO-BE는 3차 실행 내내 0'),
	row('주문 503 (자리 확보 실패)', 11, 0, true, n0, '건', '교착이 상품 500 → 주문 503으로 보이던 것. 실행 전체 k6 집계'),
	row('흐름 끊김 — 주문 단계', 11, 0, true, n0, '명', '예매하다 주문에서 실패한 사람. 실행 전체 k6 집계 (TO-BE의 흐름 끊김 148명은 모두 앱 열기 = Traefik 재시작)'),
	row('결제 503 · 결제 못 함', cnt(a, 'pay_busy_first') + cnt(a, 'booking_failed_final'), cnt(b, 'pay_busy_first') + cnt(b, 'booking_failed_final'), true, n0, '', '두 실행 모두 0'),
	row('정합성 어긋남', 0, 0, true, n0, '건', '결제 금액 = 예치금, 팔린 자리 = 결제 참가권 (2차 1,254석 · 3차 1,246석)'),
].join('');

// 성능 (조건 다름)
const perf = stages.map((stg, i) => {
	const sa = a.stages[i], sb = b.stages[i];
	return `<tr class="grp"><td class="l" colspan="5"><b>구간 ${i + 1} (${i + 1}쌍, ${300 * (i + 1)}명)</b></td></tr>`
		+ row('X — 결제 완료 예매/초 (30초 창)', x(a, stg), x(b, stg), false, n2, '', '')
		+ row('메인 CPU 평균', inst(sa.nodeCpuAvg) * 100, inst(sb.nodeCpuAvg) * 100, true, n0, '%', '')
		+ row('사용자 쪽 조회 p95', kind(sa, 'kindP95', 'read') * 1000, kind(sb, 'kindP95', 'read') * 1000, true, n0, 'ms', '')
		+ row('사용자 쪽 쓰기 p95', kind(sa, 'kindP95', 'write') * 1000, kind(sb, 'kindP95', 'write') * 1000, true, n0, 'ms', '')
		+ row('서버 p95 — order', svcP(sa, 'order-service'), svcP(sb, 'order-service'), true, n0, 'ms', '서비스가 스스로 잰 시간')
		+ row('서버 p95 — product', svcP(sa, 'product-service'), svcP(sb, 'product-service'), true, n0, 'ms', '')
		+ row('API 실패', fails(sa, false), fails(sb, false), true, n0, '건', i === 1 ? 'TO-BE: Traefik 재시작 1분 연결 거부' : '')
		+ row('화면 실패 (502·504)', fails(sa, true), fails(sb, true), true, n0, '건', i === 2 ? `AS-IS ${n0(pageReqs(sa))}건 중 / TO-BE ${n0(pageReqs(sb))}건 중` : '');
}).join('');

// 조건 차이 (구간 1 요청 구성)
const s1a = a.stages[0], s1b = b.stages[0];
const cond = [
	['API 요청 합계', apiReqs(s1a), apiReqs(s1b)],
	['GET /order/orders/{orderId} (홈: 주문마다 상세)', reqs(s1a, 'GET /order/orders/{orderId}'), reqs(s1b, 'GET /order/orders/{orderId}')],
	['GET /product/races/{raceId} (홈: 대회마다 정보)', reqs(s1a, 'GET /product/races/{raceId}'), reqs(s1b, 'GET /product/races/{raceId}')],
	['POST /order/orders (주문)', reqs(s1a, 'POST /order/orders'), reqs(s1b, 'POST /order/orders')],
	['POST /queue (줄 등록)', reqs(s1a, 'POST /queue'), reqs(s1b, 'POST /queue')],
].map(([l, av, bv]) => `<tr><td class="l">${l}</td><td>${n0(av)}</td><td>${n0(bv)}</td><td>${av ? `${bv - av > 0 ? '+' : ''}${Math.round(((bv - av) / av) * 100)}%` : '-'}</td></tr>`).join('');

const html = `<!doctype html>
<html lang="ko"><head><meta charset="utf8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>크루런 운영 부하 테스트 AS-IS / TO-BE — 상품 유니크 인덱스(CRU-88) 전후</title>
<style>
:root{--bg:#f6f7f8;--surface:#fff;--fg:#1b1f23;--muted:#5c646c;--border:#e4e7ea;--accent:#1d7a8c;--navbg:#eef1f3;
--ok:#3b6d11;--ok-bg:#eaf3de;--warn:#854f0b;--warn-bg:#faeeda;--crit:#a32d2d;--crit-bg:#fcebeb}
@media (prefers-color-scheme:dark){:root{--bg:#15181b;--surface:#1e2226;--fg:#e8eaec;--muted:#9aa2a9;--border:#2d3338;--accent:#5dcaba;--navbg:#1a1e22;
--ok:#97c459;--ok-bg:#223016;--warn:#f0b24a;--warn-bg:#36280f;--crit:#f09595;--crit-bg:#3a1a1a;color-scheme:dark}}
*{box-sizing:border-box}body{background:var(--bg);color:var(--fg);font-family:system-ui,-apple-system,sans-serif;line-height:1.6;margin:0;font-feature-settings:"tnum"}
main{max-width:1120px;margin:0 auto;padding:34px 20px 56px}
h1{font-size:24px;margin:0 0 4px}h2{font-size:17px;margin:0 0 8px}.sub{color:var(--muted);font-size:13.5px;margin:0 0 18px}
.card{background:var(--surface);border:1px solid var(--border);border-radius:14px;padding:20px 18px;margin-bottom:16px}
.banner{background:var(--warn-bg);color:var(--warn);border-radius:11px;padding:12px 15px;font-size:13.5px;margin-bottom:16px}
.good{border-left:4px solid var(--ok);border-radius:0 14px 14px 0}.bad{border-left:4px solid var(--crit);border-radius:0 14px 14px 0}
.big{font-size:18px;font-weight:600;margin:0 0 6px}
table{width:100%;border-collapse:collapse;font-size:13px}th,td{text-align:right;padding:7px 8px;border-bottom:1px solid var(--border);vertical-align:top;white-space:nowrap}
th:first-child,td:first-child,td.l,th.l{text-align:left}th{color:var(--muted);font-weight:500;font-size:11.5px}td.wrap{white-space:normal}
tr.grp td{background:var(--navbg)}.note2{color:var(--muted);font-size:12px}
.pill{display:inline-block;font-size:11.5px;font-weight:500;padding:2px 9px;border-radius:999px;background:var(--navbg)}
.pill.ok{background:var(--ok-bg);color:var(--ok)}.pill.crit{background:var(--crit-bg);color:var(--crit)}.pill.warn{background:var(--warn-bg);color:var(--warn)}
.cols{display:grid;grid-template-columns:1fr 1fr;gap:14px}.col{border:1px solid var(--border);border-radius:11px;padding:12px 14px}.col.to{border-color:var(--accent)}
.col .t{font-weight:600;margin-bottom:6px}ul{margin:6px 0 0;padding-left:18px}li{margin:4px 0;font-size:13.5px}
code{font-size:12px;background:var(--navbg);padding:1px 5px;border-radius:5px}.note{font-size:12px;color:var(--muted);margin-top:10px}
@media (max-width:760px){.cols{grid-template-columns:1fr}}
</style></head><body><main>
<h1>운영 부하 테스트 AS-IS / TO-BE — 상품 유니크 인덱스 전후</h1>
<p class="sub">AS-IS = 2차 (11/20 03:59~04:15, <code>${a.testid}</code>) · TO-BE = 3차 (11/20 11:31~11:47, <code>${b.testid}</code>) · 같은 스크립트·인원(300×1~3)·계정 · CRU-84 / CRU-88</p>
<div class="banner"><b>읽기 전에</b> — 인덱스의 직접 효과(교착·주문 실패)는 비교할 수 있지만, X·응답 시간은 <b>두 실행의 시작 조건이 달라</b> 인덱스 효과로 읽으면 안 된다. TO-BE는 배포 20~36분 뒤(덜 데워진 JVM)였고, 2구간에서 입구(Traefik)가 재시작됐고, 계정에 주문 기록이 더 쌓여 있었다. 아래 「조건 차이」 참고.</div>

<div class="card">
<h2>무엇을 바꿨나</h2>
<div class="cols">
<div class="col"><div class="t">AS-IS (2차)</div><ul>
<li>운영 <code>participation_hold</code>에 <code>participation_id</code> 유니크 인덱스 <b>없음</b></li>
<li>결제 판매 <code>findForUpdate(participationId)</code> EXPLAIN: <code>type=ALL</code>, <b>8,913행 훑으며 모두 잠금</b></li>
<li>옛 칸 <code>application_id</code>·<code>crew_application_id</code>, 옛 행 35개(<code>participation_id=0</code>)</li>
</ul></div>
<div class="col to"><div class="t">TO-BE (3차) — CRU-88, 11/20 11:12 배포</div><ul>
<li><code>uk_participation_hold_participation</code> <b>있음</b></li>
<li>같은 조회 EXPLAIN: <code>type=const</code>, <b>1행만 잠금</b></li>
<li>옛 칸 삭제, 옛 행 채움, kit_option 외래 키 추가, 옛 티셔츠 칸 삭제</li>
</ul></div>
</div>
</div>

<div class="card good">
<p class="big">직접 효과 — 교착이 사라졌다</p>
<div style="overflow-x:auto"><table><thead><tr><th class="l">지표</th><th>AS-IS</th><th>TO-BE</th><th>변화</th><th class="l">메모</th></tr></thead><tbody>${direct}</tbody></table></div>
<p class="note">교착은 「같은 순간 판매와 확보가 겹칠 때」 나는 일이라 서버가 느릴수록 더 잘 난다. 서버가 더 느렸던 TO-BE에서 0건이므로 인덱스가 원인을 없앤 것으로 본다.</p>
</div>

<div class="card bad">
<p class="big">성능 — 숫자는 나빠졌지만 인덱스 효과가 아니다 (조건 다름)</p>
<div style="overflow-x:auto"><table><thead><tr><th class="l">지표</th><th>AS-IS</th><th>TO-BE</th><th>변화</th><th class="l">메모</th></tr></thead><tbody>${perf}</tbody></table></div>
<p class="note">초록 = 좋아짐, 빨강 = 나빠짐. 구간 1에서 서버가 스스로 잰 시간까지 2~3배인 것은 요청이 기다린 시간이 아니라 서비스 안에서 더 오래 걸린 시간이라, 덜 데워진 JVM(JIT)의 직접 증거다. 구간 3에서는 서버 p95가 2차 수준으로 돌아왔다.</p>
</div>

<div class="card">
<h2>조건 차이 — 같게 맞춘 것과 달랐던 것</h2>
<div style="overflow-x:auto"><table><thead><tr><th class="l">항목</th><th>AS-IS</th><th>TO-BE</th><th>영향</th></tr></thead><tbody>
<tr><td class="l">스크립트·인원·간격·계정</td><td>같음</td><td>같음</td><td class="l wrap note2">93211ed1, 300×1~3, 240초, realflow1~2310</td></tr>
<tr><td class="l">서비스 JVM이 뜬 지 (1구간 오픈 때)</td><td>200~350분</td><td>19~36분</td><td class="l wrap"><span class="pill crit">큼</span> 배포 직후라 JIT 몫이 CPU를 더 씀</td></tr>
<tr><td class="l">오픈 직전 메인 CPU</td><td>13~14%</td><td>16~20%</td><td class="l wrap"><span class="pill warn">작음</span></td></tr>
<tr><td class="l">입구 재시작</td><td>web (04:09, 3구간)</td><td>Traefik (11:35:08, 2구간 오픈)</td><td class="l wrap"><span class="pill crit">큼</span> TO-BE 2구간 142명이 앱 열기에서 끊김</td></tr>
<tr><td class="l">계정의 주문 기록</td><td>1차 765건</td><td>1·2차 2,019건</td><td class="l wrap"><span class="pill warn">작음</span> 홈 화면 조회가 늘었지만 API 합계는 비슷 (아래)</td></tr>
<tr><td class="l">시간대</td><td>새벽 03:59</td><td>낮 11:31</td><td class="l wrap"><span class="pill ok">없음</span> 오픈 전 다른 요청 초당 0건</td></tr>
</tbody></table></div>
<h2 style="margin-top:16px">구간 1 요청 구성 (300명, 사람당 요청이 같은가)</h2>
<div style="overflow-x:auto"><table><thead><tr><th class="l">요청</th><th>AS-IS</th><th>TO-BE</th><th>차이</th></tr></thead><tbody>${cond}</tbody></table></div>
<p class="note">주문 기록이 쌓여 홈 화면 조회는 늘었지만 API 합계는 비슷하다. 요청량보다 JVM 예열과 입구 재시작이 결과를 가른 것으로 본다.</p>
</div>

<div class="card">
<h2>판정과 다음 측정</h2>
<ul>
<li><b>CRU-88 목표 달성</b>: 교착 11 → 0, 주문 503 → 0, 판매 잠금 8,913행 → 1행</li>
<li><b>X·p95 개선 여부는 아직 모른다</b>: 같은 조건에서 다시 재야 한다
<ul>
<li>예열 실행(1구간짜리) → 쉬기 → 본 측정 (AS-IS가 1차 뒤에 돈 것과 같은 상태)</li>
<li>새 계정 번호대로 사람당 요청 수를 맞춘다</li>
</ul></li>
<li><b>다음 개선 1순위</b>: 입구 Pod(web·Traefik)의 생존 확인 기준. 두 실행 모두 CPU 포화 때 입구가 재시작돼 그 순간 들어온 사람이 전부 실패했다</li>
</ul>
</div>
<p class="note">자료: <code>real-flow/results/prod-x2-*</code>, <code>prod-x3-*</code> · 상세 보고서: 「…결과_2026-11-20.html」(2차), 「…결과_3차_2026-11-20.html」(3차)</p>
</main></body></html>`;
fs.writeFileSync(OUT, html);
console.log('written', OUT, html.length);
