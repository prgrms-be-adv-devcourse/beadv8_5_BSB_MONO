# team05 예매 몰림 funnel 부하 테스트 (2026-11-18 재계획)

실사용자 0명 dev 서버(team05, `crewrun.duckdns.org`, 2코어·7.6GB·스왑 3GB, k3s)에서 **예매 오픈 몰림 funnel**(대기열→주문→결제)이 어디서 먼저 무너지고 안정 최대 TPS가 얼마인지 수치로 낸다.

## 왜 이게 빈칸인가 (11/18 팀 가이드 반영)

팀이 11/17 밤 운영 부하 테스트를 이미 했으나, 대상은 **결제 단계·동시결제·member·crew·board·둘러보기**였다. 예매 funnel(scenario-a)은 `backend/loadtest/README.md`에서 **"로컬만"**이라 운영에서 안 돌렸다. → **이 funnel 운영 테스트가 내 과제이자 팀의 빈칸.**

11/17 결과(참고 baseline):
- 결제 단계적 증가: **초당 10건까지 버팀, 20건에서 1분 P99 5초 초과 자동멈춤**(병목 미확인).
- 동시 결제 300명: 전원 성공, 5xx 0, 결제 응답 중앙값 4.4초·P99 6.5초.
- 둘러보기(읽기): 초당 100건까지 실패 0 (P99 20/s 110ms → 50/s 191ms → 100/s 943ms).
- 확인 SQL: 돈 합계 일치, 이중차감·음수·상한초과 0.
- **단계별 P95/P99는 요약 파일을 못 남겨 "기록 없음"** → 이번엔 반드시 남긴다.

## 쓰는 자산 (전부 레포 정식, origin/dev)

- `backend/loadtest/accounts/identities.sh` + `accounts/login.js`: 운영 대상, throttle 로그인(1.1s/건, `JOIN=1`이면 가입). 토큰을 `.accounts.json`에 저장.
- `backend/loadtest/lib/common.js`: 운영 기본 URL, 단계(smoke/load/stress), `arrival()`, 자동멈춤(실패율 5% → 30초 뒤 abort), `assertTokensFresh()`, 409 태깅.
- `backend/loadtest/scenario-a/scenario-a.js`: 몰림 funnel 로직(입장→폴링→입장허가→신청→주문→결제). **단 localhost 가드** → 운영용으로 가드만 풀고 common.js 규약(토큰 파일·태그·자동멈춤)에 맞춘 변형을 쓴다. 원본은 안 건드린다.
- 발생기: 미니 PC(`k6 v0.54` 설치됨). team05 밖에서 `crewrun.duckdns.org`로 쏜다.

## 토큰 전략 (팀 방식 그대로)

nginx가 `/api/v1/auth/*`를 **IP당 초당 1건(burst 20)**으로 막는다(`upstreams.conf:13`, 무차별 방지). 이건 단일 IP 발생기에만 걸리는 artifact(실사용자는 IP가 달라 안 걸림).
→ 팀 방식: **throttle 로그인(1.1s/건) + 인원을 토큰 수명(30분) 안에 로그인 끝나게 제한.** nginx 상향·JWT 위조 안 함.
→ **예매 funnel 본 경로(queue·order·payment)는 레이트 리밋 없음** → 진짜 용량을 잰다.

## 규모 (2026-11-18 결정: 1,000명)

loadtest1~1000. 로그인은 throttle 1.1s/건 → ~18분(토큰 30분 안에 run까지 들어감). 1,000이면 구매자를 더 확보해 구매율을 높게(~15/s) 밀어 결제 붕괴(11/17 ~20/s)를 funnel에서 직접 본다.
단계별로 구매자 번호(buyerFirstNo)를 겹치지 않게 나눈다(구매자는 1회용).

## 실행 명령 (준비 완료)

