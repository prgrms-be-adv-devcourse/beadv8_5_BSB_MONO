// report-x4.mjs = report.mjs(2차 문구)를 4차 결과에 맞게 바꾼다. 숫자는 지표에서 계산하는 부분을 그대로 쓴다
import fs from 'node:fs';
const path = '/a/report-x4.mjs';
let s = fs.readFileSync(path, 'utf8');
const section = (id, html) => {
	const re = new RegExp(`<section class="page( show)?" id="page-${id}">[\\s\\S]*?</section>`);
	if (!re.test(s)) throw new Error('section ' + id);
	s = s.replace(re, html);
};
const rep = (a, b) => { if (!s.includes(a)) throw new Error('not found: ' + a.slice(0, 50)); s = s.replace(a, b); };
const sub = (re, b) => { if (!re.test(s)) throw new Error('not found: ' + re); s = s.replace(re, b); };

rep('<title>크루런 운영 부하 테스트 — 예매 오픈 몰림 X 측정 (real-flow)</title>', '<title>크루런 운영 부하 테스트 4차 — 상품 인덱스 반영 뒤, 2차와 같은 조건 (real-flow)</title>');
rep('<p class="brand">크루런 예매 오픈 몰림<br>운영 부하 테스트 (real-flow)</p>', '<p class="brand">크루런 예매 오픈 몰림<br>운영 부하 테스트 4차</p>');
rep('2026-11-20 03:59~04:15 KST · 운영 crewrun.duckdns.org · CRU-84', '2026-11-20 12:41~12:57 KST · 상품 인덱스(CRU-88) 반영 뒤 · 예열·새 계정 · CRU-84');
rep('<a data-page="appendix">부록 <span class="tag">1차 실행·작업 기록</span></a>', '<a data-page="appendix">부록 <span class="tag">정합성·작업 기록</span></a>');

