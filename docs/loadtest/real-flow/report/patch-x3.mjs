// report-x3.mjs = report.mjs(2차 문구)를 3차 결과에 맞게 바꾼다. 숫자는 지표에서 계산하는 부분을 그대로 쓴다
import fs from 'node:fs';
const path = '/a/report-x3.mjs';
let s = fs.readFileSync(path, 'utf8');
const section = (id, html) => {
	const re = new RegExp(`<section class="page( show)?" id="page-${id}">[\\s\\S]*?</section>`);
	if (!re.test(s)) throw new Error('section ' + id);
	s = s.replace(re, html);
};
const rep = (a, b) => { if (!s.includes(a)) throw new Error('not found: ' + a.slice(0, 50)); s = s.replace(a, b); };

rep('<title>크루런 운영 부하 테스트 — 예매 오픈 몰림 X 측정 (real-flow)</title>', '<title>크루런 운영 부하 테스트 3차 — 상품 인덱스 반영 뒤 (real-flow)</title>');
rep('<p class="brand">크루런 예매 오픈 몰림<br>운영 부하 테스트 (real-flow)</p>', '<p class="brand">크루런 예매 오픈 몰림<br>운영 부하 테스트 3차</p>');
rep('2026-11-20 03:59~04:15 KST · 운영 crewrun.duckdns.org · CRU-84', '2026-11-20 11:31~11:47 KST · 상품 인덱스(CRU-88) 반영 뒤 · CRU-84');
rep('<a data-page="appendix">부록 <span class="tag">1차 실행·작업 기록</span></a>', '<a data-page="appendix">부록 <span class="tag">정합성·작업 기록</span></a>');

section('overview', `<section class="page show" id="page-overview">
<h1>결론 한눈에</h1>
<p class="sub">상품 자리 확보 표에 유니크 인덱스를 넣은 뒤(CRU-88, 11:12 배포), 2차와 같은 스크립트·인원·계정으로 다시 돌렸다</p>
<div class="kpis">
<div class="kpi"><div class="k">상품 자리 확보 교착</div><div class="v" style="color:var(--ok)">0건</div><div class="s">2차 11건 → 3차 0건 · 주문 503 6 → 0</div></div>
<div class="kpi"><div class="k">결제까지 끝난 예매 (3구간 합)</div><div class="v">\${n0(cnt('booking_completed'))}건</div><div class="s">결제 503 \${n0(cnt('pay_busy_first'))} · 결제 못 함 \${n0(cnt('booking_failed_final'))}</div></div>
<div class="kpi"><div class="k">정합성 어긋남</div><div class="v" style="color:var(--ok)">0건</div><div class="s">주문 결제 = 예치금 결제 53,780,000원 · 팔린 자리 = 결제 참가권 1,246</div></div>
<div class="kpi"><div class="k">X (30초 창, 3쌍)</div><div class="v" style="color:var(--crit)">\${n2(X30)}건/초</div><div class="s">2차 10.03 · <b>시작 조건이 달라 비교 불가</b>(아래)</div></div>
</div>
<div class="card good">
<p class="big" style="font-family:var(--head);font-size:19px;font-weight:600;margin:0 0 6px">인덱스로 <b style="color:var(--ok)">교착은 사라졌다</b> — 결제 판매가 행 1개만 잠근다</p>
<p>2차에서 11명을 실패시킨 상품 자리 확보 교착(MySQL deadlock → 상품 500 → 주문 503)이 0건이 됐다. 주문 단계의 흐름 끊김도 0건이다.
운영 DB에서 판매 조회 EXPLAIN이 <code>type=ALL</code>(8,913행) → <code>const</code>(1행)로 바뀐 것을 배포 뒤 확인했다.</p>
</div>
<div class="card verdict">
<p class="big">X·응답 시간은 2차보다 나빠졌지만 <b>인덱스 때문이 아니다</b> — 3차는 시작 조건이 달랐다</p>
<p>① <b>덜 데워진 서비스</b>: 11:12 배포로 모든 서비스가 새로 떴고, 1구간 오픈 때 JVM이 뜬 지 19~36분이었다(2차 200~350분, 1차 부하로 이미 데워짐).
처음 몰리는 요청에 자주 쓰는 코드를 기계어로 바꾸는 일(JIT)까지 2코어가 떠안아, 1구간 메인 CPU 평균이 51% → \${n0(inst(s1.nodeCpuAvg, MAIN) * 100)}%, 서버가 스스로 잰 order p95가 182 → \${n0(s1.svcP95Ms.find((r) => r.m.job === 'order-service')?.v)}ms가 됐다.<br>
② <b>입구 재시작</b>: 2구간 오픈 순간(11:35:08) Traefik이 CPU를 기다리다 생존 확인(2초 × 3번)에 답하지 못해 재시작됐다. 약 1분 동안 연결 거부(<code>connection refused</code> 788건) → 앱 열기에서 142명이 끊겼다.<br>
③ <b>계정에 쌓인 주문</b>: 같은 계정을 세 번째 쓰면서 홈 화면의 주문 상세 조회가 1구간 266 → 794건으로 늘었다(API 합계는 8,977 → 8,933으로 비슷).</p>
</div>
<div class="card">
<h2>다음 측정</h2>
<ul>
<li><b>데운 뒤 측정</b>: 배포 직후가 아니라, 1구간짜리 예열 실행을 먼저 돌리고 서버가 쉰 뒤 본 측정을 한다 (2차가 1차 뒤에 돈 것과 같은 조건)</li>
<li><b>새 계정 번호대</b>: 주문 기록이 없는 계정으로 돌려 사람당 요청 수를 2차와 맞춘다</li>
<li><b>입구 생존 확인</b>: web(2차)·Traefik(3차) 모두 CPU 포화 때 짧은 생존 확인 제한에 걸려 재시작됐다 → 제한 시간·실패 횟수를 늘리는 것이 X보다 먼저다</li>
</ul>
</div>
</section>`);

