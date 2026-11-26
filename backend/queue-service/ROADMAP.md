# queue-service 작업 로드맵

queue-service(대기열)를 만드는 순서와 결정을 적습니다. 2026-10-14 사용자(B)의 구현 순서안을 바탕으로 Claude와 논의해 정했습니다.
작업은 이 문서를 기준으로 shrimp-task-manager로 나눠 진행합니다. CRUD를 한 번에 만들지 않고, 기능 하나씩 제대로 동작하는지 확인하며 진행합니다.
shrimp 작업 설명과 이 문서가 다르면 **이 문서가 우선**입니다. 작업을 시작할 때마다 이 문서의 최신 결정을 먼저 읽습니다.
참고 프로젝트는 `D:\Study\portfolio\ticketplay`이고, 우리 서비스(MSA, 게이트웨이 헤더 인증)에 맞게 바꿔서 씁니다.

- **[결정됨]**: 사용자와 함께 정한 것
- **[제안]**: Claude 의견, 아직 정하지 않음
- **[미정]**: 그 단계에서 정할 것

진행 상황(10/16): 1~6단계를 모두 마치고 PR #146으로 dev에 머지했다. Linear는 PRO-111과 하위 이슈가 Done이고, 문서 맞추기(PRO-177)는 하지 않기로 했다(Canceled). 남은 것은 아래 "남은 일"에 있다.

## 범위 (세미)

- 개인 예매 대기열: 대회 · 종목 단위로 줄 세우기, 활성(active) 승격, 순번 조회, 주문 쪽 입장 확인
- 빼는 것
  - 크루 예매의 대기열 진입: 최종 (10/13 결정, 세미는 개인 예매 + 대기열만)
  - [결정됨] 분산 락: 세미에서는 넣지 않는다. 스케줄러 서버를 일부러 따로 두었다. 최종 프로젝트에서 성능을 끌어올릴 때 일정에 따라 사용자가 정한다.

## 일정 제약

| 날짜 | 무엇 |
|---|---|
| 10/22 · 10/23 | 세미 제출 · 발표 |

- 주문(order-service)은 이다윗 님 담당이다. 주문 쪽 변경은 전달 목록으로 넘기고, 대기열과 주문을 끝까지 이어서 확인하는 일은 주문이 반영된 뒤에 한다.
- 주문은 대기열을 아직 가짜 어댑터(`FakeQueueAdmissionAdapter`, 운영 포함)로 대신한다. 대기열 HTTP 어댑터는 주문 쪽 작업이라, 대기열 쪽은 단독 테스트까지 확인했다. 상품 자리 확보는 운영에서 HTTP 어댑터(`HttpSeatHoldAdapter`)로 부른다(dev 기준 10/16).

## 결정 사항 (2026-10-14)

- [결정됨] 순서는 아래 1~6단계를 따른다 (사용자 순서안에 2단계를 새로 넣고, 순번 조회를 승격 앞으로 옮김).
- [결정됨] 뼈대 구성 (PRO-172)
  - order-service와 같은 구성: JPA · MySQL · QueryDSL · Kafka 포함
  - Redis 설정은 member-service 방식 (대기 시간 2초, dev `localhost:6379`, prod `${REDIS_HOST}`/`${REDIS_PORT}`)
  - `X-Member-Id`는 order-service 방식 (`in/MemberIdHeader` 상수 + `QueueExceptionHandler`)
- [결정됨] 대기열 진입 단위는 **대회 ID 하나 + 종목 ID 하나**다.
  - 대회(1) : 종목(N) 관계는 최종에서 대회를 기준으로 종목 옵션을 골라 대기열에 들어가도록 확장하려고 둔 것이다.
  - 세미에서 실제로 구현하는 관계는 대회(1) : 종목(1)이다. 최종에서 확장해도 대회 ID 하나에 종목 ID 하나로 대기열에 들어간다.
- [결정됨] 대기열에 들어올 수 있는 때는 판매 상태가 **`ON_SALE`**이고 **`individualOpenAt ≤ now < applicationCloseAt`**일 때다. `SCHEDULED`는 판매 예정이라 들어올 수 없다.
  - `ON_SALE`은 판매자에게만 보이는 상태다. 구매자에게 예매하기 버튼이 보이는 것은 `individualOpenAt` 시각부터다(`RaceSaleStatus.java:9`).
- [결정됨] 접수 일정은 **Kafka 복제**로 받는다. 대기열은 대회 · 종목 **복제 테이블**을 둔다.
  - 이유: 트래픽이 몰리는 대기열에서는 Cache-Aside(상품 내부 API + 캐시, 참고 `D:\Study\redis\redisinspring`)보다 복제한 대회 · 종목을 쓰는 쪽이 튼튼하고 유리하다.
  - 상품이 바뀌면 바뀐 정보가 대기열 복제 테이블에 **멱등하게** 반영되어야 한다.
- [결정됨] 여러 기기(아이폰 · 갤럭시 · 태블릿 · PC): **회원 단위 한 자리**다. 이미 줄에 있는 회원이 다시 등록하면 NX로 처리해 **순번을 유지**한다.
- [결정됨] 승격은 **주기마다 정해진 인원**을 올린다. 동시 active 상한은 두지 않는다.
  - 상한을 두면 active 회원이 결제를 마치거나 나가야 자리가 비어 다음 승격이 된다. 모두가 10분 동안 결제하지 않으면 나머지 회원은 10분 동안 같은 순번만 보게 된다.
- [결정됨] 정책: 주문 · 결제 페이지로 넘어가면 TTL 10분을 준다. 프론트 시안의 타이머 값은 예시일 뿐이다.
- [제안] Redis는 공용 `bukang-redis` 한 대를 쓴다. ticketplay의 `RedisConfig`(Sentinel, 레플리카 우선 읽기)는 가져오지 않는다. 레플리카에서 읽으면 승격 직후 조회가 옛 값을 볼 수 있다.

## 단계

### 1. yaml · gradle 검토 (+ Redis Testcontainers)