section('overview', `<section class="page show" id="page-overview">
<h1>결론 한눈에</h1>
<p class="sub">상품 자리 확보 표에 유니크 인덱스를 넣은 뒤(CRU-88), <b>2차와 같은 조건</b>으로 다시 쟀다 — 예열 실행으로 JVM을 데우고, 주문 기록이 없는 새 계정으로</p>
<div class="kpis">
<div class="kpi"><div class="k">상품 자리 확보 교착</div><div class="v" style="color:var(--ok)">0건</div><div class="s">2차 11건 → 4차 0건 · 주문 503 11 → 0 · 서비스 5xx 11 → 0</div></div>
<div class="kpi"><div class="k">결제까지 끝난 예매 (3구간 합)</div><div class="v">\${n0(cnt('booking_completed'))}건</div><div class="s">2차 1,254 · 결제 503 \${n0(cnt('pay_busy_first'))} · 결제 못 함 \${n0(cnt('booking_failed_final'))} · 흐름 끊김 \${n0(cnt('flow_broken'))}</div></div>
<div class="kpi"><div class="k">정합성 어긋남</div><div class="v" style="color:var(--ok)">0건</div><div class="s">주문 결제 = 예치금 결제 54,010,000원 · 팔린 자리 = 결제 참가권 1,253</div></div>
<div class="kpi"><div class="k">X (예매 처리 속도)</div><div class="v">≈ 10건/초</div><div class="s">30초 창 최대 10.47(2차 10.03) · 3쌍 개인 정원 504석이 다 팔리는 데 <b>105초 = 2차 105초</b></div></div>
</div>
<div class="card good">
<p class="big" style="font-family:var(--head);font-size:19px;font-weight:600;margin:0 0 6px">인덱스로 <b style="color:var(--ok)">교착이 사라지고, 잠금 대기와 3구간 API 응답이 절반으로</b> 줄었다</p>
<p>2차에서 11명을 실패시킨 상품 자리 확보 교착이 0건이고, 상품·주문 로그의 ERROR도 0건이다. 결제 때 자리를 판매로 바꾸는 API(<code>holds/{id}/sell</code>)의 서버 p95가 132·71·235ms → 50·35·35ms,
MySQL 행 잠금 대기가 240·284·578건 → 126·155·134건, 잠금으로 기다린 시간 합이 39.6초 → 10.4초로 줄었다.
가장 붐빈 3구간에서 사용자 쪽 p95는 조회 6,371 → 2,891ms, 쓰기 6,006 → 3,186ms, 대기열 4,827 → 2,343ms로 절반이 됐다.<br>
<span style="color:var(--muted)">다만 1·2구간의 사용자 쪽 대기열·쓰기 p95는 오히려 59~95% 높았다. 같은 구간의 서버 p95·CPU 평균은 2차와 같아 서비스 밖(입구·CPU 차례)에서 기다린 차이이고, k6 p95(5초 창 최댓값)의 흔들림일 수 있다 — 원인은 확인하지 못했다.</span></p>
</div>
<div class="card verdict">
<p class="big">X는 그대로 약 10건/초 — 막고 있는 곳이 <b>상품 DB가 아니라 메인 EC2 CPU</b>이기 때문</p>
<p>메인 EC2(2코어)는 2구간부터 100%로 2차와 똑같이 찼고, CPU 1위는 여전히 web(Next.js) 화면 렌더링이다. 3구간 화면 요청의 \${n1((sumFail(s3, true) / sumReq(s3, true)) * 100)}%가 504로 실패했다(2차 19.9%, 그중 502는 web 재시작 몫).
인덱스는 결과를 「맞게」 만들고 잠금 대기를 줄였지만, 처리량을 막는 곳이 아니어서 X는 움직이지 않았다. 30초 창 X가 3구간에서 8.03(2차 10.03)으로 낮은 것은 결제가 한꺼번에 몰리지 않고 고르게 퍼져서이고, 정원이 다 팔리는 시간은 같다.</p>
</div>
<div class="card">
<h2>2차와 같게 맞춘 것</h2>
<ul>
<li><b>예열</b>: 본 측정 전에 300명짜리 1구간 실행(12:10~12:26, 옛 계정)을 돌리고 서버가 쉰 뒤(1분 부하 0.17) 시작했다. 1구간 메인 CPU 평균 51% = 2차 51%</li>
<li><b>새 계정</b>: 주문·파티 기록이 없는 realflow3001~5310과 새 크루 300개. 2차 계정에는 1차 1구간 기록이 조금 있어 4차의 API 요청이 구간마다 7~11% 적다(홈 화면 주문 조회 몫). 화면 요청 수는 같다</li>
<li>같은 스크립트·인원(300×1~3)·간격(240초)·준비 시간·판매자, Pod 재시작 없음, 다른 트래픽 없음</li>
</ul>
</div>
<div class="card">
<h2>다음</h2>
<ul>
<li><b>web 생존 확인 기준</b>: 4차에서도 3구간에 생존 확인을 7번 실패했다(연속 3번이 아니어서 재시작은 피함). 2차는 여기서 재시작됐다 → 제한 시간·실패 횟수를 늘리는 것이 1순위</li>
<li><b>X를 올리려면 CPU</b>: web 화면 렌더링 줄이기(대회 목록·상세 캐시), web을 메인 밖으로, 또는 메인 4코어</li>
</ul>
</div>
</section>`);