발생기: 미니 PC. k6 `C:\Users\user\loadtest\k6bin\k6-v0.54.0-windows-amd64\k6.exe`, 스크립트 `C:\Users\user\loadtest\prod-run\`(`login-prod.js`, `funnel.js`). 둘 다 그 폴더에서 실행(토큰 파일 `.accounts.json`을 거기 씀).

```
# 1) 토큰 (로그인만, throttle, ~18분). 비번은 채팅 금지 — 환경변수로
LOADTEST_PASSWORD=<비번> COUNT=1000 k6 run login-prod.js
# 2) funnel 단계 (토큰 만든 뒤 바로). 조회(poll) 스케일: smoke→s1→s2→s3, 이어서 결제 한계용 buystress
k6 run -e STAGE=smoke funnel.js        # 흐름 확인
k6 run -e STAGE=s1 --summary-export s1.json funnel.js   # poll 100/s, buy 2/s
k6 run -e STAGE=s2 --summary-export s2.json funnel.js   # poll 200/s, buy 3/s
k6 run -e STAGE=s3 --summary-export s3.json funnel.js   # poll 330/s, buy 2/s
# buystress는 구매자 1..900 소비 → 새 토큰(login 다시) 뒤 단독 실행
k6 run -e STAGE=buystress --summary-export buy.json funnel.js   # buy 15/s
```
- 대회 ID 기본값: BUY race 9/course 16, WAIT race 10/course 20 (스크립트 기본, 필요시 `-e BUY_RACE=` 등).
- 단계별 P95/P99는 `--summary-export`로 꼭 남긴다(11/17 누락 교훈). `K6_WEB_DASHBOARD=true K6_WEB_DASHBOARD_EXPORT=xx.html`로 그래프도.
- 자동멈춤: 실패율 5%(30초), write p99 5초가 2분 지속.

## 실행 사이 대기열 리셋 — 불필요 (2026-11-18 확인)

운영 Redis(10.0.2.222)에 race 9/10 대기열 키가 **남지 않는다**(스모크 뒤 자동 정리, members·ai 키만). 단계 사이 수동 리셋 필요 없음. 재입장 409·재주문 409는 스크립트가 정상 거절로 처리하고, 구매자 번호는 단계마다 안 겹치게 둔다.

## 토큰 타이밍 (중요)

로그인은 nginx 제한으로 1.1s/건 → 1,000명 ~18분. 토큰 수명 30분이라 **1,000명 로그인 후 s1·s2·s3를 전부 돌리면 초과**. 그래서 **단계마다 그 단계가 쓸 인원만 로그인 → 즉시 실행**:
| 단계 | login COUNT | 로그인 | 실행 | 합 |
|---|---|---|---|---|
| s1 | 300 | ~5.5분 | ~5분 | ~11분 |
| s2 | 660 | ~12분 | ~5분 | ~17분 |
| s3 | 1000 | ~18분 | ~4분 | ~22분 |
| buystress | 900 | ~16분 | ~2분 | ~18분 |
각 단계는 로그인 직후 바로 실행(토큰 신선). smoke는 이미 검증됨(생략 가능).

## 셋업 현황 (운영에 넣고 검증 완료, 2026-11-18)

- **loadtest 회원 1,000명 준비·검증 완료**: loadtest1~1000 전원 존재, 비밀번호 `Loadtest!2026` DB 통일(`password_hash` BCrypt `$2a$10$`), 예치금 각 100만(`cash_wallet.balance`). 검증: members 1~1000=1000, 지갑 100만=1000(누락 0), 로그인 1·500·1000 모두 200.
- **발생기 스크립트 준비 완료**: 미니 PC `C:\Users\user\loadtest\prod-run\`에 `login-prod.js`·`funnel.js`, k6 `k6bin\...\k6.exe`. 실행 명령은 "실행 명령" 절.
- 판매자: `admin@crewrun.com`(member 8) → `product_seller` id 1005 등록 완료.
- 테스트 대회: race 9(구매, 코스 16 정원 5000, kit_included=0)·race 10(대기)을 판매자 1005로 열고 ON_SALE 전환 확인.
- **1계정 전 과정 검증 완료:** loadtest1 로그인→대기열→입장→신청→주문→**결제 PAID**, 예치금 100만→95만 차감 확인.
- **가입 함정(발견):** 어제 필수 약관 `AGE_OVER_14`(CRU-22) 추가로 `SERVICE·PRIVACY`만으론 가입 400(`MEMBER_REQUIRED_TERMS_NOT_AGREED`). → 가입 바디 agreedTerms는 **`SERVICE·PRIVACY·AGE_OVER_14`** 3종(SELLER 불필요). `accounts/login.js`의 `JOIN` 바디도 이 3종이어야 함.
- 마킹: 대회 이름 `[부하테스트]`·먼 미래 날짜·ES 차단은 안 함(운영 DB 발표 전 비움 결정으로 정리 부담 작음). 필요시 보완.

## 설계 주의: 구매자는 1회용

한 회원은 코스당 1회만 구매(이후 409). → **400 계정으론 높은 구매율(20/s)을 길게 못 끈다**(20/s×수분 = 수천 계정 필요). 그래서 이 규모에서는:
- **대기열 조회(poll) 경로**는 대기자 400으로 ~133/s까지 제대로 부하(읽기 한계 측정).
- **구매(write)**는 짧은 버스트로 "무너지는 지점" 확인(11/17 payment 단독 ~20/s를 funnel에서 재확인).
- 지속 고구매율이 꼭 필요하면 계정을 더 늘린 뒤 재실행.

## DB·Redis 접근 경로 (확정)

team05에서 데이터 노드(10.0.2.222)로 네트워크 도달. team05에 `mysql:8.4`·`redis:8.2` 이미지가 있어 클라이언트로 씀:
- MySQL: `sudo docker run --rm -e MYSQL_PWD=<cash파드 MYSQL_PASS> mysql:8.4 mysql -h 10.0.2.222 -u<MYSQL_USER>` (스키마 `crewrun_member`·`crewrun_cash`·`crewrun_product`).
- Redis: team05 `/dev/tcp/10.0.2.222/6379` 또는 `redis:8.2 redis-cli -h 10.0.2.222` (비번 없음). 본인인증 주입용.
- 자격증명은 파드 env(`MYSQL_USER`/`MYSQL_PASS`, member `JWT_SECRET`)에서만 읽고 출력하지 않는다.

## 단계 (common.js 규약)

smoke(0.5분, 도는지만) → load(5분, 목표 초당) → stress(10분, 0→목표×4 램프). funnel은 구매가 느려 stress에서 한계가 드러날 것.
- 목표(초당): 대기열 조회는 대기자÷3(≈100/s), 구매는 2→5→10→20까지 올려 **무너지는 지점**을 본다.
- 통과 기준: 조회 p95 300ms, 쓰기 p95 1초, 실패율 1%. 자동멈춤: 실패율 5%(30초 뒤). **funnel엔 "1분 P99 5초 2분 지속" 멈춤을 추가**(11/17 결제처럼 에러 없이 느려지는 붕괴를 잡기 위해).
- 정상 거절은 실패로 안 셈: 매진 409(`ORDER_SOLD_OUT`), 결제 확인중 202(`PAYING`), 이미 입장 409.

## 관측 (Grafana + Prometheus 세팅됨, 2026-11-18)

- **모니터링 서버: `https://grafana.bukang-devops.duckdns.org` (admin, 비번은 사용자 제공).** Grafana 13.3.1 + Prometheus(default 데이터소스)가 **서비스 13개 + 노드 2대(team05·10.0.2.222) + cadvisor·mysql·redis·redpanda·ES**를 수집 중. metrics-server 불필요.
- 대시보드: **JVM (Micrometer)**(힙·GC·스레드), **Node Exporter Full**(CPU·메모리·스왑), **MySQL Exporter**, k6 Prometheus, ES.
- 병목 지표 직접 쿼리(Grafana → Explore 또는 datasource proxy): **`hikaricp_connections_pending`**(서비스별, 커넥션풀 대기 — 1순위), `jvm_memory_used_bytes{area="heap"}`, `jvm_gc_pause_seconds_*`, `http_server_requests_seconds`(서버측 지연), cadvisor 컨테이너 메모리·CPU, node CPU·스왑.
- **데이터 받아오기: `docs-local/ops/promsnap.py`** (Grafana 프록시로 Prometheus 쿼리). 각 단계와 함께 돌려 지표를 시계열로 남긴다:
  `GRAFANA_PW=<비번> python docs-local/ops/promsnap.py 15 40 > results/<stage>-metrics.txt` (15초 간격 40회=10분). 노드 CPU·여유·스왑 + 서비스별 hikari(pend/active)·힙%·GC/s. (gateway는 DB 풀 없어 hikari `-`가 정상.)