- 완료(10/14, PRO-172): 모듈 뼈대 `1bc5a73`(포트 8086, yaml 4개, compose · Dockerfile 등록), 게이트웨이 라우팅 `fcded5e`(`/api/v1/queue/**`)
- 완료(10/15): 진짜 Redis를 띄우는 테스트 수단을 넣고, 연결만 SET/GET으로 확인했다. ZSet · Lua 테스트는 스크립트를 만드는 3단계 · 5단계에서 쓴다.
  - member-service처럼 `com.redis:testcontainers-redis` + `testcontainers-junit-jupiter`. Lua는 가짜(mock) Redis로 검증할 수 없다(`member-service/.../RefreshTokenRepositoryTest.java:23`).
  - [결정됨] Docker가 없으면 실패시킨다(옵션 없는 `@Testcontainers`, member 방식). 팀원 PC는 고려하지 않는다(사용자 10/15). 전체 build는 이미 member 테스트 때문에 Docker가 필요하다.
  - [결정됨] 연결 확인 테스트 `out/RedisConnectionTest`(`redis:8.2`에 SET/GET)는 3단계 뒤에도 계속 둔다(사용자 10/15).
- yaml 4개(application, dev, prod, test)는 검토 결과 고칠 곳이 없다.
- Kafka(2단계 복제 수신)와 JPA(2단계 복제 테이블)는 둘 다 쓰므로 그대로 둔다.
- 확인(10/15): 사용자가 `RedisConnectionTest`를 실행해 통과, `./gradlew :queue-service:checkstyleMain :queue-service:checkstyleTest` 경고 0건

### 2. 상품 일정 이벤트 발행 → 대기열 복제본 수신

- 완료(10/15, PRO-176 · PRO-178)
  - 상품: `dbd6a76` 판매 상태 enum을 common으로 옮김, `51e9399` · `33fa6fd` 대회 변경 메시지, `2a31381` · `ba6e3f3` 종목 변경 메시지, `81cf276` 대기열 확인용 더미 대회
  - 대기열: `abe2e98` 메시지를 받아 복제본 저장, `5893423` 테스트
- 왜: 3단계 등록 API가 "지금 이 대회 · 종목에 줄을 세울 수 있는가"를 판정해야 한다.
- [결정됨] 대회와 종목을 함께 복제한다(대회 · 종목 복제 테이블, `QUEUE_` 접두사).
- [결정됨] 상품이 바뀌면 바뀐 정보를 복제 테이블에 멱등하게 반영한다. 같은 메시지가 두 번 와도 결과가 같다.
- [결정됨] 들어올 수 있는 때: 판매 상태 `ON_SALE` + 시각 조건 `individualOpenAt ≤ now < applicationCloseAt`.
  - 시각 조건도 보는 이유: `ON_SALE`은 판매 시작일(크루 예매 시작과 개인 예매 오픈 중 이른 날짜) 0시에 바뀌어 개인 예매 오픈 시각 전에도 `ON_SALE`일 수 있다. 또 `CLOSED`는 대회일 다음 날 0시에 바뀌어, 접수 마감 뒤에도 그때까지 `ON_SALE`로 남는다(`Race.java:233-239`, `RaceSaleStatus.java:8`).
- [결정됨] 상품의 상태 배치가 판매 상태를 바꾸면, 상품이 Kafka 생산자로서 대기열에 동기화 메시지를 보낸다.
  - 상태는 등록 때 `SCHEDULED`이고, 5분마다 도는 상태 배치가 `ON_SALE`로 바꾼다(`RaceSaleStatusScheduler`, `ProductFacade.java:149-161`). 이 메시지가 없으면 복제본은 `SCHEDULED`에 머물러 대기열이 열리지 않는다.
  - 알려진 한계: 개인 예매 오픈이 판매 시작일 0시 정각이면, 상태 배치(5분 주기)가 `ON_SALE`로 바꾸고 메시지가 올 때까지 최대 5분쯤 줄을 설 수 없다. 상품은 같은 이유로 자리 확보 판정에 `SCHEDULED`도 넣었다(`Race.java:289`).
    - [결정됨] 막지 않는다. 자정을 예매 시작 시각으로 잡는 판매자는 드문 엣지 케이스라, 실제 운영에서 문제가 생기면 그때 대응한다(사용자 10/14).
- 상품 쪽 (사용자 담당): 커밋 뒤 메시지를 보낸다. 지금 상품은 일정 메시지를 보내지 않는다(등록 · 수정은 `race.filelink.requested`, 취소는 `order.race.cancel`만).
- 대기열 쪽: [결정됨] `in/QueueKafkaConsumer`가 `@KafkaListener`로 받아 복제 테이블에 저장한다.
- [결정됨] 대회 메시지와 종목 메시지를 나눈다(사용자 10/15). 토픽 · 리스너를 대회용 · 종목용으로 나누고, 각 메시지는 자기 행의 `updated_at`으로 옛 메시지를 가린다(B5).
  - 나누면 종목 삭제를 알리는 메시지가 따로 필요하다. 대회 · 종목을 한 스냅숏에 담으면 목록을 통째로 바꿔 빠진 종목이 저절로 지워지지만, 나누면 알려 주지 않는 한 복제본에 남는다.
  - 두 토픽 사이의 순서는 보장되지 않는다. 종목 메시지가 대회 메시지보다 먼저 올 수 있어, 복제 테이블에 종목 → 대회 외래 키를 걸지 않고 진입 판정은 대회 · 종목이 둘 다 있어야 통과로 한다.
  - 사용자 판단: 나누면서 걱정한 경우들은 크지 않다.