rep('구간 1: 대회 1쌍(300명) 03:59 오픈 → 구간 2: 2쌍(600명) 04:03 → 구간 3: 3쌍(900명) 04:07', '구간 1: 대회 1쌍(300명) 11:31 오픈 → 구간 2: 2쌍(600명) 11:35 → 구간 3: 3쌍(900명) 11:39 (2차와 같은 간격)');
rep('실행 중 PC CPU 평균 19%·최대 47%', '실행 중 PC CPU 평균 19%·최대 44%');

// X 탭: 사람 단위 결과 아래 문장
s = s.replace(/<p class="note">정원은 몰린 인원의 70%라[\s\S]*?<\/p>/, `<p class="note">정원은 몰린 인원의 70%라 30%는 매진·자리 부족이 정상이다. 세 구간 모두 개인 정원은 끝까지 팔렸다(168·336·504). 2구간 파티 신청이 84 → 81인 것은 입구 재시작으로 파티장 29명이 앱 열기에서 끊겨서다.
흐름 끊김 \${n0(cnt('flow_broken'))}건은 모두 <b>앱 열기</b>(재발급·로그인 연결 실패)이고 142건이 2구간(Traefik 재시작)이다. <b>주문 단계 끊김은 0건</b>(2차 11건, 상품 교착).</p>`);

// API 탭: 구간별 증상 아래 문장
s = s.replace(/<p class="note">p95는 구간 안에서 k6가 5초마다 계산한 p95의 최대값\(ms\)\.[\s\S]*?<\/p>/, `<p class="note">p95는 구간 안에서 k6가 5초마다 계산한 p95의 최대값(ms). 2구간 API 실패 559건은 Traefik 재시작 1분 동안의 연결 거부(<code>status 0</code>)이고, 3구간 화면 실패는 504(Next 서버 응답 늦음)다. 5xx를 낸 API는 없다.
1구간이 2차보다 크게 느린 것은 덜 데워진 JVM 때문이다(결론 참고). 서버가 스스로 잰 p95도 1구간에서 2~3배였다가 3구간에서 2차 수준으로 돌아왔다.</p>`);

