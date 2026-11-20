// 2차(AS-IS) vs 4차(TO-BE, 예열·새 계정으로 2차와 같은 조건) 비교 HTML. 사용: node compare4.mjs <x2.json> <x4.json> <out.html>
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
const svcN = (st, job) => st.svcReqs.find((r) => r.m.job === job)?.v;
const apiReqs = (st) => st.nameReqs.filter((r) => r.m.kind !== 'page').reduce((s, r) => s + r.v, 0);
const fails = (st, page) => st.nameFail.filter((r) => (page ? r.m.kind === 'page' : r.m.kind !== 'page')).reduce((s, r) => s + r.v, 0);
const pageReqs = (st) => st.nameReqs.filter((r) => r.m.kind === 'page').reduce((s, r) => s + r.v, 0);
const cnt = (d, name, stage) => d.counters[name].filter((r) => !stage || r.m.stage === stage).reduce((s, r) => s + r.v, 0);
const x = (d, st, w = '30s') => d.xPeak[w].find((r) => r.m.stage === st)?.v;
const sell = (d, i, key) => d.stages[i][key].find((r) => r.m.uri === '/internal/v1/product/holds/{participationId}/sell')?.v;
const lockW = (d, i) => d.stages[i].mysqlRowLockWaits[0]?.v;
const stages = ['300x1', '300x2', '300x3'];

// 변화 칸: 낮을수록 좋은 지표(lowerBetter) 기준으로 좋아짐·나빠짐 색. 5% 안은 비슷(회색)
function delta(av, bv, lowerBetter, unit = '') {
	if (av === undefined || bv === undefined) return '<td>-</td>';
	if (unit === '%') {
		const d = bv - av;
		const cls = Math.abs(d) < 3 ? '' : (lowerBetter ? d < 0 : d > 0) ? 'ok' : 'crit';
		return `<td><span class="pill ${cls}">${d > 0 ? '+' : ''}${n0(d)}%p</span></td>`;
	}
	const diff = bv - av;
	if (Math.abs(diff) < 1e-9) return '<td><span class="pill">같음</span></td>';
	const rel = av ? diff / av : 1;
	const pct = av ? ` (${diff > 0 ? '+' : ''}${Math.round(rel * 100)}%)` : '';
	const cls = Math.abs(rel) < 0.05 ? '' : (lowerBetter ? diff < 0 : diff > 0) ? 'ok' : 'crit';
	return `<td><span class="pill ${cls}">${diff > 0 ? '+' : ''}${n0(diff)}${unit}${pct}</span></td>`;
}
const row = (label, av, bv, lowerBetter, fmt = n0, unit = '', note = '') =>
	`<tr><td class="l">${label}</td><td>${fmt(av)}${unit}</td><td>${fmt(bv)}${unit}</td>${delta(av, bv, lowerBetter, unit)}<td class="l wrap note2">${note}</td></tr>`;

// 직접 효과
const direct = [
	row('상품 자리 확보 교착 (MySQL deadlock)', 11, 0, true, n0, '건', '상품 로그 <code>Deadlock found</code>. TO-BE는 실행 내내 0, 상품·주문 ERROR 로그도 0'),
	row('주문 503 (자리 확보 실패)', 11, 0, true, n0, '건', '교착이 상품 500 → 주문 503으로 보이던 것. 실행 전체 k6 집계'),
	row('흐름 끊김 (예매하다 실패한 사람)', 11, cnt(b, 'flow_broken'), true, n0, '명', 'TO-BE는 앱 열기·주문·결제 어디서도 끊긴 사람이 없다'),
	row('결제 503 · 결제 못 함', cnt(a, 'pay_busy_first') + cnt(a, 'booking_failed_final'), cnt(b, 'pay_busy_first') + cnt(b, 'booking_failed_final'), true, n0, '', '두 실행 모두 0'),
	row('정합성 어긋남', 0, 0, true, n0, '건', '결제 금액 = 예치금, 팔린 자리 = 결제 참가권 (2차 1,254석 · 4차 1,253석)'),
	...[0, 1, 2].map((i) => row(`판매 확정 API 서버 p95 — 구간 ${i + 1}`, sell(a, i, 'uriP95Ms'), sell(b, i, 'uriP95Ms'), true, n0, 'ms', i === 0 ? '<code>POST /internal/v1/product/holds/{id}/sell</code> — 교착이 나던 그 잠금 조회(findForUpdate)' : '')),
	...[0, 1, 2].map((i) => row(`MySQL 행 잠금 대기 — 구간 ${i + 1}`, lockW(a, i), lockW(b, i), true, n0, '건', i === 0 ? '<code>innodb_row_lock_waits</code> 증가. 잠금으로 기다린 시간 합은 실행 전체 39.6초 → 10.4초' : '')),
].join('');

