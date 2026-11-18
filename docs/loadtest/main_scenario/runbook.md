# 예매 몰림 funnel 운영 부하 테스트 — 재현 러닝북

team05(운영 dev, 2 vCPU·7.6GB, k3s)에서 예매 오픈 몰림(대기열→주문→결제)을 부하 테스트하는 법. 2026-11-18 실측 기준.
**결과 한 줄:** 병목은 2코어 CPU(조회 경로), 안정 조회 **~100 RPS**(요청/초)·330 RPS 붕괴, 예매 완결 **~20 TPS**(거래/초) 수준. DB풀·메모리 아님. (상세: `results.md`)

## 0. 준비물
- **k6**(발생기, team05 밖에서 실행 — 미니 PC나 개인 PC). team05 위에서 돌리지 않는다.
- **team05 SSH**(`team05` 스킬, `sudo kubectl`).
- **Grafana**: `https://grafana.bukang-devops.duckdns.org`(admin, 비번은 팀에 문의) — Prometheus가 전 서비스·노드 수집.
- 레포 스크립트: `backend/loadtest/scenario-a/`(funnel 로직), `backend/loadtest/accounts/login.js`(throttle 로그인), `lib/common.js`.
  운영용 변형(가드 완화·단계·자동멈춤)은 미니 PC `C:\Users\user\loadtest\prod-run\`(`funnel.js`, `login-prod.js`). 측정 스크립트 `docs-local/ops/promsnap.py`.

## 1. DB·Redis 접근 (team05 경유)
데이터 3종(MySQL·Redis·Kafka)은 별도 노드 **10.0.2.222**. team05엔 클라이언트 없지만 `mysql:8.4`·`redis:8.2` 도커 이미지가 있다. 자격증명은 파드 env에서만 읽는다(출력 금지).
```bash
# team05에서. MYSQL_USER/MYSQL_PASS는 cash 파드 env
E=$(sudo kubectl -n crewrun exec deploy/bukang-cash-service -- printenv)
U=$(echo "$E"|sed -n 's/^MYSQL_USER=//p'); export MYSQL_PWD=$(echo "$E"|sed -n 's/^MYSQL_PASS=//p')
sudo -E docker run --rm -e MYSQL_PWD mysql:8.4 mysql -h 10.0.2.222 -u"$U" -N -e "SELECT 1;"
# Redis는 비번 없음: redis:8.2 redis-cli -h 10.0.2.222 ... (또는 team05 bash /dev/tcp)
```

## 2. 운영 시드 (prod 프로필이라 API만으론 안 됨)
운영은 `prod` 프로필 → **가짜키 예치금 충전·실본인인증이 API로 막힘.** 그래서:
- **회원**: Redis에 본인인증 결과 주입 → `POST /api/v1/auth/join`. 약관은 **SERVICE·PRIVACY·AGE_OVER_14 3종**(AGE_OVER_14 빠지면 400). nginx가 `/auth`를 IP당 1/s로 막으니 1.1s 간격 throttle.
- **비밀번호**: DB로 통일. `UPDATE crewrun_member.member_member SET password_hash='<BCrypt $2a$10 해시>' WHERE email LIKE 'loadtest%@crewrun.test';` (해시는 team05 `python3 -c "import bcrypt;..."`).
- **예치금**: 충전 API 막혀 DB 직접. `UPDATE crewrun_cash.cash_wallet SET balance=1000000 WHERE holder_id IN (...)`. (가입 시 지갑 자동 생성됨.)
- **판매자·대회**: `product_seller`에 판매자 등록 → `race`의 `sale_open_date=CURDATE(), individual_open_at=NOW()-1m`로 날짜만 당기고 **sale_status는 SCHEDULED로 둔다**(product 5분 스케줄러가 ON_SALE로 바꾸며 queue에 통지). 코스 `race_course`의 `individual_quota` 크게·`kit_included=0`.

## 3. 토큰
nginx 1/s 제한 + 토큰 30분 수명 → **단계마다 쓸 인원만 로그인하고 바로 실행**. 1,000명 한 번에 로그인하면 18분이라 뒤 단계 토큰이 만료된다.
```bash
# 미니 PC prod-run 에서. 단계별 COUNT: s1=300, s2=660, s3=1000, buystress=900
LOADTEST_PASSWORD='<비번>' COUNT=300 k6 run login-prod.js   # → .accounts.json (throttle, s1이면 ~5.5분)
```

## 4. 실행 (단계 램프)
```bash
k6 run -e STAGE=s1 --summary-export s1.json funnel.js   # 조회 100/s + 구매 2/s
k6 run -e STAGE=s2 --summary-export s2.json funnel.js   # 조회 200/s
k6 run -e STAGE=s3 --summary-export s3.json funnel.js   # 조회 330/s (여기서 붕괴)
# 쓰기 한계: 구매 15/s. 구매자(1~900)가 이미 샀으면 코스 바꿔 신규로:
k6 run -e STAGE=buystress -e BUY_RACE=9 -e BUY_COURSE=17 --summary-export buy.json funnel.js
```
- 단계별 **P95/P99는 `--summary-export`로 꼭 남긴다**(안 남기면 기록 없음).
- 자동멈춤: 실패율 5%(30초), 쓰기 p99 5초 2분 지속.
- 대기열 리셋 불필요(키 자동 정리). 재입장·재주문 409는 스크립트가 정상 처리.

## 5. 측정 (서버 지표)
Grafana에서 JVM·Node·MySQL 대시보드. 또는 Prometheus 직접 쿼리(병목 1순위 `hikaricp_connections_pending`):
```bash
GRAFANA_PW='<비번>' python docs-local/ops/promsnap.py 15 40 > results/<stage>-metrics.txt
# 노드 CPU·여유·스왑 + 서비스별 hikari(대기/활성)·힙%·GC/s. 또는 max_over_time(...[Nm])로 구간 피크.
```

## 6. 함정 (실측에서 걸린 것)
- **k6 0.54엔 `crypto.randomUUID` 전역 없음** → Idempotency-Key를 `buy-${memberNo}-${Date.now()}` 식으로.
- **k6 arrival-rate는 `maxVUs` 안 주면 preAllocatedVUs에 갇힘** → 목표 부하 미달(측정 무효). poll/buy에 maxVUs 넉넉히.
- **PowerShell이 k6 경고를 에러로** 취급 → ps1에 `$ErrorActionPreference='Continue'`.
- **구매자는 1회용**(코스당 1회, 이후 409). 높은 구매율 길게 못 끔 → 신규 코스로 바꾸거나 계정 늘림.
- **CPU가 벽이라 hikari 풀은 안 참**(CPU가 구매 throughput을 묶음).

## 7. 결과 요약
| 단계 | 조회 RPS | CPU | 대기열 p95 | 판정 |
|---|---|---|---|---|
| s1 | 100 | 77% | 198ms | 안정 |
| s2 | 200 | 91% | 519ms | 열화·드롭 |
| s3 | 330 | 99.6% | 5.8s | 붕괴(3,601 드롭) |

(조회 숫자는 RPS = 대기열 조회 요청/초. 예매 완결은 별도 축으로 ~20 TPS = 거래/초 수준.) 병목 = 노드 2코어 CPU. 조회(nginx→gateway JWT 검증→queue)가 CPU 독식, gateway가 단일 최대(0.56코어). 개선 1순위 = 게이트웨이 JWT 검증 비용 절감, 다음 코어 증설. 테스트 후 전 파드 Running·OOM 0.