rep('const s3 = S[2], s1 = S[0];', 'const s3 = S[2], s1 = S[0], s2 = S[1];');
sub(/<li><b>1쌍 → 2쌍<\/b>[\s\S]*?<\/li>\s*<li><b>2쌍 → 3쌍<\/b>[\s\S]*?<\/li>/, `<li><b>1쌍 → 2쌍</b>: 처리 \${n1(xPeak('30s', '300x2') / xPeak('30s', '300x1'))}배. 사람이 2배인데 1.6배 → 이미 CPU가 막기 시작했다(2구간 CPU 100%). 2차 1.5배와 같다.</li>
<li><b>2쌍 → 3쌍</b>: 30초 창 최대가 \${n2(xPeak('30s', '300x2'))} → \${n2(X30)}건으로 오히려 낮다. 1분 창은 \${n2(xPeak('1m', '300x2'))} → \${n2(xPeak('1m', '300x3'))}건으로 조금 는다. 3쌍에서는 결제가 한순간에 몰리지 않고 고르게 퍼졌기 때문이고, 개인 정원 504석이 다 팔리는 데 걸린 시간은 <b>2차와 같은 105초</b>(절반은 60초, 2차 70초)다 → <b>X ≈ 초당 10건이 이 구성의 한계</b>라는 2차 결론 그대로.</li>`);
sub(/<li><b>N \(쾌적 최대 인원\)<\/b>[\s\S]*?<\/li>/, `<li><b>N (쾌적 최대 인원)</b>: 1쌍 300명에서도 p95 대기열 \${ms(kindP(s1, 'kindP95', 'queue'))}ms · 쓰기 \${ms(kindP(s1, 'kindP95', 'write'))}ms로 기준을 넘었다(조회 p95는 끊긴 요청 1건이 만든 값이라 뺀다) → <b>N &lt; 300</b> (화면 요청까지 함께 보낼 때). N은 100 → 300 구간으로 다시 재야 한다.</li>`);
rep("실제 브라우저에서는 오류 화면을 본 사람이 있다.", "실제 브라우저에서는 오류 화면(504)을 본 사람이 있다.");
rep('%가 502·504라 실제 브라우저', '%가 504라 실제 브라우저');
rep('가설이며 같은 조건으로 다시 돌려 확인할 것.', '4차는 예열 뒤인데도 1구간 결제 서버 p95가 1,838ms(2·3구간 426·290ms)로 높아, JIT만으로는 설명되지 않는다. 첫 구간의 결제가 같은 순간 몰리는 영향일 수 있다 — 확인 필요.');

rep('구간 1: 대회 1쌍(300명) 03:59 오픈 → 구간 2: 2쌍(600명) 04:03 → 구간 3: 3쌍(900명) 04:07', '구간 1: 대회 1쌍(300명) 12:41 오픈 → 구간 2: 2쌍(600명) 12:45 → 구간 3: 3쌍(900명) 12:49 (2차와 같은 간격)');
rep('실행 중 PC CPU 평균 19%·최대 47%', '실행 중 PC CPU 평균 20%·최대 77%(5초 표본 1개)');

sub(/<p class="note">정원은 몰린 인원의 70%라[\s\S]*?<\/p>/, `<p class="note">정원은 몰린 인원의 70%라 30%는 매진·자리 부족이 정상이다. 세 구간 모두 개인 정원은 끝까지 팔렸다(168·336·504). 매진 72·144·216명은 정확히 「개인 예매 − 정원」이다 — 중간에 실패한 사람이 한 명도 없다는 뜻이다(2차는 65·142·214 + 교착 실패).
파티 신청 42·84·126은 2차와 같다. <b>흐름 끊김 \${n0(cnt('flow_broken'))}건</b>(2차 11건, 상품 교착).</p>`);

sub(/<p class="note">p95는 구간 안에서 k6가 5초마다 계산한 p95의 최대값\(ms\)\.[\s\S]*?<\/p>/, `<p class="note">p95는 구간 안에서 k6가 5초마다 계산한 p95의 최대값(ms). 1구간 조회 p95 19,187ms는 <code>GET /order/orders?page</code> 356건 중 1건이 19초 뒤 연결이 끊긴(unexpected EOF) 값이다. 그 요청의 p50은 40~75ms다.
2·3구간 화면 실패는 모두 504(Next 서버 응답 늦음)이고 502는 0건이다(2차 3구간 502 508건은 web 재시작). 5xx를 낸 API는 없다.</p>`);

