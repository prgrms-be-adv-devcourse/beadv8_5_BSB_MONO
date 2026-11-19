# real-flow: 인기 대회 예매 오픈 몰림 (화면과 같은 요청)

인기 대회 예매 오픈 순간에 사람들이 몰릴 때, **화면을 누를 때 나가는 요청을 그대로** 보내 서버가 몇 명까지 버티는지 잰다 (CRU-84).
핵심 API만 부르는 `docs/loadtest/main_scenario`와 달리 앱 열기(재발급·내 정보·홈), 화면 이동(`_rsc`), 이미지, 알림, 결제 뒤 다시 조회까지 보낸다.

- 요청 순서: 구글 시트 「크루런 시나리오 URL 정리_google」의 **「부하 시나리오」 탭** (로컬 화면을 눌러 개발자 도구로 확인)
- 진행 방식과 숫자의 근거: 「부하 시나리오 작성 팁」 문서 (2026-11-19). 아래 절 번호는 그 문서의 절이다

## 무엇을 재는가

| 숫자 | 뜻 | 실행 |
|---|---|---|
| **N** | 결제 503이 한 번도 안 나고 응답 기준을 통과하는 최대 인원 | N·M 실행: 대회 한 쌍, 100 → 300 → 600 → 1,000명 |
| **M** | 503은 나도 다시 시도해 모두 결제가 끝나는 최대 인원 | 같은 실행 |
| **X** | 초당 처리하는 예매 건수 (결제까지 끝난 예매) | X 실행: 대기열 설정은 그대로, 같은 시각에 열리는 대회 쌍을 1 → 2 → 3 |

정상 기준 (8절)
- 정합성 불일치는 **항상 0건** (`check.sh`)
- N 구간: 결제 503(`pay_busy_first`) 0건, 응답 p95 조회 300ms·대기열 200ms·쓰기 1초, 실패율 1% 미만
- M 구간: 다시 시도해도 결제 못 한 사람(`booking_failed_final`) 0명
- 매진(주문 409)은 실패가 아니다. `booking_sold_out`으로 따로 센다

## 한 구간에서 일어나는 일

대회 한 쌍을 같은 시각(오픈 시각 T)에 연다 (9절). 두 대회 모두 5K·10K·하프·풀 4종목.

| 사람 | 몇 명 (N 기준) | 하는 일 |
|---|---|---|
| 개인 예매 | N × 80% (대회 B) | 앱 열기 → 대회 목록 → 상세 → 「신청하기」 → 대기열 → 신청서(30초) → 주문 → 결제(5초) |
| 크루 파티장 | N × 20% (대회 A, 파티 5명) | 앱 열기 → 목록 → 상세 → 크루 탭 → 크루 홈 → 파티 → 「5명 파티 신청하기」 → 파티 대기열 → 파티 신청 |
| 파티원 결제 (배경) | 정원 안에 든 파티의 파티원 × 25% | T + 1~5분에 앱 열기 → 알림 → 결제 알림 → 파티 → 내 몫 주문 → 결제 |
| 보기만 하는 사람 | N × 10% (N에 안 들어감) | 앱 열기 → 대회 목록 → 상세 → 나감 |

- 도착 (11절): 90%가 T 1분 전부터 들어와 기다리다 T부터 5초 안에 누른다. 10%는 T 뒤 1분 동안 들어온다
- 앱 열기 (15절): 모두 재발급 + 내 정보 + 홈. 그중 10%는 쿠키가 없어 로그인 화면에서 로그인한다. 모든 요청은 401이면 재발급 뒤 다시 보낸다
- 종목 (10-2절): 10K 40%, 하프 30%, 5K 20%, 풀 10%. 정원은 몰리는 인원의 70% (14절)
- 결제 503 (8-3절): 실패 화면 3초 → 「다시 결제하기」(주문 다시 조회) → 2초 → 다시 누름, 최대 5번
- 다음 구간은 앞 구간 오픈 4분 뒤(`STAGE_GAP`)에 연다. 앞 구간 파티원 결제는 뒤에서 겹쳐 돈다 (12절)

N·M 실행의 인원 (12-2절)

| N | 개인 예매 | 개인 정원 | 파티 | 크루 정원 | 파티원 결제 | 보기만 |
|---|---|---|---|---|---|---|
| 100 | 80 | 56 | 20 | 14파티·70자리 | 약 18명 | 10 |
| 300 | 240 | 168 | 60 | 42파티·210자리 | 약 53명 | 30 |
| 600 | 480 | 336 | 120 | 84파티·420자리 | 약 105명 | 60 |
| 1,000 | 800 | 560 | 200 | 140파티·700자리 | 약 175명 | 100 |

## 파일