- 세부 (B1~B8)
  - [결정됨] B1 메시지 모양(사용자 10/15). key는 둘 다 `raceId`라 같은 대회의 메시지는 토픽마다 한 파티션에 모인다.
    - 대회 메시지 `RaceChangedEvent`: `raceId`, `saleStatus`, `individualOpenAt`, `applicationCloseAt`, `updatedAt`(`race.updated_at`)
    - 종목 메시지 `RaceCourseChangedEvent`: `raceCourseId`, `raceId`, `updatedAt`(`race_course.updated_at`), `deleted`. 진입 판정에 쓰는 값은 모두 대회 칸에 있어, 종목 쪽은 "이 대회에 있는 종목인가"만 알면 된다. 종목 이름 같은 다른 값은 주문 쪽에서 필요하다고 하면 그때 더한다.
    - 위치: common `shared/race/event/`
    - [결정됨] 대회에 묶인 종목을 뺄 때마다 종목 삭제 메시지를 보낸다(사용자 10/15). 토픽은 B3의 `queuing.racecourse.changed`이고, key가 `raceId`라 같은 대회의 종목 메시지와 한 파티션에서 순서가 지켜진다. 지운 행에는 `updated_at`이 남지 않으므로, 비교 값으로 삭제 시각을 담는다(사용자 10/15).
      - [결정됨] 삭제는 종목 메시지의 삭제 표시 칸 `deleted`로 나타낸다(사용자 10/15). 평소 메시지는 `false`, 삭제 메시지는 `true`이고 `updatedAt`에 삭제 시각을 담는다. 대기열은 이 값을 복제본의 삭제 표시 컬럼에 그대로 옮긴다. 이벤트 종류 칸(`type`)은 쓰지 않는다.
    - [결정됨] 대기열은 삭제 메시지를 받으면 복제본 행을 지우지 않고, 복제 테이블의 삭제 표시 컬럼에 표시만 남긴다(사용자 10/15). 재시도로 늦게 온 옛 수정 메시지가 지운 종목을 되살리는 것을 `updated_at` 비교로 막는다.
  - B2 보내는 시점: 등록 · 수정 · 취소 커밋 뒤. 상태 배치가 판매 상태를 바꾼 뒤(`ON_SALE`/`CLOSED`)에도 보낸다([결정됨]). 발행은 `ProductFacade.publish()`(`ProductFacade.java:186`)를 쓴다. 매진(`SOLD_OUT`)은 보내지 않는다. 결제 대기가 풀리면 `OPEN`으로 돌아오는 일시적인 값이다.
    - 등록은 대회 1건 + 종목 전부를 보낸다.
    - [결정됨] 취소 · 상태 배치는 대회 메시지만 보낸다(사용자 10/15). 취소는 종목을 지우지 않고 대회 판매 상태만 `CANCELED`로 바꾸지만(`RaceRepository.java:69-74`), 진입 판정이 대회 상태를 보므로 대회 메시지만으로 막힌다. 취소가 결제 대기 자리를 풀면서 종목 `updated_at`이 바뀌어도, 대기열이 쓰는 종목 값은 그대로다.
    - [결정됨] 수정 때는 바뀐 행만 보낸다(사용자 10/15). 대회 칸이 바뀌었으면 대회 메시지, 바뀐 종목 · 새 종목은 종목 메시지, 뺀 종목은 종목 삭제 메시지.
      - [결정됨] 바뀐 행은 수정 전후의 `updated_at`을 비교해 고른다(사용자 10/15). 대회 칸만 바뀌면 `race.updated_at`만, 종목 칸만 바뀌면 그 종목의 `updated_at`만 바뀐다(B5 확인 줄). 같은 값인데 바뀐 것으로 잡히는 경우(소수 초 7~9자리 시각, `null`과 `""`)는 같은 내용을 한 번 더 보낼 뿐이라 대기열에서 멱등하게 처리된다.
      - [결정됨] 이 동작을 테스트로 다시 확인하지 않는다. 코드와 Hibernate 소스를 읽은 확인 결과로 진행한다(사용자 10/15).
  - [결정됨] B3 토픽: `queuing.race.changed`, `queuing.racecourse.changed` (상수는 common `KafkaTopics`, 이벤트 클래스는 common `shared/race/event/`). 취소는 대회 메시지의 `saleStatus = CANCELED`로 받으므로 `order.race.cancel`은 구독하지 않는다(사용자 10/15).
  - B5 순서 판단 값(늦게 온 옛 메시지 건너뛰기, 회원 동기화 방식 `ProductSyncMemberUseCase.java:21-25`)
    - [결정됨] 행마다 자기 `updated_at`으로 비교한다. 대회 메시지는 `race.updated_at`, 종목 메시지는 그 종목의 `race_course.updated_at`(사용자 10/15, 메시지 나누기와 함께 정함).
    - 검토한 다른 후보
      - 발행 시각: DB 변경 순서와 어긋날 수 있다.
      - 상품 수정 때 `race.updated_at`도 항상 갱신하도록 상품을 고친다.
      - `race.updated_at` 하나 또는 종목 `updated_at` 하나로 모두 가린다(종목 쪽은 사용자 10/14 안): 대회만 바뀐 수정과 종목만 바뀐 수정 중 한쪽을 못 가린다.
    - 참고: key가 `raceId`라 같은 대회 메시지는 한 파티션 안에서 순서가 지켜진다. 순서가 뒤집히는 것은 실패한 메시지가 재시도 토픽을 거칠 때다.
    - 확인(10/15, 코드와 Hibernate 7.4.5 소스를 읽어 확인, 테스트 실행은 안 함): 대회 칸만 고치면 `race.updated_at`만, 종목 칸만 고치면 그 종목의 `race_course.updated_at`만 바뀐다. `Race`에 `@Version`이 없어 종목 변경이 대회로 전파되지 않는다. `race_course.updated_at`은 자리 확보 · 판매 · 해제 · 복구 때도 바뀐다(`RaceCourseRepository.java:28-50`).
    - 알려진 한계: 취소 · 상태 배치는 대회 행 잠금을 잡기 전에 시각을 정한다(`ProductFacade.java:93,152`). 같은 대회의 수정과 겹치면 `race.updated_at`이 거꾸로 가서, 대기열이 새 메시지(취소 · `ON_SALE`)를 옛것으로 보고 건너뛸 수 있다.
      - [결정됨] 막지 않는다. 같은 대회의 수정과 취소를 잠금 대기 시간(밀리초) 안에 겹쳐 누르는 것은 사람의 동작이 아니다. 대기열에서 `updated_at`은 다른 칸(판매 상태 · 일정)이 옛 값으로 덮이지 않게 하는 비교용 값일 뿐이고, 판매 상태의 원본은 상품이다(사용자 10/15).
  - [결정됨] B6 재시도 · DLT: `QueueKafkaConsumer`도 `@RetryableTopic` 3번(1초 → 2초), `IllegalArgumentException`은 바로 DLT로 둔다. DLT는 로그만 남기는 전용 컨슈머 `QueueKafkaDltConsumer`(groupId `queue-service-dlt`)가 받는다. 재시도 · DLT 토픽 접미사는 다른 서비스와 겹치지 않게 `.queue.retry` · `.queue.dlt`다(`QueueKafkaConsumer.java:34-43`).
  - [결정됨] B7 대기열 전에 등록된 대회: 세미는 더미 대회를 새로 등록해 채운다(`81cf276`). "전체 다시 보내기"는 필요하면 최종에서 만든다.
  - [결정됨] B8 복제 테이블에 없는 대회 · 종목으로 등록: 404로 막는다. 상품 API로 대신 조회하지 않는다(복제를 고른 이유와 같음).