// 서비스·자원: 원인 지표 아래 문장과 병목 표
s = s.replace(/<p class="note">메인 CPU는 모드별로 user 1\.55[\s\S]*?<\/p>/, `<p class="note">1구간 메인 CPU 평균이 2차 51% → 3차 \${n0(inst(s1.nodeCpuAvg, MAIN) * 100)}%. 같은 요청량(API 8,977 vs 8,933)에서 CPU가 더 든 것은 배포 직후 JIT 컴파일 몫으로 본다(서비스 JVM 가동 19~36분). 메모리 여유 최저 \${n0(inst(s3.nodeMemAvailMin, MAIN))}MB.</p>`);
s = s.replace(/<h2>병목에 닿은 순서와 근거<\/h2>[\s\S]*?<\/tbody><\/table><\/div>/, `<h2>병목에 닿은 순서와 근거</h2>
<div class="tscroll"><table><thead><tr><th>순서</th><th class="l">어디</th><th class="l">언제</th><th class="l">근거</th><th class="l">그래서</th></tr></thead><tbody>
<tr><td>1</td><td class="l">메인 EC2 CPU (2코어)</td><td class="l">1구간부터 100%</td><td class="wrap">1구간 평균 \${n0(inst(s1.nodeCpuAvg, MAIN) * 100)}%(2차 51%), 1분 부하 최대 \${n1(inst(s1.load1, MAIN))}. 배포 직후라 JIT 몫이 더해짐</td><td class="wrap">모든 요청이 CPU 차례를 기다린다 → 2차보다 이른 구간부터 느려짐</td></tr>
<tr><td>2</td><td class="l">Traefik (입구)</td><td class="l">11:35:08 (2쌍 오픈 순간)</td><td class="wrap">생존 확인 HTTP 2초 × 3번 실패 → 재시작(재시작 횟수 6, 직전 종료 11:35:08). 약 1분 연결 거부 788건</td><td class="wrap">앱 열기 142명 끊김, 2구간 API 실패 559건</td></tr>
<tr><td>3</td><td class="l">web(Next.js)</td><td class="l">3구간</td><td class="wrap">생존·준비 확인 시간 초과 경고(재시작은 없음). 화면 504 \${n0(sumFail(s3, true))}건</td><td class="wrap">화면 실패 \${n1((sumFail(s3, true) / sumReq(s3, true)) * 100)}%</td></tr>
<tr><td>해결</td><td class="l">상품 자리 확보 (product)</td><td class="l">—</td><td class="wrap">교착 0건, 상품 ERROR 0, 주문의 자리 확보 실패 0 (2차 교착 11건)</td><td class="wrap">CRU-88 인덱스 효과 확인</td></tr>
</tbody></table></div>`);