// 성능 (같은 조건)
const perf = stages.map((stg, i) => {
	const sa = a.stages[i], sb = b.stages[i];
	return `<tr class="grp"><td class="l" colspan="5"><b>구간 ${i + 1} (${i + 1}쌍, ${300 * (i + 1)}명)</b></td></tr>`
		+ row('X — 결제 완료 예매/초 (30초 창)', x(a, stg), x(b, stg), false, n2, '', i === 2 ? '결제가 고르게 퍼져 순간 최대가 낮다. 정원 504석이 다 팔리는 시간은 둘 다 105초' : '')
		+ row('X — 1분 창', x(a, stg, '1m'), x(b, stg, '1m'), false, n2, '', '')
		+ row('메인 CPU 평균', inst(sa.nodeCpuAvg) * 100, inst(sb.nodeCpuAvg) * 100, true, n0, '%', i === 0 ? '같음 = 예열이 됐다는 뜻(3차는 91%)' : '')
		+ row('사용자 쪽 조회 p95', kind(sa, 'kindP95', 'read') * 1000, kind(sb, 'kindP95', 'read') * 1000, true, n0, 'ms', i === 0 ? 'TO-BE 19초는 356건 중 연결이 끊긴 1건이 만든 값(그 요청 p50 40~75ms)' : '')
		+ row('사용자 쪽 대기열 p95', kind(sa, 'kindP95', 'queue') * 1000, kind(sb, 'kindP95', 'queue') * 1000, true, n0, 'ms', '')
		+ row('사용자 쪽 쓰기 p95', kind(sa, 'kindP95', 'write') * 1000, kind(sb, 'kindP95', 'write') * 1000, true, n0, 'ms', '')
		+ row('서버 p95 — order', svcP(sa, 'order-service'), svcP(sb, 'order-service'), true, n0, 'ms', '서비스가 스스로 잰 시간')
		+ row('서버 p95 — product', svcP(sa, 'product-service'), svcP(sb, 'product-service'), true, n0, 'ms', '')
		+ row('API 실패', fails(sa, false), fails(sb, false), true, n0, '건', i > 0 ? 'AS-IS: 주문 503(교착)' : '')
		+ row('화면 실패 (502·504)', fails(sa, true), fails(sb, true), true, n0, '건', i === 2 ? `AS-IS ${n0(pageReqs(sa))}건 중(502 508건 = web 재시작) / TO-BE ${n0(pageReqs(sb))}건 중(모두 504·시간 초과)` : '');
}).join('');

// 조건: 사람당 요청이 같은가 (구간별)
const cond = stages.map((stg, i) => {
	const sa = a.stages[i], sb = b.stages[i];
	const r = (l, av, bv) => `<tr><td class="l">구간 ${i + 1} · ${l}</td><td>${n0(av)}</td><td>${n0(bv)}</td><td>${av ? `${bv - av > 0 ? '+' : ''}${Math.round(((bv - av) / av) * 100)}%` : '-'}</td></tr>`;
	return r('게이트웨이 요청', svcN(sa, 'gateway'), svcN(sb, 'gateway')) + r('k6 화면 요청', pageReqs(sa), pageReqs(sb)) + r('k6 API 요청', apiReqs(sa), apiReqs(sb));
}).join('');