- 알려진 한계: 상품의 발행 실패는 로그만 남긴다(10/13 아웃박스 없음 결정). 메시지를 놓치면 복제본이 계속 틀린다.
  - 다만 자리 확보 때 상품이 접수 기간을 다시 확인해 409로 막으므로(`RaceHoldUseCase.java:62-67`) 자리가 잘못 잡히지는 않는다.
- 확인: 상품에서 대회 · 종목을 등록 · 수정 · 취소하고 상태 배치가 돌면 대기열 복제 테이블에 반영되는지 본다(Redpanda Console http://localhost:8091). 같은 메시지를 두 번 보내도 결과가 같은지(멱등), 옛 메시지를 건너뛰는지 확인한다.

### 3. 등록 API (회원 단위 NX)

- 완료(10/16, PRO-179): `3877a8f` feat, `84e734c` test
- 요청은 대회 ID 하나 + 종목 ID 하나다. 회원은 `X-Member-Id`로 받고, ZSet 멤버는 회원 ID다. 여러 기기는 한 자리를 함께 쓴다.
  - 두 번째 기기를 막지는 않는다. 같은 회원의 중복 예매는 주문이 막는다: 대회당 살아 있는 신청 1건(`uk_participation_active`, `Participation.java:36`), [결제하기]의 신청 행 잠금(`OrderCreateUseCase.java:53`).
- [결정됨] waiting과 active를 **함께** 확인하는 Lua 하나로 등록한다.
  - waiting에 있으면 NX로 순번을 유지한다.
  - NX는 waiting만 본다. 승격된 회원은 waiting에서 빠지므로 NX만 쓰면 다시 줄에 들어간다. 그 뒤 다시 승격되면 active 끝 시각이 덮어써져 활성 시간이 늘어난다(ticketplay `RegistrationService.java:41`).
  - 확인과 추가를 명령 두 번으로 나누면 그 틈에 승격 Lua가 끼어들 수 있어서 Lua 하나로 묶는다.
- score는 등록 시각(epoch ms)이다. 같은 밀리초 동점은 멤버 문자열 사전순으로 정렬된다(`"10"`이 `"9"`보다 앞). 영향은 작다.
- 접수 판정은 2단계 복제 테이블로 한다.
- 키는 `.claude/rules/redis-key-convention.md`(콜론 계층, 키는 한곳에서 만든다)를 따른다. Lua는 member의 `RefreshTokenRepository` 방식(`resources/redis/*.lua` + `DefaultRedisScript`)을 따른다.
  - 사용자 후보(10/14): `race:{raceId}:racecourse:{raceCourseId}` 아래에 대기열 정보를 둔다.
- ticketplay에서 바꿀 것: 인증(`@AuthenticationPrincipal` 아이디 → `X-Member-Id` 숫자), 경로(`/api/reservation/...` → `/api/v1/queue/...`), 계층 없는 `queues` 키
- [결정됨] 경로: `POST /api/v1/queue`, 본문 `{raceId, raceCourseId}`. 응답은 4단계 순번 조회와 같은 모양(`myRank`, `ahead`, `total`, `canEnter`)이다.
- [결정됨] 키(위 사용자 후보 기준)
  - 줄: `race:{raceId}:racecourse:{raceCourseId}:waiting`(score = 등록 시각), `race:{raceId}:racecourse:{raceCourseId}:active`(score = 입장 끝 시각)
  - 승격할 대기열 목록: `queue:waiting`(Set, 값 `{raceId}:{raceCourseId}`). 등록 Lua가 넣고, 승격 Lua가 waiting · active가 모두 비면 뺀다.
- [결정됨] active인 회원이 다시 등록하면 거부한다(409 "이미 입장한 회원입니다."). 끝 시각이 지난 active는 스케줄러가 지우기 전이라도 다시 줄을 설 수 있다(`queue-register.lua`).
- [결정됨] Redis 장애 때 막는다(fail-closed, 503 "대기열에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.").
- 진입 판정 응답: 복제본에 없는 대회 · 종목 404, 다른 대회의 종목 · 삭제 표시 종목 · 접수 기간 아님 403
- 확인(10/16): Testcontainers Redis로 등록 · 다른 기기 재등록(순번 유지) · active 회원 재등록 · `ON_SALE`이 아닌 대회 등록 · 개인 예매 오픈 전과 접수 마감 뒤 등록을 확인했다.

### 4. 순번 조회 API (브라우저 폴링)

- 완료(10/16, PRO-180): `c1cce02` feat, `cc41457` test
- 대기 화면이 몇 초마다 부르는 API다. 3단계 등록과 5단계 승격을 눈으로 확인하는 수단이기도 하다.
- ticketplay는 `/status`(순번 · 앞 인원 · 전체)와 `/active`(입장 여부)를 따로 두고 3초마다 둘 다 불렀다(`waiting.html:169-202`).
- [결정됨] 경로: `GET /api/v1/queue/races/{raceId}/racecourses/{raceCourseId}`. 순번 · 앞 인원 · 전체 인원 · 입장 여부를 한 번에 준다(`myRank`, `ahead`, `total`, `canEnter`).
  - 입장(active)했으면 `canEnter=true`이고 순번 칸은 모두 0이다.
- [결정됨] `expiresAt`은 넣지 않는다. 입장 끝 시각은 6단계 내부 API로만 준다.
- [결정됨] 줄에도 없고 입장 중도 아니면 404 "대기열에 없는 회원입니다."다. 끝 시각이 지난 active도 같다(따로 구분하지 않음).
- [결정됨] 대회 · 종목 복제본(DB)은 보지 않고 Redis만 읽는다. 없는 대회 · 종목 ID로 부르면 줄에 없는 회원과 같이 404다. Redis 장애는 503이다.
- [결정됨] 이 API는 DB 커넥션을 잡지 않는다. 이 저장소의 Facade 클래스 단위 `@Transactional(readOnly = true)` 관례를 따르지 않고, `QueueFacade`에 트랜잭션을 걸지 않는다.
  - ticketplay는 `RegistrationService` 클래스 단위 `@Transactional(readOnly = true)`를 빼서 HikariCP 대기가 188에서 0으로 줄고 처리량이 41% 늘었다(커밋 `d06140c`).
- [결정됨] 읽는 순서: waiting을 먼저 읽어 방금 승격된 회원을 없는 회원으로 잘못 보지 않게 하고, 전체 인원(ZCARD)을 순번(ZRANK)보다 먼저 읽어 순번이 전체보다 커지지 않게 한다. 저장소 메서드는 `findPosition` 하나(`Position` 결과)다.
- 30분 넘게 기다려 Access Token이 만료돼도 폴링은 끊기지 않는다. 프론트가 10/14부터 401을 받으면 재발급한 뒤 다시 보낸다(`frontend/src/shared/api/fetcher.ts:20-21`, 커밋 `fa81c12`). 재발급까지 실패해야(Refresh Token 만료 등) 로그인 화면으로 간다(`RaceQueuePage.tsx:40-46`).
- 확인(10/16): 등록 뒤 조회로 순번 · 앞 인원, 앞 회원이 승격되면 순번이 당겨짐, 입장 중 `canEnter`, 없는 회원 · 끝 시각 지난 회원 · 다른 종목 404를 확인했다.

### 5. 승격 스케줄러

- 완료(10/16, PRO-181): `1b8dece` feat, `36645a4` test
- waiting 앞에서부터 active로 옮기고, active 끝 시각은 승격 시각 + 10분이다.
  - Redis TTL은 키 단위라 회원마다 줄 수 없다. 그래서 active ZSet의 score에 끝 시각을 넣어 판정한다(ticketplay와 같음).
- [결정됨] 만료된 active는 **승격 스케줄러 한 곳에서만** 지운다. ticketplay는 스케줄러와 순번 조회 두 곳에서 지워서 만료 후처리(좌석 해제)가 건너뛰어졌다(`QueueScheduler.java:48-55`, `RegistrationService.java:81-82`).
  - 6단계와 이어서 정한 결과: 대기열은 끝난 active를 지우기만 하고 다른 서비스에 알리지 않는다. 자리 해제는 주문 만료 스케줄러가 결제 기한(= 입장 끝 시각)이 지난 주문의 자리를 `product.hold.cancel`로 푼다(PRO-166, `OrderExpiryUseCase.java:25-26`).
- [결정됨] `@EnableScheduling`을 넣고, 스케줄러는 `@Profile("!test")`로 테스트에서 끈다(order · product 관례).
- [결정됨] 승격 규칙: 주기마다 정해진 인원(동시 active 상한 없음, 결정 사항 참고). 동시에 active인 회원 수는 저절로 "주기마다 올리는 인원 × (600초 ÷ 주기)"를 넘지 않는다(5초마다 20명이면 대기열 하나에 최대 2,400명).
- [결정됨] 주기 5초, 대기열(대회 · 종목)마다 한 번에 20명, 활성 시간 10분. 세 값은 `application.yaml`의 `queue.schedule.time` · `queue.schedule.size` · `queue.active.time`이다(`config/QueueProperties`, 사용자 10/16).
  - 주기를 3초로 바꿨다. 프론트가 입장 여부를 묻는 주기(3초)와 맞춰 입장이 화면에 늦게 보이지 않게 한다. 한 번에 20명은 그대로라 종목당 입장 속도는 초당 4명에서 약 6.7명으로 빨라진다. 결제 부하는 다시 측정한다(CRU-48, 사용자 11/18).
- [결정됨] 대기열마다 승격 Lua(`queue-promote.lua`)를 한 번 부른다: 끝난 active 지우기(`ZREMRANGEBYSCORE`) → waiting 앞에서 꺼내(`ZPOPMIN`) 끝 시각을 score로 active에 넣기 → waiting · active가 모두 비면 `queue:waiting`에서 빼기. 한 번에 처리해 그 사이에 등록 Lua가 끼어들지 못한다(사용자 10/16).
- [결정됨] 승격 때 접수 기간은 다시 확인하지 않는다. 승격 · 만료 인원은 바뀐 것이 있을 때만 debug 로그로 남긴다(dev에서만 보임)(사용자 10/16).
- [결정됨] 한 대기열 키(대회 · 종목)에서 오류가 나도 다른 대회 대기열의 승격에는 영향이 없어야 한다. 대기열 키마다 오류를 따로 처리한다.
- [결정됨] 방식: `@Scheduled` + UseCase(order · product 관례). Spring Batch를 쓴다고 더 효율적이지 않다(10/14 Claude 판단, 사용자 동의).
  - 승격은 몇 초마다 Redis 명령 몇 개(Lua)를 실행하는 일이다. Batch의 장점(대량 데이터 청크 처리, 실패 지점부터 재시작, 실행 기록)을 쓸 곳이 없다.
  - 실패해도 다음 주기가 곧 재시도이므로 재시작 기능이 필요 없다.
  - Batch는 Job을 실행할 때마다 실행 기록을 남긴다. JDBC 저장소(file-service 방식)면 3초 주기에 하루 약 28,800번 `BATCH_` 테이블에 행이 쌓인다. 기록을 남기지 않는 `ResourcelessJobRepository`(spring-batch-core 6)도 있지만, 그래도 얻는 것이 없다.
- [결정됨] 시각: Facade가 `LocalDateTime.now()`를 만들어 UseCase에 넘긴다(order · product 방식). epoch ms와 `LocalDateTime`은 `Asia/Seoul`로 바꾼다(`QueueRedisRepository`).
- [결정됨] 이탈 API는 만들지 않는다. 창을 닫은 회원도 순번을 유지한 채 승격돼 10분 동안 active로 남는다(사용자 10/16).
- 확인(10/16): 고정 시각으로 UseCase를 직접 불러 앞에서 20명만 승격, 끝 시각, 만료 정리, 빈 대기열 목록에서 빼기, 대기열마다 따로 승격, 형식이 틀린 목록 값 건너뛰기를 확인했다(Testcontainers Redis). dev 실행으로 5초 주기를 보는 확인은 하지 않았다.

### 6. 주문 연동 API

- 완료(10/16, PRO-182): `f080f12` feat, `c946f95` test
- 주문이 [결제하기] 때 "이 회원이 이 대회 · 종목의 active인가"와 active 끝 시각(`expiresAt`)을 확인할 수단이다.
  - ticketplay는 한 앱 안에서 `registrationService.isActive(...)`를 직접 불렀다(`WorkController.java:60-62`). 결제가 끝나면 `leaveActive(...)`로 active에서 뺐다(`PaymentService.java:44-45`). 우리는 서비스가 나뉘어 있어 서비스 간 통신이 필요하다.
- 필요한 이유
  - 대기열 건너뛰기를 막는다. 주문 API는 게이트웨이로 누구나 부를 수 있고, 브라우저가 폴링으로 받은 결과는 믿을 수 없다.
  - 상품 자리 확보 API가 `expiresAt`(대기열 활성 시간이 끝나는 시각)을 필수로 받는다(`HoldRequestDto.java:27-31`). 주문은 dev에서 `SeatHoldPort.hold(raceCourseId, participationId, expiresAt)`로 바뀌어, [결제하기] 때 입장 끝 시각을 결제 기한으로 보낸다(`SeatHoldPort.java:10-12`, `OrderCreateUseCase.java:79-86`).
- [결정됨] 정책: 주문 · 결제 페이지로 넘어가면 TTL 10분을 준다.
- [결정됨] 방법: 주문이 대기열 내부 API를 부른다(사용자 10/16). 서명 토큰 · 게이트웨이 검사 안은 쓰지 않는다.
  - `GET /internal/v1/queue/races/{raceId}/racecourses/{raceCourseId}`, 회원은 `X-Member-Id` 헤더로 받는다(4단계와 같은 모양). 내부 호출이라 주문이 받은 회원 ID를 직접 넣는다.
  - 입장 여부는 항상 200 `{admitted, admissionEndsAt, remainingMillis}`로 답한다. 줄 서는 중 · 입장 시간이 끝남 · 대기열에 없음이면 `admitted=false`, `admissionEndsAt=null`, `remainingMillis=0`이다. 요청이 잘못되면 400(대회 · 종목 · 회원 ID 오류), 401(`X-Member-Id` 없음)이다.
  - `remainingMillis`를 함께 주는 이유: `LocalDateTime`에는 시간대가 없어, 주문과 대기열 앱의 기본 시간대가 다르면 끝 시각이 어긋난다. 받는 쪽이 자기 지금 시각에 남은 시간을 더하면 시간대와 상관없이 맞다.
  - Redis 장애 때도 503이 아니라 `admitted=false`로 답한다(사용자 10/16). 응답으로 장애가 드러나지 않으므로 저장소가 에러 로그를 남긴다.
  - 대회 · 종목 복제본(DB)은 보지 않고 Redis만 읽는다. 끝 시각이 지난 active는 스케줄러가 지우기 전이라도 입장 중으로 보지 않는다(4단계와 같은 기준).
- 주문이 부르는 곳(dev 주문 코드 기준): 타이머 API(`GET /api/v1/order/queue/admission`, `QueueAdmissionUseCase`)와 [결제하기](`OrderCreateUseCase`, 장바구니 결제도 지남) 두 군데다. 결제(예치금 차감 → 판매)는 대기열을 묻지 않고 결제 기한만 본다.
  - dev 프론트는 타이머 API를 결제 화면뿐 아니라 대기 화면(3초마다)과 신청서 화면에서도 부른다(`use-queue-admission.ts:12`, `RaceQueuePage.tsx:33-37`, `RaceApplyPage.tsx:83`, `CheckoutPage.tsx:230`). 주문이 HTTP 어댑터를 붙이면, 프론트를 바꾸기 전까지 대기 화면 폴링마다 주문이 대기열 내부 API를 부른다.
- [결정됨] 결제 뒤 active는 반납하지 않는다. 끝 시각이 지나면 승격 스케줄러가 지운다(10/15 이다윗 님 합의, 10/16 사용자 재확인: `queuing.active.clear` 메시지는 만들지 않는다).
  - 승격은 active 인원과 상관없이 주기마다 정해진 인원이라, 반납해도 다른 회원이 더 빨리 들어가지 않는다. 지우는 일은 어차피 스케줄러 명령 한 번에 묶여 Redis 부담도 늘지 않는다.
- 이다윗 님과 정할 것으로 두었던 항목의 결과(dev 주문 코드 기준)
  - 참가 신청서는 대기열 뒤에 쓴다(대기열 → 신청서 → [결제하기]). 신청서 API는 대기열을 검사하지 않고, [결제하기]가 검사한다.
  - 장바구니는 한 종목만 담고, 장바구니 결제는 참가 신청 1건만 받는다(PRO-192). 장바구니 결제도 [결제하기]처럼 대기열 입장을 확인한다(PRO-191, `OrderCreateUseCase.java:79`).
  - active가 끝났을 때의 자리 해제는 주문 만료 스케줄러가 `product.hold.cancel`로 한다(PRO-166, 5단계 참고).
- 주문 코드는 수정하지 않는다. 주문 쪽 변경은 아래 "남은 일"의 전달 목록에 있다.
- 확인(10/16): 대기열 쪽 단독 테스트로 입장 중이면 끝 시각과 남은 시간을 받고, 끝 시각이 지났거나 active에 없으면 `admitted=false`, Redis 장애면 `admitted=false`임을 확인했다. 주문과 이어서 확인하는 일은 주문 HTTP 어댑터가 반영된 뒤에 한다.

## 남은 일 (단계 밖)

- 완료: 이슈와 PR은 대기열 작업을 모두 마친 뒤 한 번에 했다(사용자 10/14 결정). 브랜치 `feat/PRO-172-queue-skeleton`을 dev와 맞춘 뒤 PR #146으로 머지했다(10/16). PRO-111 · 172 · 176 · 178~182는 Done이다.
- [결정됨] 문서 맞추기(PRO-177)는 하지 않는다(사용자 10/16, Canceled). 그때 적어 둔 대상: `docs/policy/party-lottery.md` · `backend/product-service/ROADMAP.md`의 대기열 "최종" 표기, `GLOSSARY.md`의 대기열 · 활성 시간 용어, `docs/erd/cash.md`의 "결제 기한은 개인 10분", "중복 기기 불가" 정책 원문(피그잼 2차 8장).
- 전달 목록 (대기열 코드로는 고칠 수 없는 것, 사용자가 전달)
  - 이다윗 님(주문)
    - 대기열 HTTP 어댑터: 이다윗 님이 `feat/PRO-226-queue-admission-http` 브랜치에서 진행 중이다(10/16 기준 dev 머지 전, `ea3717e` · `038dcbc`). `HttpQueueAdmissionAdapter`가 6단계 내부 API를 부르고 끝 시각은 `remainingMillis`를 주문의 지금 시각에 더해 세며, 대기열 호출 제한을 3초(`order.clients.queue.timeout`)로 따로 두어 대기열의 Redis 제한 2초보다 길게 기다린다. 운영 queue mode는 http로 바뀐다.
    - 알릴 것: Redis 장애 때도 대기열은 503이 아니라 200 `admitted=false`로 답한다. 주문 포트 주석("장애면 503", `QueueAdmissionPort.java:14-15`)과 다르고, 주문은 이 경우를 "입장 전" 403으로 알리게 된다(사용자 10/16 결정).
    - 결제 뒤 active 반납은 없다(10/15 합의 그대로).
  - 프론트 담당
    - 대기 화면이 지금은 3초마다 주문의 `GET /api/v1/order/queue/admission`만 부르고, 대기열 등록 · 순번 조회(`/api/v1/queue`)를 부르지 않는다(`use-queue-admission.ts:12,47`, `RaceQueuePage.tsx:33-37`). 가짜 대기열이 처음 물을 때 바로 입장시켜서 지금은 돌아간다. 진짜 대기열로 바꾸면 "3단계 등록 → 4단계 폴링(`canEnter`) → 입장 뒤 타이머 값 받기"로 바꿔야 한다.
- 알려진 한계 (10/16 논의, 막지 않음)
  - Redis가 고장 나면 줄 순서를 잃는다. 자리(확보 · 판매)와 결제 기한은 상품 · 주문 DB에 있고 결제 단계는 대기열을 묻지 않으므로, [결제하기]를 마친 회원은 영향이 없다. waiting · active · `queue:waiting`은 한 Redis에서 함께 사라지거나 같은 스냅숏 시점으로 함께 돌아오고, active는 끝 시각으로 판정하므로 대기열 안에서 값이 서로 어긋나지 않는다.
  - 상품은 매진(`SOLD_OUT`)을 다른 서비스에 알리지 않는다. 대기열도 매진을 몰라, 매진된 종목의 대기자도 승격되고 [결제하기]에서 매진 응답을 받는다.
- 확인하지 않은 것: bootRun으로 8086에서 띄워 승격 5초 주기 보기, Redis를 멈춘 상태의 503 · `admitted=false`, Redpanda Console로 상품 메시지 확인

## 논의 기록

### 2026-10-14 뼈대 (PRO-172)

- 사용자 지시: 다른 `*-service`와 같은 구조로 만든다. Redis · Docker · `X-Member-Id` · yaml(dev · prod · test) · gradle은 다른 서비스와 맞춘다.
- 사용자 선택: JPA 포함, Kafka 포함, `X-Member-Id`는 order 방식, Linear는 PRO-111 아래 하위 이슈 + 세미 제출(PRO-111 마일스톤도 최종 발표 → 세미 제출)
- Claude 기본값(보고함): 포트 8086, 게이트웨이 경로 `/api/v1/queue/**`, compose 앱 컨테이너는 포트를 열지 않음, 공용 `bukang-redis` 한 대, `@EnableScheduling`은 구현 때, 커밋은 모듈 뼈대와 게이트웨이 라우팅 두 개

### 2026-10-14 구현 순서 논의

- 사용자 순서안: ① yaml · gradle 검토 ② 주문으로 넘겨줄 데이터 논의(상품 정보, 유저 정보?, 판매자 정보?) ③ Waiting 등록 API(중복 기기 불가) ④ Waiting → Active 승격(TTL 10분) 스케줄러(배치?) ⑤ 주문 서비스에서 상태 폴링을 위한 API
- Claude 의견
  - ②: 상품 · 판매자 정보는 주문이 상품 내부 API(`/internal/v1/product/courses/{id}`)로 직접 가져오고, 회원은 `X-Member-Id`로 받는다. 대기열이 줄 것은 입장 여부와 `expiresAt`이다.
  - ⑤: 브라우저 폴링(순번 조회)과 주문의 입장 확인 두 가지로 읽힌다. 경로 · 인증 · 응답이 모두 다르다.
  - ③: 지금 요청 정보(`X-Member-Id`)로는 기기를 구분할 수 없다. Access Token에 기기 정보가 없다.
  - 빠진 것: 대기열 단위, 여는 시점과 일정 출처, 이탈 · 반납, Redis 장애 정책, Redis 테스트 수단
- 사용자 결정
  - 대기열 단위: 종목 (세미 1:1)
  - 일정 출처: Kafka 복제 (상품 내부 API + Cache-Aside 대신)
  - 여러 기기: 회원이 이미 줄에 있으면 NX
  - NX면 순번 유지 → Claude 확인: 맞다. 단 waiting과 active를 함께 확인해야 한다(3단계).
- 사용자 질문: "주문 연동 API는 원래 없던 단계인가?" → ⑤를 두 갈래로 나눈 것이고, ticketplay에서는 API 없이 같은 앱 안에서 `isActive`를 직접 불렀다. 남은 질문(입장 확인 방법)에 따라 6단계 모양이 바뀐다.
- 최종 순서(사용자 확인): 1 yaml · gradle(+ Redis Testcontainers) → 2 상품 일정 발행 · 복제 수신 → 3 등록 → 4 순번 조회 → 5 승격 → 6 주문 연동

### 2026-10-14 ROADMAP 검토 (사용자 피드백)

- 사용자 결정
  - 진입 단위: 대회 ID 하나 + 종목 ID 하나. 1:N은 최종 확장(대회를 기준으로 종목 옵션 선택)용이고, 세미 구현은 1:1이다.
  - 들어올 수 있는 판매 상태는 `ON_SALE`이다(`SCHEDULED`는 판매 예정).
  - 대회와 종목을 함께 복제한다. 바뀐 정보는 복제 테이블에 멱등하게 반영한다. Redis 키 후보 `race:{raceId}:racecourse:{raceCourseId}`
  - 복제를 고른 이유: 트래픽이 몰리는 대기열에서 Cache-Aside보다 대회 · 종목 복제가 튼튼하고 유리하다.
  - 대기열 컨슈머 이름은 `QueueKafkaConsumer`
  - 만료된 active는 승격 스케줄러 한 곳에서만 지운다. 스케줄러는 `@Profile("!test")`
  - 분산 락은 세미에서 넣지 않는다(스케줄러 서버를 따로 둠). 최종에서 성능을 끌어올릴 때 사용자가 정한다.
  - 승격은 주기마다 정해진 인원이고, 동시 active 상한은 두지 않는다(상한이면 모두가 결제하지 않는 10분 동안 대기열이 멈춘다).
  - 정책: 주문 · 결제 페이지로 넘어가면 TTL 10분. 프론트 시안 타이머는 예시다.
  - 이슈 정리와 PR은 대기열 작업을 다 마친 뒤 한 번에 한다.
- 사용자 지적: "종목마다 예외를 잡아 다음 종목을 계속 처리한다"는 문장은 시스템을 잘못 이해한 것이다 → 이 [제안]을 지웠다.
- Claude 답
  - `updated_at`: 종목만 바꾼 수정은 `race_course.updated_at`만 바뀌고 `race.updated_at`은 그대로다(B5에 반영).
  - Spring Batch: 승격에는 더 효율적이지 않다(5단계 [제안]에 이유).
  - `ON_SALE`을 기준으로 하면 상태 배치가 바꾼 상태도 메시지로 보내야 한다(B2에 반영).

### 2026-10-14 ROADMAP 검토 2차

- 사용자 결정
  - Spring Batch 대신 `@Scheduled` + UseCase (Batch는 승격에 이득이 없다)
  - 한 대기열 키에서 오류가 나도 다른 대회 대기열 승격에는 영향이 없어야 한다. 앞서 "다음 종목을 계속 처리"라고 적은 것은 같은 대회의 다음 종목으로 읽힐 수 있는 표현이었다.
  - 상품 상태 배치가 판매 상태를 바꾸면 상품이 Kafka로 대기열에 동기화 메시지를 보낸다.
  - 진입 조건은 `ON_SALE` + `individualOpenAt ≤ now < applicationCloseAt`. `ON_SALE`은 판매자에게만 보이는 상태이고, 구매자 예매하기 버튼은 `individualOpenAt`부터 보인다.
- 사용자 질문: "종목의 `updated_at`을 기준으로 두면?" → 종목 `updated_at`만으로는 대회 칸 변경을 못 가린다. 행마다 자기 `updated_at`으로 비교하는 안을 [제안]으로 B5에 적었다.

### 2026-10-15 B5 `updated_at` 확인

- 사용자 요청: 대회만 고치면 대회 `updated_at`만, 종목만 고치면 종목 `updated_at`만 바뀌는지 확인 → 맞다(B5 확인 줄).
- 사용자 안: 동기화 컨슈머를 대회용 · 종목용으로 나누기 → 처음에는 [미정]으로 적었다가, 같은 날 사용자가 나누기로 정했다. B5도 행마다 자기 `updated_at`으로 비교하는 것으로 정해졌다.
- 이어서 사용자 결정: 종목을 뺄 때마다 종목 삭제 메시지, 취소 · 상태 배치는 대회 메시지만, 수정 때는 바뀐 행만, 토픽 두 개(B3) 확정.
- 2단계 상품 쪽 질문에 대한 사용자 결정: B1 필드와 record 이름(`RaceChangedEvent`, `RaceCourseChangedEvent`) · 위치 승인, 종목 이름 등은 주문이 요청할 때 더함, 삭제 메시지는 삭제 시각을 담음, 대기열 복제본은 삭제 표시 컬럼으로 남김, 바뀐 행은 수정 전후 `updated_at` 비교로 고름, `updated_at` 동작 테스트는 하지 않음, 삭제는 종목 메시지의 `deleted` 칸으로 나타냄.
- Claude가 찾은 한계: 취소 · 상태 배치가 수정과 겹치면 `race.updated_at`이 거꾸로 갈 수 있다 → 사용자 결정: 막지 않는다(사람이 낼 수 없는 겹침, `updated_at`은 비교용 값일 뿐).
- 사용자 결정(10/14): 개인 예매 오픈이 판매 시작일 0시 정각일 때의 최대 5분 지연도 막지 않는다(2단계 알려진 한계).

### 2026-10-16 순번 조회 (4단계)

- 사용자 결정: 경로 `/races/{raceId}/racecourses/{raceCourseId}`, 입장하면 `canEnter=true`에 순번 칸 0, `expiresAt`은 넣지 않음, 줄에도 active에도 없으면 404, 대회 · 종목 복제본은 보지 않음, Redis 장애 503, 전체 인원을 순번보다 먼저 읽음, 저장소 메서드는 `findPosition` 하나

### 2026-10-16 승격 스케줄러 (5단계)

- 사용자 결정: 5초마다 대기열마다 20명, 활성 시간 10분, 세 값은 `application.yaml`로 옮김
- 사용자 질문: 등록 Lua가 끼어들 수 없다는 점의 트레이드오프 → Claude 답: Lua가 도는 동안 다른 명령이 기다린다. 수만 명을 한 번에 옮기는 것이 아니면 실행 시간이 짧다 → 사용자 결정: 대기열마다 Lua 하나로 처리
- 사용자 결정: waiting · active가 모두 비면 목록에서 빼기, 승격 때 접수 기간 확인 안 함, 이탈 API 없음, Facade가 지금 시각을 넘김, 바뀐 것이 있을 때만 debug 로그
- 사용자 질문: ticketplay는 프론트가 3번 폴링하지 않으면 대기열에서 빼지 않았나 → Claude 확인: ticketplay에는 그런 API가 없다. 창을 닫을 때 경고(`beforeunload`)만 띄우고(`waiting.html:222-227`), 지우는 API는 좌석 페이지의 `DELETE …/active` 하나다.

### 2026-10-16 주문 연동 (6단계)

- 사용자 결정: 주문이 대기열 내부 API를 부름, 회원은 `X-Member-Id` 헤더 + 대회 · 종목은 경로, 응답은 항상 200 + `admitted`, 입장 아님과 Redis 장애 모두 `admitted=false`, 끝 시각과 함께 남은 시간(ms)도 줌
- 사용자 결정: 결제 뒤 active 반납 메시지(`queuing.active.clear`)는 만들지 않고 자연 소멸에 맡김
  - 사용자 질문: 삭제도 쓰기인데 쌓아 두다 자연 소멸하는 것과 메시지로 지우는 것 중 무엇이 Redis에 부담인가 → Claude 답: 둘 다 회원 한 명을 한 번 지운다. 자연 소멸은 이미 도는 승격 Lua 한 번에 묶여 추가 명령이 없고, 남아 있는 메모리는 대기열 하나에 수백 KB 수준이다.
- 사용자 의견: 프론트가 받은 TTL로 결제 페이지 타이머를 세는 것은 놔둬도 된다. 주문 페이지는 폴링하지 않는다. (서버는 [결제하기] 때 다시 확인한다.)
- 사용자 의견: 신청서는 대기열 뒤에 쓴다. 장바구니는 주문 담당이 정할 일이다(dev 주문 코드에서 한 종목 · 결제 때 입장 확인으로 이미 정해짐).

### 2026-10-16 마무리

- 사용자 결정: 문서 맞추기(PRO-177)는 하지 않음(Canceled), 대기열 이슈 모두 Done, PR #146 머지
