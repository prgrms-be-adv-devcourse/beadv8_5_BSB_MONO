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
- **판매자**: 부하 계정 `loadtest1000`을 `crewrun_product.product_seller`에 승인 상태로 넣어 뒀다(id 900001, 2026-11-19). product 쪽 판매자 표만 넣은 것이라 member-service에는 판매자로 없다 → 이 계정의 대회는 정산 대상이 되면 안 된다(대회일을 120일 뒤로 둔다).
- **대회**: 실행마다 `prepare-race.js`가 API로 새로 만든다(`POST /api/v1/product/races` → `POST /races/{id}/open` 판매 바로 시작). DB로 날짜를 당기던 예전 방식은 쓰지 않는다.

## 3. 토큰
nginx 1/s 제한 + 토큰 30분 수명 → **단계마다 쓸 인원만 로그인하고 바로 실행**. 1,000명 한 번에 로그인하면 18분이라 뒤 단계 토큰이 만료된다.
```bash
# 미니 PC prod-run 에서. 단계별 COUNT: s1=300, s2=660, s3=1000, buystress=900
LOADTEST_PASSWORD='<비번>' COUNT=300 k6 run login-prod.js   # → .accounts.json (throttle, s1이면 ~5.5분)
```

## 4. 실행 (단계 램프)
```powershell
# 미니 PC prod-run 에서. 단계마다 새 대회 2개를 만들고 바로 funnel을 돌린다
powershell -File run-stage.ps1 -Stage s1 -Tag 1119-pm-main-68f0336        # 조회 100/s + 구매 2/s
powershell -File run-stage.ps1 -Stage s2 -Tag 1119-pm-main-68f0336        # 조회 200/s
powershell -File run-stage.ps1 -Stage s3 -Tag 1119-pm-main-68f0336        # 조회 330/s (여기서 붕괴)
powershell -File run-stage.ps1 -Stage buystress -Tag 1119-pm-main-68f0336 # 쓰기 한계: 구매 15/s
```
```powershell
# 실제 흐름(rush.js): 같은 사람이 줄 서서 구매까지. 단계 = 몰리는 인원
powershell -File run-stage.ps1 -Stage r300 -Tag <날짜-커밋> -Script rush.js
powershell -File run-stage.ps1 -Stage r600 -Tag <날짜-커밋> -Script rush.js
powershell -File run-stage.ps1 -Stage r990 -Tag <날짜-커밋> -Script rush.js
```
- 단계 사이에는 60~90초 쉰다. SSH가 끊겨도 k6는 계속 도니, `<stage>.window.txt`가 생겼는지 보고 다음 단계로 간다.
- `.ps1`은 **UTF-8 BOM**으로 저장한다. BOM이 없으면 Windows PowerShell이 한글 주석을 잘못 읽어 다음 줄(`param`)을 삼킨다(2026-11-19: 50명 로그인이 1,000명으로 돌았다).
- 결과는 `runs\<Tag>\`에 남는다: `<stage>.json`(요약), `<stage>-raw.csv`(요청별 원시), `<stage>.log`, `<stage>.window.txt`(시작·끝 UTC, 대회 ID), `<stage>.race.json`.
- **Tag에 날짜와 main 커밋을 넣고**, 서비스별 이미지 태그를 `MANIFEST.md`에 적는다(재현용).
- 자동멈춤: 실패율 5%(30초), 쓰기 p99 5초 2분 지속.
- 대기열 리셋 불필요(키 자동 정리). 재입장 409는 정상 처리, **신청 409는 실패**로 센다(새 대회라 중복이 나오면 안 된다).
- 끝나면 `신청 201`·`주문`·`결제` 체크 건수가 0이 아닌지 확인한다.

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
- **구매자는 대회당 1번만 신청**(이후 409 중복). 예전엔 409를 정상 거절로 세서, 대회를 재사용한 실행은 주문·결제 0건인데도 통과로 보였다 → 실행마다 새 대회를 만든다.
- **CPU가 벽이라 hikari 풀은 안 참**(CPU가 구매 throughput을 묶음).

## 7. 결과 요약
| 단계 | 조회 RPS | CPU | 대기열 p95 | 판정 |
|---|---|---|---|---|
| s1 | 100 | 77% | 198ms | 안정 |
| s2 | 200 | 91% | 519ms | 열화·드롭 |
| s3 | 330 | 99.6% | 5.8s | 붕괴(3,601 드롭) |

(조회 숫자는 RPS = 대기열 조회 요청/초. 예매 완결은 별도 축으로 ~20 TPS = 거래/초 수준.) 병목 = 노드 2코어 CPU. 조회(nginx→gateway JWT 검증→queue)가 CPU 독식, gateway가 단일 최대(0.56코어). 개선 1순위 = 게이트웨이 JWT 검증 비용 절감, 다음 코어 증설. 테스트 후 전 파드 Running·OOM 0.

## 8. 로그인을 빨리 끝내기 (부하 PC만 /auth 제한에서 빼기)
nginx가 `/auth`를 IP당 초당 1건으로 막아 1,000명 로그인에 20분이 걸리고, 그동안 먼저 받은 토큰(30분)이 닳는다. 부하를 돌리는 동안만 부하 PC IP를 제한에서 뺀다(11/19 파티 부하에서 1,600명 약 6분).
```bash
ssh team05 'bash -s' <부하PC 공인IP> < ops/nginx-open.sh   # 열기 (nginx 몇 초 재시작, 열 때마다 백업을 새로 뜬다)
powershell -File run-login.ps1 -Count 1000 -Interval 0.2    # 미니 PC에서
# ... 부하 ...
ssh team05 'bash -s' < ops/nginx-close.sh                   # 닫기 (실패해도 꼭, 되돌린 뒤 백업을 지운다)
```
- ConfigMap의 파일(키)을 모두 꺼내 `upstreams.conf`만 고쳐 다시 만든다. 파일 이름을 적어 두지 않아서, `nginx.conf`(#670 연결 한도)처럼 키가 늘어도 빠지지 않는다.
- 그 IP에서 오는 로그인만 제한이 풀린다. 다른 사용자는 그대로다.
- 부하 도중 nginx를 다시 배포하면 설정이 원래대로 돌아간다.

## 9. k6 지표를 Grafana에서 보기
`run-stage.ps1`은 미니 PC의 `prod-run\.prom-rw-password.txt`가 있으면 k6 지표를 수집 서버(Prometheus)로도 보낸다. 없으면 보내지 않고 파일로만 남긴다.
- 보내는 곳: `https://prom.bukang-devops.duckdns.org/api/v1/write` (team05 수집기와 같은 주소, 사용자 `crewrun`). 비밀번호는 team05의 Secret `monitoring-remote-write`와 같은 값이고, 파일은 커밋하지 않는다.
- Grafana `k6 Prometheus` 대시보드에서 **testid**로 실행을 고른다. 값은 `<Tag>-<Stage>`(예: `1120-main-abc1234-r990`)이고 `<stage>.window.txt`의 `grafana_testid`에도 남는다.
- 여러 사람이 같은 시간에 부하를 넣어도 testid가 다르면 따로 보인다.
- 부하를 넣은 시각(시작·끝 UTC)은 `<stage>.window.txt`에 남는다. 같은 구간을 Grafana에서 다시 볼 때 쓴다.
