# 예매 몰림 운영 부하 테스트

team05(운영 dev, 2 vCPU·7.6GB, k3s)에서 예매 오픈 몰림(대기열→주문→결제)에 부하를 줘 병목·최대 처리량을 재는 한 벌. **다른 시나리오에도 복사해 쓰도록** 구성했다.

**결과 한 줄:** 병목은 2코어 CPU(조회 경로). 안정 **조회 ~100 RPS**·330 RPS 붕괴, **예매 완결 ~20 TPS** 수준. DB 커넥션풀·메모리·GC는 끝까지 여유, 크래시·OOM 0.

## 파일
```
guide.md     일반 가이드 + 시나리오 템플릿 + "읽은 AI에게" (먼저 읽기)
runbook.md   예매 funnel 재현 단계별
results.md   계획 + 실측 수치·병목 분석
data/        수집 자료 원본(요약·원시 CSV 압축·TPS 계산·Prometheus 시계열) + data/README.md(스크립트 흐름·부하 방식·파일 구조, AI가 읽기 좋게)
k6/
  funnel.js         기준선(대기자·구매자 분리, 조회를 일정하게 유지하는 최악 가정). STAGE: smoke/s1/s2/s3/buystress
  rush.js           실제 흐름: N명이 한 대회에 줄 서서 차례로 신청→주문→결제. STAGE: r30/r300/r600/r990
  login-prod.js     테스트 회원 throttle 로그인 → .accounts.json
  prepare-race.js   실행마다 새 대회 2개(구매용·대기용) 등록 + 판매 바로 시작 → race.json
  run-stage.ps1     미니PC(Windows) 실행 래퍼: 새 대회 생성 → funnel → 요약·원시 CSV·시간창을 runs\<태그>\ 에 저장
  run-login.ps1     로그인 래퍼
  probe.js          새 대회에서 신청→주문→결제 1건이 실제로 되는지 확인 (run-probe.ps1)
  analyze.py        원시 CSV → 초당 RPS·TPS, 엔드포인트별 p95·p99
  promrange.py      실행 시간창의 Prometheus 시계열(노드·파드 CPU/메모리·hikari·힙·GC·서버 RPS) → JSON
  promsnap.py       Grafana→Prometheus 지표 스냅샷(실행 중 눈으로 볼 때)
```

## 빠른 시작
1. `guide.md`의 B(준비)·C(시드)로 회원·비번·예치금·대상 데이터를 운영 DB에 심는다.
2. 발생기(team05 밖)에서 단계별로:
   ```
   LOADTEST_PASSWORD=<비번> COUNT=1000 k6 run login-prod.js
   powershell -File run-stage.ps1 -Stage s1 -Tag <날짜-커밋>     # 새 대회 생성 → funnel
   ```
3. 측정: `GRAFANA_PW=<비번> python promrange.py <시작UTC> <끝UTC> s1.prom.json` (시간창은 `s1.window.txt`), `python analyze.py s1-raw.csv s1.tps.json`.

## 알아둘 것
- **운영 k6 스크립트다**(prod 가드 완화). 로컬 전용인 `backend/loadtest/scenario-a`와 별도이며, funnel 로직의 원본은 거기 있다.
- **대회는 실행마다 새로 만든다**(`prepare-race.js`). 신청은 대회당 1번이라, 대회를 재사용하면 구매가 전부 409(중복 신청)로 끝나 주문·결제가 한 건도 실행되지 않는다. 2026-11-19 오전 실행이 그랬고, 실패율 0%로 "통과"처럼 보였다. 그래서 funnel.js는 신청 409를 실패로 센다.
- 실행한 뒤에는 **체크 건수**(`신청 201`·`주문 201/200`·`결제 200/202`)가 0이 아닌지 꼭 본다.
- **실행별 배포 커밋을 남긴다**(`runs\<태그>\MANIFEST.md`). 배포가 끼면 같은 날 실행이라도 조건이 다르다.
- 토큰은 30분 수명 + nginx `/auth` 1/s 제한 → **단계마다 필요 인원만 로그인하고 바로 실행**.
- 비밀번호·토큰·자격증명은 파일에 없다(실행 시 환경변수). `.accounts.json` 등은 `.gitignore`로 제외.
- 수치는 **RPS**(요청/초)와 **TPS**(완결 거래/초)를 구분해 읽는다.

## 다른 테스트 파생
`guide.md` H절 템플릿을 채우고 공통 틀(B~G)은 그대로 재사용한다. `funnel.js`를 복사해 exec 함수(API 호출)만 바꾸면 다른 흐름 테스트가 된다. 가이드를 읽은 AI는 I절대로 "뭘 파생/도와줄지"를 먼저 묻는다.