sub(/<p class="note">메인 CPU는 모드별로 user 1\.55[\s\S]*?<\/p>/, `<p class="note">메인 CPU 평균 \${n0(inst(s1.nodeCpuAvg, MAIN) * 100)}·\${n0(inst(s2.nodeCpuAvg, MAIN) * 100)}·\${n0(inst(s3.nodeCpuAvg, MAIN) * 100)}%로 2차(51·84·59%)와 같다. 예열이 됐다는 뜻이다(3차 1구간은 91%). 메모리 여유 최저 \${n0(inst(s3.nodeMemAvailMin, MAIN))}MB. 보조 EC2(DB)는 최대 50% 아래로 여유.</p>`);
sub(/<h2>병목에 닿은 순서와 근거<\/h2>[\s\S]*?<\/tbody><\/table><\/div>/, `<h2>병목에 닿은 순서와 근거</h2>
<div class="tscroll"><table><thead><tr><th>순서</th><th class="l">어디</th><th class="l">언제</th><th class="l">근거</th><th class="l">그래서</th></tr></thead><tbody>
<tr><td>1</td><td class="l">메인 EC2 CPU (2코어)</td><td class="l">2구간부터 100%</td><td class="wrap">2구간 평균 \${n0(inst(s2.nodeCpuAvg, MAIN) * 100)}%, 1분 부하 최대 \${n1(inst(s3.load1, MAIN))}. Pod CPU 1위 web(최대 0.74코어)</td><td class="wrap">모든 요청이 CPU 차례를 기다린다 → 화면 p95 18~24초</td></tr>
<tr><td>2</td><td class="l">web(Next.js)</td><td class="l">3구간 (12:49~12:50)</td><td class="wrap">생존 확인 실패 7번·준비 확인 실패 14번(TCP 1초). 연속 3번이 아니어서 재시작은 없음. 화면 504 \${n0(sumFail(s3, true))}건</td><td class="wrap">화면 실패 \${n1((sumFail(s3, true) / sumReq(s3, true)) * 100)}% (2차 19.9%, web 재시작 포함)</td></tr>
<tr><td>3</td><td class="l">Traefik (입구)</td><td class="l">—</td><td class="wrap">재시작 없음(3차에서는 2구간 오픈 순간 재시작)</td><td class="wrap">앱 열기 끊김 0</td></tr>
<tr><td>해결</td><td class="l">상품 자리 확보 (product)</td><td class="l">—</td><td class="wrap">교착 0, 상품·주문 ERROR 0, 판매 API p95 35~50ms(2차 71~235ms)</td><td class="wrap">CRU-88 인덱스 효과 확인</td></tr>
</tbody></table></div>`);

section('improve', `<section class="page" id="page-improve">
<h1>개선 제안 — 4차에서 확인한 것</h1>
<p class="sub">2차 제안 중 product 인덱스는 끝났고 효과를 같은 조건에서 확인했다. 남은 것은 메인 EC2 CPU와 입구</p>
<div class="card">
<div class="imp"><div class="t"><span class="pill ok">끝남</span> product — 자리 확보 표 유니크 인덱스 (CRU-88)</div>
<div class="row"><b>결과</b> 교착 11 → 0, 주문 503 11 → 0, 판매 API p95 132·71·235 → 50·35·35ms, 행 잠금 대기 약 절반, 잠금 대기 시간 합 −74%. X는 그대로(병목이 아님).</div></div>
<div class="imp"><div class="t"><span class="pill crit">1순위</span> web(Next.js) 생존 확인 기준</div>
<div class="row"><b>지금</b> TCP 1초 × 3번. 4차 3구간에서 7번 실패해 재시작 직전까지 갔고, 2차는 재시작돼 화면 502가 508건 났다. 3차는 같은 이유로 Traefik이 재시작됐다.</div>
<div class="row"><b>개선</b> 제한 3~5초·실패 횟수 6으로. Traefik도 HelmChartConfig로 같이.</div>
<div class="row"><b>주의</b> 인프라·프론트 담당과 함께. 진짜로 멈춘 Pod를 늦게 알아채는 대가가 있다.</div></div>
<div class="imp"><div class="t"><span class="pill crit">1순위</span> web 화면 렌더링 줄이기 · 메인 EC2 코어</div>
<div class="row"><b>지금</b> X ≈ 10건/초에서 멈춘 원인. 3구간 화면 실패 \${n1((sumFail(s3, true) / sumReq(s3, true)) * 100)}%(504), 메인 CPU 100%.</div>
<div class="row"><b>개선</b> 대회 목록·상세 캐시, web을 메인 EC2 밖으로, 또는 메인 4코어. 서비스 안 처리는 평균 수십 ms라 백엔드 코드보다 자원 배치가 먼저다.</div></div>
</div>
</section>`);

