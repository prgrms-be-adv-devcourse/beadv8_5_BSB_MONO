# 예매 몰림 운영 부하 테스트

team05(운영 dev, 2 vCPU·7.6GB, k3s)에서 예매 오픈 몰림(대기열→주문→결제)에 부하를 줘 병목·최대 처리량을 재는 한 벌. **다른 시나리오에도 복사해 쓰도록** 구성했다.

**결과 한 줄:** 병목은 2코어 CPU(조회 경로). 안정 **조회 ~100 RPS**·330 RPS 붕괴, **예매 완결 ~20 TPS** 수준. DB 커넥션풀·메모리·GC는 끝까지 여유, 크래시·OOM 0.

## 파일
```
guide.md     일반 가이드 + 시나리오 템플릿 + "읽은 AI에게" (먼저 읽기)
runbook.md   예매 funnel 재현 단계별
results.md   계획 + 실측 수치·병목 분석
k6/
  funnel.js         예매 funnel(대기열→신청→주문→결제). STAGE: smoke/s1/s2/s3/buystress
  login-prod.js     테스트 회원 throttle 로그인 → .accounts.json
  run-*.ps1         미니PC(Windows) 실행 래퍼 (예시 — k6 경로만 맞추면 됨)
  promsnap.py       Grafana→Prometheus 지표 스냅샷(CPU·메모리·hikari·힙)
```

## 빠른 시작
1. `guide.md`의 B(준비)·C(시드)로 회원·비번·예치금·대상 데이터를 운영 DB에 심는다.
2. 발생기(team05 밖)에서 단계별로:
   ```
   LOADTEST_PASSWORD=<비번> COUNT=300 k6 run login-prod.js
   k6 run -e STAGE=s1 --summary-export s1.json funnel.js
   ```
3. 측정: Grafana 또는 `GRAFANA_PW=<비번> python promsnap.py 15 40 > s1-metrics.txt`.

## 알아둘 것
- **운영 k6 스크립트다**(prod 가드 완화). 로컬 전용인 `backend/loadtest/scenario-a`와 별도이며, funnel 로직의 원본은 거기 있다.
- **buystress는 `-e BUY_COURSE=17`처럼 신규 코스로** 돌려야 한다 — s1~s3에서 이미 산 구매자(1~900)가 재주문하면 409만 난다.
- 토큰은 30분 수명 + nginx `/auth` 1/s 제한 → **단계마다 필요 인원만 로그인하고 바로 실행**.
- 비밀번호·토큰·자격증명은 파일에 없다(실행 시 환경변수). `.accounts.json` 등은 `.gitignore`로 제외.
- 수치는 **RPS**(요청/초)와 **TPS**(완결 거래/초)를 구분해 읽는다.

## 다른 테스트 파생
`guide.md` H절 템플릿을 채우고 공통 틀(B~G)은 그대로 재사용한다. `funnel.js`를 복사해 exec 함수(API 호출)만 바꾸면 다른 흐름 테스트가 된다. 가이드를 읽은 AI는 I절대로 "뭘 파생/도와줄지"를 먼저 묻는다.
