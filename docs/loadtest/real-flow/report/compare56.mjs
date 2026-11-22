// 5차(AS-IS, 인덱스 지움) vs 6차(TO-BE, 인덱스 되살림) 비교 HTML. 고친 스크립트(화면이 안 뜨면 새로고침 2번 뒤 이탈)로 잰 두 실행
// 사용: node compare56.mjs <x5.json> <x6.json> <out.html>
import fs from 'node:fs';
const [, , A, B, OUT] = process.argv;
const a = JSON.parse(fs.readFileSync(A, 'utf8'));
const b = JSON.parse(fs.readFileSync(B, 'utf8'));
const n0 = (x) => (x === undefined || x === null || Number.isNaN(x) ? '-' : Math.round(x).toLocaleString('ko-KR'));
const n2 = (x) => (x === undefined || Number.isNaN(x) ? '-' : x.toFixed(2));
const MAIN = 'ip-10-0-1-145';
const inst = (arr) => arr.find((r) => r.m.instance === MAIN)?.v;
const kind = (st, k) => st.kindP95.find((r) => r.m.kind === k)?.v;
const svcP = (st, job) => st.svcP95Ms.find((r) => r.m.job === job)?.v;
const svcN = (st, job) => st.svcReqs.find((r) => r.m.job === job)?.v;
const sumBy = (arr, page) => arr.filter((r) => (r.m.kind === 'page') === page).reduce((s, r) => s + r.v, 0);
const cnt = (d, name, stage, flow) => d.counters[name].filter((r) => (!stage || r.m.stage === stage) && (!flow || r.m.flow === flow)).reduce((s, r) => s + r.v, 0);
const x = (d, st, w = '30s') => d.xPeak[w].find((r) => r.m.stage === st)?.v;
const sell = (d, i) => d.stages[i].uriP95Ms.find((r) => r.m.uri === '/internal/v1/product/holds/{participationId}/sell')?.v;
const lockW = (d, i) => d.stages[i].mysqlRowLockWaits[0]?.v;
const stages = ['300x1', '300x2', '300x3'];
// flow_broken{where=화면} 구간별 합 (Prometheus, 카운터 JSON에는 where가 없다)
const SCREEN_A = [0, 26, 269];
const SCREEN_B = [1, 14, 274];

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

const direct = [
	row('상품 자리 확보 교착 (로그 <code>Deadlock found</code>)', 28, 0, true, n0, '줄', '본 실행 동안 상품 로그. 예열에서도 5차 32줄 · 6차 0줄'),
	row('주문 단계에서 끊긴 사람 (교착 → 상품 500 → 주문 503)', 7, 0, true, n0, '명', 'k6 <code>flow_broken{where=order}</code>'),
	row('서비스 5xx (order·product·gateway 각각)', 7, 0, true, n0, '건', '구간별 3·3·1 → 0·0·0'),
	row('결제 503 · 결제 못 함', cnt(a, 'pay_busy_first') + cnt(a, 'booking_failed_final'), cnt(b, 'pay_busy_first') + cnt(b, 'booking_failed_final'), true, n0, '', '두 실행 모두 0'),
	row('정합성 어긋남', 0, 0, true, n0, '건', '결제 금액 = 예치금, 팔린 자리 = 결제 참가권 (5차 1,213석 · 6차 1,225석)'),
	...[0, 1, 2].map((i) => row(`판매 확정 API 서버 p95 — 구간 ${i + 1}`, sell(a, i), sell(b, i), true, n0, 'ms', i === 0 ? '<code>POST /internal/v1/product/holds/{id}/sell</code> — 교착이 나던 잠금 조회. 6차 최대도 60~89ms' : '')),
	...[0, 1, 2].map((i) => row(`MySQL 행 잠금 대기 — 구간 ${i + 1}`, lockW(a, i), lockW(b, i), true, n0, '건', i === 0 ? '잠금으로 기다린 시간 합(실행 전체) 30.0초 → 6.8초' : '')),
].join('');