section('opinion', `<section class="page" id="page-opinion">
<h1>해석과 견해</h1>
<p class="sub">4차는 2차와 비교해도 되는 실행이다</p>
<div class="card">
<h2>1. 비교해도 되는 이유</h2>
<p>예열 뒤라 1구간 메인 CPU 평균이 2차와 같은 51%이고, 서버가 스스로 잰 order p95도 195·155·150ms로 2차(182·141·173ms)와 같은 수준이다. 구간별 게이트웨이 요청도 13,919·28,509·40,143건으로 2차(14,331·29,569·41,935건)와 3% 안이다.
3차처럼 입구가 재시작되지도 않았다. 다른 점은 새 계정이라 홈 화면 주문 조회가 조금 적다는 것(API 7~11% 적음)뿐이고, 화면 요청은 같다.</p>
</div>
<div class="card">
<h2>2. 인덱스가 바꾼 것과 못 바꾼 것</h2>
<p><b>바꾼 것</b>: 교착(실패한 사람 11 → 0), 판매 API의 꼬리 시간, 행 잠금 대기. 교착은 결과의 정확성 문제라 X보다 중요하다 — 2차는 「돈은 냈는데 자리 확보에 실패한」 사람이 생길 수 있는 조건이었다.<br>
<b>못 바꾼 것</b>: X. 줄을 서고 결제까지 가는 속도는 web 화면과 메인 CPU가 정한다. 3구간 개인 정원이 다 팔리는 시간이 두 실행 모두 105초인 것이 그 증거다.</p>
</div>
<div class="card">
<h2>3. 3구간 응답이 절반이 된 이유 — 둘이 섞여 있다</h2>
<p>① 잠금 대기가 줄어 주문·판매가 빨리 끝났다(판매 API p95 235 → 35ms). ② 2차는 3구간 중 web이 재시작돼 그 뒤 몰린 재접속이 CPU를 더 썼고, 4차는 재시작이 없었다.
②는 운의 몫도 있다 — 4차도 생존 확인을 7번 실패했다. 그래서 3구간 p95 절반을 전부 인덱스 효과로 말하지 않는다.</p>
</div>
<div class="card">
<h2>4. 측정 한계</h2>
<ul>
<li>한 번 실행한 결과다. X·p95는 실행마다 10% 안팎 흔들릴 수 있다</li>
<li>낮 시간(12:41~12:57)이지만 오픈 전 다른 요청은 없었다(1분 부하 0.17)</li>
<li>부하 PC는 원인이 아니다: PC CPU 평균 20%(2구간 오픈 때 5초 표본 하나만 77%)</li>
<li>서버 p95는 구간 지표로 계산한 근삿값이다</li>
</ul>
</div>
</section>`);