section('improve', `<section class="page" id="page-improve">
<h1>개선 제안 — 3차에서 바뀐 것</h1>
<p class="sub">2차 제안 중 product 인덱스는 끝났다. 남은 것의 순서가 바뀌었다</p>
<div class="card">
<div class="imp"><div class="t"><span class="pill ok">끝남</span> product — 자리 확보 표 유니크 인덱스 (CRU-88)</div>
<div class="row"><b>결과</b> 교착 11 → 0, 주문 503 6 → 0, 판매 조회 EXPLAIN ALL(8,913행) → const(1행). 운영 반영 11/20 11:12.</div></div>
<div class="imp"><div class="t"><span class="pill crit">1순위</span> 입구·화면 Pod의 생존 확인 기준</div>
<div class="row"><b>지금</b> web(TCP 1초 × 3)과 Traefik(HTTP 2초 × 3)이 CPU 포화 때 답하지 못해, 2차는 web·3차는 Traefik이 재시작됐다. 입구가 1분 막히면 그 순간 들어온 사람 전부가 실패한다.</div>
<div class="row"><b>개선</b> 생존 확인 제한 3~5초·실패 횟수 6으로. Traefik은 k3s 기본 설치라 HelmChartConfig로 덮어쓴다.</div>
<div class="row"><b>주의</b> 인프라·프론트 담당과 함께. 진짜로 멈춘 Pod를 늦게 알아채는 대가가 있다.</div></div>
<div class="imp"><div class="t"><span class="pill crit">1순위</span> web(Next.js) 화면 렌더링 줄이기 · 메인 EC2 코어</div>
<div class="row"><b>지금</b> 2차와 같다. 3구간 화면 실패 \${n1((sumFail(s3, true) / sumReq(s3, true)) * 100)}%(504), 메인 CPU 100%.</div>
<div class="row"><b>개선</b> 대회 목록·상세 캐시, web을 메인 EC2 밖으로, 또는 메인 4코어.</div></div>
<div class="imp"><div class="t"><span class="pill warn">측정</span> 배포 직후에는 재지 않는다</div>
<div class="row"><b>지금</b> 3차는 배포 20~36분 뒤라 JIT 몫이 결과에 섞였다.</div>
<div class="row"><b>개선</b> 예열 실행 → 쉬기 → 본 측정. 계정 번호대도 새로.</div></div>
</div>
</section>`);

section('opinion', `<section class="page" id="page-opinion">
<h1>해석과 견해</h1>
<p class="sub">3차에서 무엇을 믿고, 무엇을 믿지 않을지</p>
<div class="card">
<h2>1. 믿어도 되는 것 — 교착 제거와 정합성</h2>
<p>교착은 부하 크기와 상관없이 「같은 순간 판매와 확보가 겹치면」 나는 일이라, 서버가 느렸던 3차에서 오히려 더 잘 날 조건이었다. 그런데 0건이다. 인덱스가 원인을 없앴다고 본다.
정합성도 세 번째로 0건이다(결제 금액 = 예치금, 팔린 자리 = 결제 참가권).</p>
</div>
<div class="card">
<h2>2. 믿으면 안 되는 것 — 2차와 X·p95 비교</h2>
<p>3차의 X·p95는 「인덱스 넣은 뒤의 성능」이 아니라 「배포 직후 + 입구 재시작 + 주문 기록이 쌓인 계정」의 성능이다. 세 가지 모두 인덱스와 무관하게 결과를 나쁘게 만든다.
특히 1구간은 서버가 스스로 잰 시간까지 2~3배였다(order p95 182 → \${n0(s1.svcP95Ms.find((r) => r.m.job === 'order-service')?.v)}ms). 이것은 요청이 기다린 시간이 아니라 서비스 안에서 더 오래 걸린 시간이라, JIT 예열 부족의 직접 증거다.</p>
</div>
<div class="card">
<h2>3. 새로 보인 것 — 입구가 가장 약한 고리</h2>
<p>2차는 web, 3차는 Traefik이 같은 방식(짧은 생존 확인 + CPU 포화)으로 재시작됐다. 어느 Pod가 먼저 넘어지는지는 그날의 CPU 순서에 달렸다.
입구가 재시작되면 그 1분에 들어온 사람은 모두 실패하므로, X를 올리는 일보다 「CPU가 차도 입구는 죽지 않게」가 먼저다.</p>
</div>
<div class="card">
<h2>4. 측정 한계</h2>
<ul>
<li>낮 시간(11:31~11:47)에 돌렸지만 오픈 전 gateway 요청은 초당 0건으로, 다른 사람의 트래픽은 없었다</li>
<li>부하 PC는 원인이 아니다: PC CPU 평균 19%</li>
<li>서버 p95는 구간 지표(구간 약 69개)로 계산한 근삿값이다</li>
</ul>
</div>
</section>`);

