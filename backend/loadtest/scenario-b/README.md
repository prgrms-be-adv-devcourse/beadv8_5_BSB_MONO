# 시나리오 B: 예치금(cash) 부하 테스트

시나리오 A는 게이트웨이부터 결제까지 전체 흐름을 봤어요. 그때 결제는 초당 4건이라 cash에는 부하가 거의 없었어요.
B는 cash API를 직접 불러 cash 한 곳의 한계를 봐요. 숫자는 운영 성능이 아니라, 맥 한 대에서 같은 조건으로 다시 잴 수 있는 값이에요.

| | 무엇을 보나 | 코드에서 의심한 곳 |
|---|---|---|
| B-0 | 같은 주문·같은 정산 동시 요청, 결제→환불·정산 섞기에서 돈이 한 번만 움직이는지 | (회귀 확인) |
| B-1 | 초당 결제 20→50→100→200→400건에서 어디서 기준을 넘는지 | 모든 결제·환불이 홀딩 지갑 한 행을 잠금 |
| B-2 | 정산 지급이 섞인 1분 동안 결제가 얼마나 느려지는지 | 정산 지급도 같은 홀딩 지갑 행을 잠금 |
| B-3 | 카드 1·3·5장 회원의 전액 환급 응답 시간 | 카드 결제취소를 한 장씩 차례로 부름 |
| B-4 | 기한 알림 배치의 걸린 시간·SQL 수 (대상 100·1,000·10,000명) | 회원마다 지갑·충전을 다시 조회 |

## 판정 기준

측정 전에 정해 이 파일에 적었어요. 숫자를 보고 기준을 바꾸지 않아요. 근거 없이 고른 값에는 「추천값(미확정)」을 붙였어요.
팀 공통 응답 시간 기준은 아직 정해지지 않았어요.

**근거가 있는 값**

| 대상 | 기준 | 근거 |
|---|---|---|
| 결제(B-1·B-2) | p99 < 1초 | 주문이 예치금 응답을 2초까지 기다려요(`order.clients.timeout: 2s`). 넘으면 주문이 503으로 끝나요. 그 절반 아래로 둬요 |
| 결제(B-1·B-2) | p95 2초 이상이면 경보 | 기획 건강 지표 H7(`docs/ideation/07-north-star-metrics.md`)의 경보선 |
| 돈(모두) | `verify.sh`의 어긋난 금액·건수 0, 데드락·락 대기 시간 초과 0 | 건강 지표 H5(중복 결제 0)·H6(예치금 장부 불일치 0원) |

**추천값(미확정)**

| 대상 | 기준 | 고른 이유 |
|---|---|---|
| 결제(B-1·B-2) | p95 < 200ms | 시나리오 A에서 결제 p95가 약 60ms였어요. 그 세 배쯤을 넘으면 느려졌다고 봐요 |
| 환급(B-3) | p95 < 1초 | 화면에서 기다리는 요청이에요. 시나리오 A의 쓰기 기준(근거 기록 없음)과 같은 값이에요 |
| 결제·환급 | 실패 < 1% | 시나리오 A와 결제 잠금 측정(`docs/measurements/2026-11-13-결제-잠금-판정기준.md`)이 같은 값을 써요 |
| 결제(B-1) | 버려진 반복 0 | 목표 속도를 못 따라가면 생겨요. 처리량 천장의 신호예요 |
| 배치(B-4) | 대상이 10배가 될 때 SQL 수가 10배로 늘지 않는지 | 운영 기준이 없어, 대상 수에 비례하는지를 봐요 |

## 실행 순서 (모두 `backend/`에서)

```bash
# 1. 인프라·모니터링, 그리고 cash만 띄운다 (B-3은 PG 지연 300ms, B-4는 배치를 매분)
docker compose --profile monitoring up -d
source loadtest/scenario-b/run-env.sh
CASH_CLIENTS_PG_FAKE_CANCEL_DELAY=300ms CASH_WITHDRAWAL_DEADLINE_NOTICE_CRON='0 * * * * *' ./gradlew :cash-service:bootRun

# 2. 테스트 데이터 (다른 데이터는 건드리지 않고 900001번대만)
./loadtest/scenario-b/db.sh < loadtest/scenario-b/sql/99-cleanup.sql
./loadtest/scenario-b/db.sh < loadtest/scenario-b/sql/01-seed.sql

# 3. 시나리오마다 RUN을 바꿔 돌리고 바로 확인한다
k6 run -e RUN=1 loadtest/scenario-b/b0-integrity.js && ./loadtest/scenario-b/verify.sh 1
k6 run -e RUN=2 loadtest/scenario-b/b1-payment-ramp.js && ./loadtest/scenario-b/verify.sh 2
k6 run -e RUN=3 loadtest/scenario-b/b2-payment-with-payout.js && ./loadtest/scenario-b/verify.sh 3
./loadtest/scenario-b/seed-withdrawal.sh && k6 run loadtest/scenario-b/b3-withdrawal.js
./loadtest/scenario-b/b4-notice.sh 100   # 1000, 10000도

# 4. 끝나면 정리한다 (홀딩·시스템 지갑 잔액도 테스트 전으로 돌아감)
./loadtest/scenario-b/db.sh < loadtest/scenario-b/sql/99-cleanup.sql
```

- 정리한 결과는 `docs/measurements/`에 남겨요(첫 결과: `docs/measurements/2026-11-16-예치금-부하.md`). 실행 조건(맥, Docker 메모리, 로그·추적 설정)과 Grafana 「예치금(cash)」 대시보드 숫자를 같이 적어요.
- k6 요약(`--summary-export`)·확인 SQL 출력·맥 기록 같은 원본은 `results/`에 둬요.
- 그래프는 `graph.py`로 만들어요(matplotlib 필요, 사용법은 파일 맨 위). 시간별 그래프는 로컬 Prometheus에서 읽어서 측정 시작 시각을 적어 둬야 해요.
- cash 내부 API 키 검사가 들어오면 `k6 run -e INTERNAL_API_KEY=...`로 `.env`의 값을 넘겨요.