```
config.js        정한 값(비율·시간·구간)과 인원 계산. 숫자를 바꿀 때는 여기만 고친다
rush.js          실행. 준비(로그인·대회 한 쌍 등록·파티 만들기) 뒤 계정 하나 = 가상 사용자 하나가 구간을 차례로 돈다
lib/session.js   로그인 상태 (메모리의 Access Token, 쿠키의 Refresh Token, 401이면 재발급)
lib/flows.js     시나리오 5개. 시트의 요청 순서 그대로
seed/            준비 데이터: 본인인증 결과(identities.sh), 계정·충전·크루(seed.js), 크루 가입일 당기기, 운영 예치금 채우기
check.sql/.sh    정합성 확인
```

계정: `realflow{번호}@crewrun.test`. 번호 1부터 크루 계정(크루 i = 5i+1 크루장 ~ 5i+5), 그 뒤 개인 예매, 그 뒤 보기만.
재발급이 Refresh Token을 매번 새것으로 바꾸므로(member `TokenReissueUseCase`) 계정 하나는 가상 사용자 하나만 쓴다.
크루는 짝수·홀수 구간이 다른 묶음을 쓴다. 앞 구간 파티원 결제가 다음 구간과 겹치기 때문이다.
기본 구간(100,300,600,1000)이면 **계정 2,500개, 크루 320개**가 필요하다.

## 로컬에서 돌리기

`backend/loadtest/seat-sync/stack.sh start`로 서비스와 프론트(3177)를 띄운다. 알림 서비스(8087)도 띄운다(파티원이 알림을 본다).
k6는 도커로 돌린다 (`grafana/k6:1.2.3`). 아래는 모두 `backend/loadtest/real-flow`에서 실행한다.

```bash
K6='docker run --rm -v "$(pwd -W):/work" -w /work grafana/k6:1.2.3'   # Git Bash. 리눅스·맥은 $(pwd)
ENV="-e LOADTEST_PASSWORD=<비밀번호> -e BASE_URL=http://host.docker.internal:3177 -e API_URL=http://host.docker.internal:8000 -e STAGES=100"

# 1. 준비 데이터 (처음 한 번. STAGES를 늘리면 다시)
bash seed/identities.sh 1 190 | docker exec -i bukang-redis redis-cli      # 계정 수는 seed가 알려 준다 (STAGES=100이면 190)
MSYS_NO_PATHCONV=1 eval $K6 run $ENV -e JOIN=1 -e CHARGE=1 seed/seed.js     # → .crews.json
docker exec -i bukang-db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" bukang-db' < seed/backdate-crews.sql

# 2. 실행 (PREP: 준비 단계에 줄 시간, 로컬은 60초면 된다)
MSYS_NO_PATHCONV=1 eval $K6 run $ENV -e PREP=60 -e SUMMARY_FILE=results/local-100.json rush.js

# 3. 정합성 (rush.js가 준비 끝에 찍은 대회 ID)
bash check.sh 15,16
```

로컬 대기열은 측정용 설정(1초에 1,000명)이라 줄이 바로 빠진다. 운영과 같게 보려면 queue를 `QUEUE_SCHEDULE_TIME=3000 QUEUE_SCHEDULE_SIZE=20 QUEUE_PARTY_SCHEDULE_SIZE=5`로 띄운다.

## 운영에서 돌리기

1. **nginx 로그인 제한 스위치를 끈다**: `k8s/overlays/aws/nginx-upstreams.conf`의 `map "false"`를 `"true"`로 바꿔 main에 배포. 부하 PC는 IP 하나라 제한(IP마다 초당 1건)에 바로 막히지만 실제 사용자는 IP가 모두 다르다
2. 상품 당일 판매 설정(`k8s/overlays/aws/product-same-day-sale.yaml`)이 켜져 있는지 본다. 꺼져 있으면 오늘 오픈하는 대회가 판매 예정으로 남아 줄을 설 수 없다
3. 준비 데이터: `identities.sh`를 운영 Redis에 넣고(`backend/loadtest/README.md`의 1번과 같은 방법), `seed.js`를 `JOIN=1`로(CHARGE 없이) 돌린다. 예치금은 `seed/charge-prod.sql`, 크루 가입일은 `seed/backdate-crews.sql`(crewrun_member)을 보조 서버 MySQL에서 돌린다
   판매자: 운영에는 `seller1@crewrun.com`이 없다. 테스트 계정 `realflow0`을 가입시키고(`identities.sh 0 0`, 토큰 `realflow-0`)
   `crewrun_product.product_seller`에 승인 상태로 넣은 뒤 `-e SELLER_EMAIL=realflow0@crewrun.test`로 돌린다 (상품 쪽 판매자 표만이라 정산 대상이 되기 전에 정리한다)
   SQL에 한글(크루 이름)이 있으면 `SET NAMES utf8mb4;`를 먼저 넣는다. 없으면 0행이 바뀐다