rep('<p class="sub">운영 Grafana (grafana.bukang-devops.duckdns.org) · 2026-11-20 03:56~04:16 KST', '<p class="sub">운영 Grafana (grafana.bukang-devops.duckdns.org) · 2026-11-20 12:38~12:58 KST');
s = s.replace(/운영-X-그라파나-/g, '운영-X4-그라파나-');
sub(/<figcaption>① 크루런 · 부하 테스트[^<]*<\/figcaption>/, '<figcaption>① 크루런 · 부하 테스트 — 2·3구간 오픈마다 초당 요청이 200건 안팎으로 오르고, 3구간에서 화면 504로 k6 실패율이 20%대. 서비스 5xx는 0, DB 풀 대기 0</figcaption>');
sub(/<figcaption>② 크루런 · 서버[^<]*<\/figcaption>/, '<figcaption>② 크루런 · 서버 — 메인 CPU가 2·3구간에서 100%, CPU 기다림(PSI) 90%대. 보조 EC2(DB)는 50% 아래로 여유</figcaption>');

section('appendix', `<section class="page" id="page-appendix">
<h1>부록</h1>
<div class="card">
<h2>정합성 (4차, 대회 136~147) — 어긋남 0</h2>
<table><tbody>
<tr><td>1. 결제 완료 금액 (주문 = 예치금)</td><td>54,010,000 = 54,010,000</td><td><span class="pill ok">0</span></td></tr>
<tr><td>2. 두 번 결제된 주문</td><td>0</td><td><span class="pill ok">0</span></td></tr>
<tr><td>3. 결제 확인 중으로 남은 주문</td><td>0</td><td><span class="pill ok">0</span></td></tr>
<tr><td>4. 종목 48개: 정원 ≥ 판매 = 결제 참가권</td><td>48개 모두 일치</td><td><span class="pill ok">0</span></td></tr>
<tr><td>5. 팔린 자리 = 결제 완료 참가권</td><td>1,253 = 1,253</td><td><span class="pill ok">0</span></td></tr>
</tbody></table>
</div>
<div class="card">
<h2>2차와 같게 맞춘 것 · 다른 것</h2>
<table><thead><tr><th class="l">항목</th><th class="l">4차</th></tr></thead><tbody>
<tr><td class="l">같음</td><td class="l wrap">스크립트(rush.js), STAGES 300x1~3, 구간 간격 240초, PREP 600, 판매자 realflow0, 로그인 제한 스위치 절차, 대기열 설정, 서버 사양, JVM이 데워진 상태(예열 실행 뒤), Pod 재시작 없음</td></tr>
<tr><td class="l">다름</td><td class="l wrap">상품 인덱스(바꾸려던 것) · 계정 realflow3001~5310·새 크루 300(주문 기록 없음, 2차 계정은 1차 기록 일부 있음) · 시간대(낮 12:41, 2차 새벽 03:59)</td></tr>
</tbody></table>
</div>
<div class="card">
<h2>운영에서 한 일</h2>
<ul>
<li>로그인 제한 스위치: 12:04 끔 → 12:58 되돌림(<code>"false"</code>), 429 확인</li>
<li>새 계정 2,310개 가입(본인인증 값 Redis에 1시간), 크루 300개(실흐름-mwoe7cu1-*), 예치금 채우기, 크루 가입일 30일 전으로</li>
<li>예열 실행: 12:10~12:26, testid <code>realflow-warm4-1120-1210</code>, 대회 134·135, 옛 계정 realflow1~570 (결제 210, 흐름 끊김 0)</li>
<li>본 실행: 대회 12개(136~147) 등록, 판매 1,253석</li>
</ul>
</div>
<div class="card">
<h2>자료 위치</h2>
<ul>
<li>결과: <code>real-flow/results/prod-x4.*</code>, 예열 <code>prod-warm4.*</code> (요약 json·로그·k6 리포트·Prometheus 지표·정합성·PC CPU)</li>
<li>Grafana: k6 테스트 <code>\${d.testid}</code>, 캡처 <code>캡처/운영-X4-그라파나-*.png</code></li>
<li>2차 결과: 「크루런_운영_예매오픈몰림_X_부하테스트_결과_2026-11-20.html」, 비교: 「…_AS-IS_TO-BE_2차_4차.html」</li>
</ul>
</div>
</section>`);

fs.writeFileSync(path, s);
console.log('patched');