const html = `<!doctype html>
<html lang="ko"><head><meta charset="utf8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>크루런 운영 부하 테스트 AS-IS / TO-BE — 상품 유니크 인덱스(CRU-88) 전후, 2차 vs 4차</title>
<style>
:root{--bg:#f6f7f8;--surface:#fff;--fg:#1b1f23;--muted:#5c646c;--border:#e4e7ea;--accent:#1d7a8c;--navbg:#eef1f3;
--ok:#3b6d11;--ok-bg:#eaf3de;--warn:#854f0b;--warn-bg:#faeeda;--crit:#a32d2d;--crit-bg:#fcebeb}
@media (prefers-color-scheme:dark){:root{--bg:#15181b;--surface:#1e2226;--fg:#e8eaec;--muted:#9aa2a9;--border:#2d3338;--accent:#5dcaba;--navbg:#1a1e22;
--ok:#97c459;--ok-bg:#223016;--warn:#f0b24a;--warn-bg:#36280f;--crit:#f09595;--crit-bg:#3a1a1a;color-scheme:dark}}
*{box-sizing:border-box}body{background:var(--bg);color:var(--fg);font-family:system-ui,-apple-system,sans-serif;line-height:1.6;margin:0;font-feature-settings:"tnum"}
main{max-width:1120px;margin:0 auto;padding:34px 20px 56px}
h1{font-size:24px;margin:0 0 4px}h2{font-size:17px;margin:0 0 8px}.sub{color:var(--muted);font-size:13.5px;margin:0 0 18px}
.card{background:var(--surface);border:1px solid var(--border);border-radius:14px;padding:20px 18px;margin-bottom:16px}
.banner{background:var(--ok-bg);color:var(--ok);border-radius:11px;padding:12px 15px;font-size:13.5px;margin-bottom:16px}
.good{border-left:4px solid var(--ok);border-radius:0 14px 14px 0}.warn{border-left:4px solid var(--warn);border-radius:0 14px 14px 0}
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
<h1>운영 부하 테스트 AS-IS / TO-BE — 상품 유니크 인덱스 전후 (2차 vs 4차)</h1>
<p class="sub">AS-IS = 2차 (11/20 03:59~04:15, <code>${a.testid}</code>) · TO-BE = 4차 (11/20 12:41~12:57, <code>${b.testid}</code>) · 같은 스크립트·인원(300×1~3)·간격 · CRU-84 / CRU-88</p>
<div class="banner"><b>이번에는 비교해도 된다</b> — 3차는 배포 직후(덜 데워진 JVM)·입구 재시작·주문이 쌓인 계정 때문에 비교할 수 없었다. 4차는 예열 실행 뒤 서버가 쉰 다음, 주문 기록이 없는 새 계정으로 돌렸다. 1구간 메인 CPU 평균이 2차와 같은 51%이고, 구간별 게이트웨이 요청도 3% 안으로 같다.</div>

<div class="card">
<h2>무엇을 바꿨나</h2>
<div class="cols">
<div class="col"><div class="t">AS-IS (2차)</div><ul>
<li>운영 <code>participation_hold</code>에 <code>participation_id</code> 유니크 인덱스 <b>없음</b></li>
<li>결제 판매 <code>findForUpdate(participationId)</code> EXPLAIN: <code>type=ALL</code>, <b>표 전체를 훑으며 모두 잠금</b></li>
<li>옛 칸 <code>application_id</code>·<code>crew_application_id</code>, 옛 행 35개(<code>participation_id=0</code>)</li>
</ul></div>
<div class="col to"><div class="t">TO-BE (4차) — CRU-88, 11/20 11:12 배포</div><ul>
<li><code>uk_participation_hold_participation</code> <b>있음</b></li>
<li>같은 조회 EXPLAIN: <code>type=const</code>, <b>1행만 잠금</b></li>
<li>옛 칸 삭제, 옛 행 채움, kit_option 외래 키 추가, 옛 티셔츠 칸 삭제</li>
</ul></div>
</div>
</div>

<div class="card good">
<p class="big">직접 효과 — 교착이 사라지고 잠금 대기가 줄었다</p>
<div style="overflow-x:auto"><table><thead><tr><th class="l">지표</th><th>AS-IS</th><th>TO-BE</th><th>변화</th><th class="l">메모</th></tr></thead><tbody>${direct}</tbody></table></div>
<p class="note">초록 = 좋아짐, 빨강 = 나빠짐, 회색 = 5% 안(비슷). 교착은 「같은 순간 판매와 확보가 겹칠 때」 나는 일이라, 같은 몰림을 다시 줘도 0건이면 인덱스가 원인을 없앤 것으로 본다.</p>
</div>

<div class="card warn">
<p class="big">처리 속도(X)는 그대로, 붐빌 때 API 응답은 절반으로</p>
<div style="overflow-x:auto"><table><thead><tr><th class="l">지표</th><th>AS-IS</th><th>TO-BE</th><th>변화</th><th class="l">메모</th></tr></thead><tbody>${perf}</tbody></table></div>
<ul>
<li><b>X ≈ 초당 10건 그대로</b>: 메인 EC2 CPU(2코어)가 2구간부터 100%로 같고, CPU 1위는 web(Next.js) 화면 렌더링이다. 인덱스는 이 병목과 무관하다.</li>
<li><b>3구간 API p95가 절반</b>: ① 잠금 대기가 줄어 주문·판매가 빨리 끝났고(판매 API p95 235 → 35ms) ② 2차는 3구간 중 web이 재시작돼 재접속이 몰렸지만 4차는 재시작이 없었다. ②에는 운도 섞여 있다 — 4차도 web 생존 확인을 7번 실패했다(연속 3번이 아니라 살아남음).</li>
<li><b>1·2구간 사용자 쪽 p95는 오히려 나빠졌다</b>(대기열·쓰기 +59~95%). 같은 구간의 서버 p95(order·product)는 ±10% 안으로 같고 CPU 평균도 같아, 서비스 안이 아니라 입구·CPU 차례에서 기다린 시간 차이다. k6 p95는 5초 창 p95의 최댓값이라 순간 몰림 하나에 크게 흔들린다. 원인은 아직 확인하지 못했고, 한 번 더 돌려 흔들림 폭을 봐야 한다.</li>
</ul>
</div>

<div class="card">
<h2>조건 — 같게 맞춘 것과 남은 차이</h2>
<div style="overflow-x:auto"><table><thead><tr><th class="l">항목</th><th>AS-IS</th><th>TO-BE</th><th class="l">영향</th></tr></thead><tbody>
<tr><td class="l">스크립트·인원·간격·준비 시간·판매자</td><td>같음</td><td>같음</td><td class="l wrap note2">rush.js, 300×1~3, 240초, PREP 600, realflow0</td></tr>
<tr><td class="l">JVM 상태</td><td>1차 실행 뒤</td><td>예열 실행(300×1) 뒤</td><td class="l wrap"><span class="pill ok">같게 맞춤</span> 1구간 메인 CPU 평균 51% = 51%</td></tr>
<tr><td class="l">시작 직전 서버</td><td>쉬는 상태</td><td>1분 부하 0.17</td><td class="l wrap"><span class="pill ok">같게 맞춤</span></td></tr>
<tr><td class="l">계정</td><td>realflow1~2310 (1차 1구간 기록 일부)</td><td>realflow3001~5310 (기록 없음)</td><td class="l wrap"><span class="pill warn">작음</span> 홈 화면 주문 조회가 적어 TO-BE API 요청이 7~11% 적다. 화면 요청은 같다</td></tr>
<tr><td class="l">입구 재시작</td><td>web (04:09, 3구간)</td><td>없음</td><td class="l wrap"><span class="pill warn">중간</span> 결과 차이의 일부. 원인(짧은 생존 확인)은 그대로 남아 있다</td></tr>
<tr><td class="l">시간대</td><td>새벽 03:59</td><td>낮 12:41</td><td class="l wrap"><span class="pill ok">없음</span> 오픈 전 다른 요청 없음</td></tr>
</tbody></table></div>
<h2 style="margin-top:16px">사람당 요청이 같은가 (구간별 요청 수)</h2>
<div style="overflow-x:auto"><table><thead><tr><th class="l">요청</th><th>AS-IS</th><th>TO-BE</th><th>차이</th></tr></thead><tbody>${cond}</tbody></table></div>
</div>

<div class="card">
<h2>판정과 다음</h2>
<ul>
<li><b>CRU-88 목표 달성</b>: 교착 11 → 0, 주문 503 11 → 0, 흐름 끊김 11 → 0, 판매 잠금 표 전체 → 1행. 같은 조건에서 확인했다</li>
<li><b>X는 인덱스로 오르지 않는다</b>: 막는 곳이 메인 EC2 CPU라서. 올리려면 web 화면 렌더링 줄이기(캐시)·web을 메인 밖으로·메인 4코어</li>
<li><b>다음 개선 1순위</b>: web·Traefik 생존 확인 기준(제한 3~5초, 실패 6번). 2차 web·3차 Traefik이 재시작됐고, 4차도 web이 재시작 직전까지 갔다</li>
</ul>
</div>
<p class="note">자료: <code>real-flow/results/prod-x2-*</code>, <code>prod-x4-*</code>, 예열 <code>prod-warm4-*</code> · 상세 보고서: 「…결과_2026-11-20.html」(2차), 「…결과_4차_2026-11-20.html」(4차)</p>
</main></body></html>`;
fs.writeFileSync(OUT, html);
console.log('written', OUT, html.length);