4. `rush.js`를 `BASE_URL`·`API_URL` 없이(기본 운영 주소) 돌린다. `PREP`은 계정 수에 맞춰 넉넉히(2,310개면 600초로 준비 150초)
   k6 지표를 운영 Prometheus로 보내려면 `-o experimental-prometheus-rw --tag testid=<이름>`과 `K6_PROMETHEUS_RW_*`(사용자 `crewrun`, 비밀번호는 Secret `monitoring-remote-write`)를 붙인다. Grafana 「크루런 · 부하 테스트」에서 testid로 본다
5. 정합성: `DB=prod bash check.sh <대회 ID>`가 출력하는 SQL을 보조 서버 MySQL에서 돌린다
6. **스위치를 `"false"`로 되돌려 배포한다** (시연 전에 반드시)

X 실행은 N·M 결과를 본 뒤 `-e STAGES=600x1,600x2,600x3`처럼 쌍당 인원을 정해 돌린다. 계정이 쌍 수만큼 더 필요하다.

## 결과 보기

- 끝나면 화면에 사람 단위 숫자가 나온다: `booking_completed`(결제 완료), `booking_sold_out`(매진), `pay_busy_first`(첫 결제 503), `pay_retry_success`(다시 시도해 결제), `booking_failed_final`(결제 못 함), `party_applied`, `party_seat_short`, `flow_broken`(예상 못 한 응답)
- 요청마다 `kind`(page·read·queue·write·auth), `name`(주소), `scenario`(역할), `stage`(구간) 태그가 붙는다. `SUMMARY_FILE`의 JSON과 Grafana(k6 Prometheus 출력)에서 나눠 본다
- X는 `booking_completed`의 초당 증가(30초 창 최대)다
- 결과 HTML: `report/`의 스크립트를 Docker Node로 돌린다 (이 PC에 node가 없어도 된다)

  ```bash
  RW=<Prometheus 비밀번호>
  docker run --rm -e RW="$RW" -v "$(pwd -W)/report:/a" node:22-alpine node /a/collect.mjs <testid> <구간1 오픈 UTC> 240 <구간 수> <끝 UTC> > results/<이름>-metrics.json
  docker run --rm -v "$(pwd -W)/report:/a" -v "$(pwd -W)/results:/r" node:22-alpine node /a/summ.mjs /r/<이름>-metrics.json   # 글로 요약
  # report.mjs <지표 json> <비교용 지표 json> <Grafana 캡처 폴더> <출력 html> 로 HTML 한 장 (문구는 2026-11-20 실행 기준)
  ```

## 알아 둘 것

- k6는 기본으로 반복마다 쿠키를 비운다. 구간마다 같은 계정을 다시 쓰므로 `rush.js`에 `noCookiesReset: true`가 있어야 한다 (없으면 다음 구간에서 재발급이 401)
- 앱 열기(재발급·로그인)가 안 되면 `flow_broken{where=앱 열기}`로 센다

## 운영 결과 (2026-11-20 03:59~04:15, testid `realflow-x2-1120-0345`)

`STAGES=300x1,300x2,300x3`, 대회 110~121.

| 구간 | 결제 완료 | 매진 | 파티 신청 / 자리 부족 | X (30초 창) | 메인 CPU 최대 | 조회·대기열·쓰기 p95 (ms) |
|---|---|---|---|---|---|---|
| 1쌍 (300명) | 206 | 65 | 42 / 18 | 6.43건/초 | 82% | 1,147 · 1,380 · 1,876 |
| 2쌍 (600명) | 407 | 142 | 84 / 36 | 9.49건/초 | 99% | 1,180 · 697 · 1,002 |
| 3쌍 (900명) | 641 | 214 | 126 / 54 | 10.03건/초 | 100% | 6,371 · 4,827 · 6,006 |

- 결제 503 0, 결제 못 함 0, 정합성 어긋남 0 (54,050,000원 · 1,254석)
- **X ≈ 초당 10건**: 3쌍에서 사람은 1.5배인데 처리는 1.06배. 막은 곳은 메인 EC2 CPU 2코어 (web(Next.js) 화면 렌더링이 1위, 서비스 안 처리는 평균 수십 ms)
- 3쌍에서 web Pod가 생존 확인(TCP 1초 × 3)에 답하지 못해 재시작 → 화면 요청 19.9%가 502·504
- 상품 자리 확보 교착 11건: 운영 `participation_hold`에 `participation_id` 유니크 인덱스가 없어 판매 `findForUpdate`가 표 전체를 잠근다 (흐름 끊김 11)
- 자세한 해석: 「크루런_운영_예매오픈몰림_X_부하테스트_결과_2026-11-20.html」 (팀 공유 폴더)