rep('<p class="sub">운영 Grafana (grafana.bukang-devops.duckdns.org) · 2026-11-20 03:56~04:16 KST', '<p class="sub">운영 Grafana (grafana.bukang-devops.duckdns.org) · 2026-11-20 11:28~11:48 KST');
s = s.replace(/운영-X-그라파나-/g, '운영-X3-그라파나-');
s = s.replace(/<figcaption>① 크루런 · 부하 테스트[^<]*<\/figcaption>/, '<figcaption>① 크루런 · 부하 테스트 — 11:35 연결 거부로 k6 실패율이 35%까지 오른다(Traefik 재시작). 서버 CPU(main)는 1구간부터 100%</figcaption>');
s = s.replace(/<figcaption>② 크루런 · 서버[^<]*<\/figcaption>/, '<figcaption>② 크루런 · 서버 — 메인 CPU 100%가 1구간부터 이어진다. 보조 EC2는 여유</figcaption>');

section('appendix', `<section class="page" id="page-appendix">
<h1>부록</h1>
<div class="card">
<h2>정합성 (3차, 대회 122~133) — 어긋남 0</h2>
<table><tbody>
<tr><td>1. 결제 완료 금액 (주문 = 예치금)</td><td>53,780,000 = 53,780,000</td><td><span class="pill ok">0</span></td></tr>
<tr><td>2. 두 번 결제된 주문</td><td>0</td><td><span class="pill ok">0</span></td></tr>
<tr><td>3. 결제 확인 중으로 남은 주문</td><td>0</td><td><span class="pill ok">0</span></td></tr>
<tr><td>4. 종목 48개: 정원 ≥ 판매 = 결제 참가권</td><td>48개 모두 일치</td><td><span class="pill ok">0</span></td></tr>
<tr><td>5. 팔린 자리 = 결제 완료 참가권</td><td>1,246 = 1,246</td><td><span class="pill ok">0</span></td></tr>
</tbody></table>
</div>
<div class="card">
<h2>2차와 같게 맞춘 것 · 다른 것</h2>
<table><thead><tr><th class="l">항목</th><th class="l">3차</th></tr></thead><tbody>
<tr><td class="l">같음</td><td class="l wrap">스크립트(93211ed1), STAGES 300x1~3, 구간 간격 240초, PREP 600, 계정 realflow1~2310, 크루 300, 판매자 realflow0, 로그인 제한 스위치 절차, 준비 뒤 오픈까지 쉬는 시간(약 10분)</td></tr>
<tr><td class="l">다름</td><td class="l wrap">상품 인덱스(바꾸려던 것) · 서비스 JVM 가동 19~36분(2차 200~350분) · 계정 주문 기록 누적(1·2차 2,019건) · 시간대(낮 11:31, 2차 새벽 03:59)</td></tr>
</tbody></table>
</div>
<div class="card">
<h2>운영에서 한 일</h2>
<ul>
<li>로그인 제한 스위치: 11:16 끔 → 11:47 되돌림(<code>"false"</code>), 429·사이트 200 확인</li>
<li>대회 12개(122~133) 등록, 판매 1,246석. 계정·크루는 1·2차 것을 그대로 썼다</li>
</ul>
</div>
<div class="card">
<h2>자료 위치</h2>
<ul>
<li>결과: <code>real-flow/results/prod-x3.*</code> (요약 json·로그·k6 리포트·Prometheus 지표·정합성·PC CPU)</li>
<li>Grafana: k6 테스트 <code>\${d.testid}</code>, 캡처 <code>캡처/운영-X3-그라파나-*.png</code></li>
<li>2차 결과: 「크루런_운영_예매오픈몰림_X_부하테스트_결과_2026-11-20.html」, 비교: 「…_AS-IS_TO-BE_2차_3차.html」</li>
</ul>
</div>
</section>`);

fs.writeFileSync(path, s);
console.log('patched');