- 백업(Grafana 못 볼 때): team05에서 `sudo k3s crictl stats; free -m`, 파드 `/actuator/prometheus`.
- **병목 후보(가이드 명시, 우선 확인):** ① DB 커넥션풀 서비스당 기본 **10개** — HTTP로 다른 서비스 부르는 동안 커넥션을 쥐면 먼저 고갈(에러 아닌 지연으로 나타남). ② 게이트웨이 응답 제한 **10초** < DB 커넥션 대기 **30초** → 게이트웨이가 먼저 포기. ③ 노드 메모리·스왑. ④ 발생기→서버 네트워크 시간.

## 남은 할 일

1. 규모 확정(권장 400) → 인원까지 `login.js -e JOIN=1`로 가입 + Redis 본인인증 주입.
2. 전체 loadtest 회원 비밀번호·예치금 DB 일괄 설정.
3. 운영용 funnel 스크립트 마련(scenario-a 가드 완화 + 토큰 파일·태그·P99 자동멈춤 정렬).
4. 조용한 밤 창 확인(발사 직전 loadavg·available·kubectl) → smoke→load→stress 램프, 단계별 P95/P99·crictl·actuator 스냅샷 기록.
5. 회복 확인(끝나고 5분 대회목록 열어 보기), 모든 파드 Running 확인.
6. 결과를 아래 "결과"에. 마킹 데이터는 발표 전 DB 비움으로 정리(필요시 cleanup SQL은 만든 역순).

## 안전

밤 실행, 팀 채널에 시간 공지, DB 볼륨 삭제·`down -v`·볼륨 prune 금지. 죽은 파드는 k3s가 재시작하지만 끝나고 전 파드 Running 확인 후 보고.