const perf = stages.map((stg, i) => {
	const sa = a.stages[i], sb = b.stages[i];
	return `<tr class="grp"><td class="l" colspan="5"><b>구간 ${i + 1} (${i + 1}쌍, ${300 * (i + 1)}명)</b></td></tr>`
		+ row('X — 결제 완료 예매/초 (30초 창)', x(a, stg), x(b, stg), false, n2, '', '')
		+ row('X — 1분 창', x(a, stg, '1m'), x(b, stg, '1m'), false, n2, '', '')
		+ row('결제까지 끝난 개인 예매', cnt(a, 'booking_completed', stg, 'individual'), cnt(b, 'booking_completed', stg, 'individual'), false, n0, '명', `개인 정원 ${168 * (i + 1)}석`)
		+ row('메인 CPU 평균', inst(sa.nodeCpuAvg) * 100, inst(sb.nodeCpuAvg) * 100, true, n0, '%', i === 0 ? '두 실행 모두 예열 뒤' : '')
		+ row('사용자 쪽 쓰기 p95', kind(sa, 'write') * 1000, kind(sb, 'write') * 1000, true, n0, 'ms', i === 0 ? '주문·결제·파티 신청' : '')
		+ row('서버 p95 — order', svcP(sa, 'order-service'), svcP(sb, 'order-service'), true, n0, 'ms', '서비스가 스스로 잰 시간')
		+ row('서버 p95 — product', svcP(sa, 'product-service'), svcP(sb, 'product-service'), true, n0, 'ms', '')
		+ row('화면이 끝내 안 떠 이탈한 사람', SCREEN_A[i], SCREEN_B[i], true, n0, '명', i === 2 ? '새로고침 2번에도 안 뜸 → 다음 단계로 못 감 (고친 스크립트)' : '')
		+ row('화면 실패 (502·504·연결 실패)', sumBy(sa.nameFail, true), sumBy(sb.nameFail, true), true, n0, '건', i === 2 ? `5차 ${n0(sumBy(sa.nameReqs, true))}건 중 / 6차 ${n0(sumBy(sb.nameReqs, true))}건 중 (새로고침 포함)` : '');
}).join('');

const cond = stages.map((stg, i) => {
	const sa = a.stages[i], sb = b.stages[i];
	const r = (l, av, bv) => `<tr><td class="l">구간 ${i + 1} · ${l}</td><td>${n0(av)}</td><td>${n0(bv)}</td><td>${av ? `${bv - av > 0 ? '+' : ''}${Math.round(((bv - av) / av) * 100)}%` : '-'}</td></tr>`;
	return r('게이트웨이 요청', svcN(sa, 'gateway'), svcN(sb, 'gateway')) + r('k6 화면 요청', sumBy(sa.nameReqs, true), sumBy(sb.nameReqs, true)) + r('k6 API 요청', sumBy(sa.nameReqs, false), sumBy(sb.nameReqs, false));
}).join('');