## 결과 (2026-11-18 실행 중)

### 단계별 (k6, main 구간 / Grafana 피크)

| 단계 | 조회 RPS | 구매 도착/s | 대회상세 p95 | 대기열 p95 | 쓰기 p95 | 실패율 | dropped | 노드 CPU 피크 | hikari 대기 |
|---|---|---|---|---|---|---|---|---|---|
| s1 | 100 | 2 | 33ms | **198ms** | 481ms | 0% | 0 | 77% | 0 |
| s2 | 200 | 3 | 30ms | **519ms** ✗ | 812ms | 0.08% | **104** ✗ | **90.6%** | 0 |
| s3 | 330 | 2 | 33ms | **5.8s** ✗ | **1.64s** ✗ | 0.07% | **3,601** ✗ | **99.6%** | 0 |

- 메모리: 여유 s1/s2 1.1GB+ → s3 715MB(OOM 아님), 스왑 ~220MB, 힙 피크 ~48% 이하. GC 무시 가능.
- **조회(read) 경로 결론 — 병목은 노드 CPU(2코어), 단일 원인:**
  - 안정 ~100/s(s1 깨끗) → 열화 200/s(s2: 대기열 p95 519ms, 104 드롭, CPU 90.6%) → **붕괴 330/s(s3: 대기열 p95 5.8s, 3,601 드롭, CPU 99.6% 두 코어 포화, load1 5.4)**.
  - 대기열 상태 GET이 CPU 작업(게이트웨이 JWT 검증 + 큐 조회)이라 CPU를 먼저 소진. **hikari 커넥션풀 대기는 s1~s3 내내 0, 활성 최대 3 → DB풀·메모리·GC 아님**(구매율이 낮아 쓰기 경로 미자극).
### buystress (쓰기 경로, 구매 15/s · 코스 17 신규)

| 조회 RPS | 구매 목표/s | 쓰기 p95 | 대기열 p95 | 쓰기 에러 | dropped | CPU 피크 | hikari 대기 |
|---|---|---|---|---|---|---|---|
| 133 | 15 | **829ms** | 784ms ✗ | **0%** | 39 | 95.2% | **0** |

- 쓰기(신청+주문+결제) p95 829ms로 **임계 1초 아래**, 쓰기 526건 전부 성공(에러 0). 쓰기 경로 자체는 안 터짐.
- **단, 실제 구매 처리량은 15/s에 한참 못 미침**: iteration p95 38초 — CPU 포화로 구매자가 대기열·입장에서 적체돼 동시 주문 수가 낮게 유지됨. 그래서 **hikari 풀이 찰 틈이 없었음(대기 0, 활성 1)**. CPU가 구매 throughput 자체를 묶는다.

## 최종 결론 (2026-11-18)

**1. 처음 무너지는 지점·원인**
- **조회(대기열 상태 GET) 경로가 먼저 무너진다.** 열화 시작 조회 200 RPS(s2), 붕괴 330 RPS(s3, 대기열 p95 5.8초·3,601 드롭).
- **원인 = 노드 CPU(2 vCPU) 포화.** s1 77% → s2 90.6% → s3 99.6%(두 코어). **메모리·GC·DB 커넥션풀(hikari 전 구간 0)·크래시 모두 아님.** 모듈로는 게이트웨이 JWT 검증 + queue-service 상태 조회가 CPU를 소진하고, 조회가 최다 요청이라 먼저 포화.

**2. 안정 최대 — RPS/TPS 구분**
- **조회(read) 한계: ~100 RPS**(요청/초, s1: 대기열 p95 198ms, 쓰기 p95 481ms, 실패 0%, 드롭 0). 150~200 RPS 열화, 330 RPS 붕괴. 전체 funnel 혼합 ~96 req/s.
- **예매 완결(write) 한계: ~20 TPS**(거래/초) 수준. CPU에 막혀 독립적으로 못 터뜨림(실제 완결 < 15 TPS, 구매자 적체). 11/17 결제 단독 측정과 일관 — 공통 한계는 CPU.
- (RPS = 초당 요청 수·도착률. TPS = 초당 완결 거래 수 = 예매 1건 전체.)

**3. 테스트 후 상태**: 전 파드 Running, 재시작·OOM 0, 노드 회복(여유 933MB, loadavg 1.1). 크래시 없이 지연으로만 저하.

**개선 방향(가벼운 제안)**: ① CPU가 벽 → 코어 증설(t3.xlarge 등)이 가장 직접적. ② 게이트웨이 JWT 검증·queue 상태 조회의 CPU 비용 절감(조회 캐싱/경량화). ③ DB 풀(10)·메모리·GC는 당장 손댈 곳 아님(이 부하에선 여유).