const html = `<!doctype html>
<html lang="ko"><head><meta charset="utf8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>크루런 운영 부하 테스트 AS-IS / TO-BE — 상품 유니크 인덱스 지움(5차) vs 되살림(6차)</title>
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
figure{margin:0 0 12px}figure img{width:100%;border:1px solid var(--border);border-radius:10px}figcaption{font-size:12.5px;color:var(--muted)}
@media (max-width:760px){.cols{grid-template-columns:1fr}}
</style></head><body><main>
<h1>운영 부하 테스트 AS-IS / TO-BE — 상품 유니크 인덱스 (5차 vs 6차)</h1>
<p class="sub">AS-IS = 5차 (11/20 15:01~15:17, 인덱스 지움, <code>${a.testid}</code>) · TO-BE = 6차 (11/20 15:48~16:04, 인덱스 되살림, <code>${b.testid}</code>) · 같은 날 연달아, 같은 스크립트·인원(300×1~3)·상품 조건 · CRU-84 / CRU-88</p>
<div class="banner"><b>인덱스 하나만 바꾼 비교</b> — 두 실행 모두 예열 실행 뒤, 주문 기록이 없는 새 계정(5차 realflow6001~8310, 6차 realflow9001~11310)과 새 크루로 돌렸다. 대회는 실행마다 새로 등록(이름만 오픈 시각으로 다름, 종목·가격·정원 같음). 스크립트는 <b>화면이 오류로 뜨면 사람처럼 새로고침하고, 끝내 안 뜨면 그 사람은 거기서 멈추게</b> 고쳤다(2·4차는 화면이 실패해도 다음 API로 넘어가 TPS가 부풀었다).</div>

<div class="card">
<h2>무엇을 바꿨나 — 운영 DB에서 인덱스만 지웠다가 되살림</h2>
<div class="cols">
<div class="col"><div class="t">AS-IS (5차) — 14:29 인덱스 지움</div><ul>
<li><code>alter table participation_hold drop index uk_participation_hold_participation</code></li>
<li>판매 <code>findForUpdate(participationId)</code> EXPLAIN: <code>type=ALL</code>, <b>13,121행 훑으며 잠금</b> (2차 때 8,913행보다 큼)</li>
</ul></div>
<div class="col to"><div class="t">TO-BE (6차) — 15:17 인덱스 되살림</div><ul>
<li>중복 0건 확인 뒤 <code>create unique index uk_participation_hold_participation (participation_id)</code></li>
<li>같은 조회 EXPLAIN: <code>const</code>, <b>1행만 잠금</b> (CRU-88 배포 상태와 같음)</li>
</ul></div>
</div>
</div>

<div class="card good">
<p class="big">직접 효과 — 인덱스가 있으면 교착이 없고, 판매 API가 1/3~1/7로 빨라진다</p>
<div style="overflow-x:auto"><table><thead><tr><th class="l">지표</th><th>AS-IS</th><th>TO-BE</th><th>변화</th><th class="l">메모</th></tr></thead><tbody>${direct}</tbody></table></div>
<p class="note">초록 = 좋아짐, 빨강 = 나빠짐, 회색 = 5% 안(비슷). 인덱스를 지우자 교착이 다시 나고(예열 포함), 되살리자 다시 0이 됐다 — 원인과 효과를 같은 날 양쪽으로 확인했다.</p>
</div>

<div class="card warn">
<p class="big">처리 속도(X)는 두 실행이 같다 — 막는 곳이 메인 EC2 CPU(web 화면)라서</p>
<div style="overflow-x:auto"><table><thead><tr><th class="l">지표</th><th>AS-IS</th><th>TO-BE</th><th>변화</th><th class="l">메모</th></tr></thead><tbody>${perf}</tbody></table></div>
<ul>
<li><b>X ≈ 초당 10건(30초 창 최대 10.66 vs 10.87)</b>: 메인 CPU는 2구간부터 100%로 같고, 3구간 개인 정원이 다 팔리는 시간도 115초 vs 110초로 비슷하다.</li>
<li><b>3구간에는 두 실행 모두 정원이 남았다</b>(504석 중 494·488석): 화면이 60초 시간 초과로 안 떠서 약 270명이 결제 전에 멈췄다. 고친 스크립트가 이제 이 사람들을 실패로 센다 — 실제 사용자라면 오류 화면 앞에서 떠난 사람들이다.</li>
<li><b>같은 방식으로 비교한 X</b>: 2·4차의 10.03·8.03은 화면 실패를 건너뛴 값이라 5·6차와 같은 줄에 놓지 않는다.</li>
</ul>
</div>

<div class="card">
<h2>조건 — 같게 맞춘 것과 남은 차이</h2>
<div style="overflow-x:auto"><table><thead><tr><th class="l">항목</th><th>AS-IS (5차)</th><th>TO-BE (6차)</th><th class="l">영향</th></tr></thead><tbody>
<tr><td class="l">스크립트·인원·간격·준비 시간·판매자·상품 조건</td><td>같음</td><td>같음</td><td class="l wrap note2">고친 rush.js, 300×1~3, 240초, PREP 600, realflow0, 4종목</td></tr>
<tr><td class="l">예열</td><td>14:30~14:46 (300×1)</td><td>15:17~15:33 (300×1)</td><td class="l wrap"><span class="pill ok">같게 맞춤</span> 1구간 메인 CPU 평균 51% vs 49%</td></tr>
<tr><td class="l">계정·크루</td><td>새 계정 2,310 · 새 크루 300</td><td>새 계정 2,310 · 새 크루 300</td><td class="l wrap"><span class="pill ok">같게 맞춤</span> 주문 기록 없음</td></tr>
<tr><td class="l">자리 확보 표 행 수 (실행 시작 때)</td><td>약 13,900</td><td>약 16,300</td><td class="l wrap"><span class="pill warn">작음</span> 인덱스가 있으면 행 수와 상관없음. 없을 때(5차)는 행이 많을수록 불리</td></tr>
<tr><td class="l">Pod 재시작</td><td>없음</td><td>없음</td><td class="l wrap"><span class="pill ok">같음</span> 두 번 다 web이 생존 확인을 9·10번 실패했지만 연속 3번은 아니었다</td></tr>
<tr><td class="l">부하 PC CPU (실행 구간 평균)</td><td>21% (최대 66%)</td><td>40% (최대 100%)</td><td class="l wrap"><span class="pill warn">작음</span> 6차 1구간 오픈 직후 15:48:27~49:04에 100% 근처 4번. 1구간 숫자는 조심해서 읽는다</td></tr>
</tbody></table></div>
<h2 style="margin-top:16px">사람당 요청이 같은가 (구간별 요청 수)</h2>
<div style="overflow-x:auto"><table><thead><tr><th class="l">요청</th><th>AS-IS</th><th>TO-BE</th><th>차이</th></tr></thead><tbody>${cond}</tbody></table></div>
</div>

<div class="card">
<h2>판정과 다음</h2>
<ul>
<li><b>CRU-88 효과 확정</b>: 인덱스가 없으면 교착(주문 실패 7명·5xx 7건)이 나고, 있으면 0. 판매 API p95 237·209·88 → 33·31·32ms, 잠금 대기 시간 −77%</li>
<li><b>X는 인덱스로 오르지 않는다</b>: 막는 곳은 메인 EC2 CPU와 web 화면. 3구간에서 화면이 안 떠 약 270명이 결제 전에 떠났다 — 이것이 지금 가장 큰 손실이다</li>
<li><b>다음 1순위</b>: web 화면 렌더링 줄이기(대회 목록·상세 캐시)·web을 메인 밖으로·생존 확인 기준 늘리기</li>
<li>참고: 여러 실행에서 요청 한 건이 정확히 19.17~19.19초 뒤 <code>unexpected EOF</code>로 끊기는 값이 p95를 튀게 했다(5차 2·3구간, 6차 1·2구간). 입구 어딘가의 시간 제한으로 보이며 따로 확인할 것</li>
</ul>
</div>
<div class="card">
<h2>Grafana</h2>
<figure><img src="캡처/운영-X5-그라파나-1-부하테스트.png" alt="5차"><figcaption>AS-IS 5차 — 서비스 5xx 그래프에 교착 오류가 구간마다 뾰족하게 보인다</figcaption></figure>
<figure><img src="캡처/운영-X6-그라파나-1-부하테스트.png" alt="6차"><figcaption>TO-BE 6차 — 서비스 5xx 0</figcaption></figure>
</div>
<p class="note">자료: <code>real-flow/results/prod-x5-*</code>, <code>prod-x6-*</code>, 예열 <code>prod-warm5-*</code>·<code>prod-warm6-*</code> · 캡처 <code>캡처/운영-X5·X6-그라파나-1~5.png</code></p>
</main></body></html>`;
fs.writeFileSync(OUT, html);
console.log('written', OUT, html.length);
