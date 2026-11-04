# product-service 작업 로드맵

product-service를 만드는 순서와 결정을 적습니다. 2026-10-12 사용자(B)의 구현 순서안을 바탕으로 Claude와 논의해 정했습니다.
작업은 이 문서를 기준으로 shrimp-task-manager로 나눠 진행합니다. 단계 안에서 세부 결정이 필요하면 grill-with-docs로 정하고 "논의 기록"에 적습니다.

- **[결정됨]**: 사용자와 함께 정한 것
- **[제안]**: Claude 의견, 아직 정하지 않음
- **[미정]**: 그 단계에서 정할 것

## 범위 (세미)

- 대회(`race`) · 종목(`race_course`)과 정원, 주문에 주는 조회 · 정원 API, 대회 이미지 연결, 등록 · 조회 API
- 빼는 것
  - 키트(`goods`): 피그잼에서 정책 삭제 (ERDCloud에만 남아 있음)
  - 대기열 · 추첨: 최종 (`docs/policy/party-lottery.md`, PRO-41)
  - `race_faq` · `race_question` · `race_terms`: 최종 (PRO-39)
  - 크루 몫 묶음 확보: 세미 포함 여부를 팀에 확인 (PRO-39는 개인 신청만, party-lottery는 세미에 넣음)

## 일정 제약

| 날짜 | 무엇 |
|---|---|
| 10/14 | 주문(C)은 상품 API를 가짜 어댑터로 대신한다. 계약(경로 · 요청 · 응답 · 상태)이 맞아야 그대로 바꿔 끼운다 |
| 10/16 | 주문이 상품 실제 API에 연결 (PRO-37 · 41 · 42 · 44 · 46) |
| 10/18 | 결제와 만료 동시성 검증 (PRO-47) |
| 10/22 · 10/23 | 세미 제출 · 발표 |

- [결정됨] 주문용 API는 10단계까지 끝낸 뒤 사용자가 주문 담당에게 공유한다 (10/14 전 계약 공유 제안은 채택하지 않음).

## 결정 사항 (2026-10-12)

- [결정됨] 순서는 사용자 안을 따른다 (아래 1~10단계).
- [결정됨] 대회 · 종목 이름은 ERD를 따른다.
  - 테이블 `race` · `race_course`, PK `race_id` · `race_course_id`, `created_at` · `updated_at`
  - Java는 common `BaseIdAndTime`을 상속하고 `@AttributeOverride`로 컬럼 이름만 바꾼다. 그래서 `FileRef.of(race)`를 그대로 쓴다.
  - ID 타입은 지금 코드처럼 int(INT). PRO-74(BIGINT 전환) 때 함께 바꾼다.
- [결정됨] 정원은 CRUD 단계에서 함께 만든다. 정원 설정은 등록 · 수정에, 주문에 주는 정보 API는 조회(Read)에 들어간다.
- [결정됨] 회원 · 판매자 복제본을 만든다.
  - 회원가입 · 판매자 메시지가 Kafka로 온다고 가정한다. 메시지 DTO는 ERD 컬럼 그대로라고 가정한다.
  - 메시지 모양은 기능을 다 만든 뒤 회원 쪽과 통일한다.
  - 지금 발행하는 서비스가 없어서 더미 데이터 · 메시지로 확인한다.
- [결정됨] ES 문서는 엔티티와 함께 만든다. 참고 자료는 사용자가 진행하며 준다.

## 단계

### 1. 의존성 · application 설정

- 완료: 모듈 뼈대(포트 8085, `ProductApplication`, yaml 4개, compose · Dockerfile 등록), `default_batch_fetch_size: 100`(사용자 추가)
- 완료(10/12): ES 의존성(`spring-boot-starter-data-elasticsearch`, `-test`), `spring.elasticsearch.uris`(dev `http://localhost:9200`, prod `${ELASTICSEARCH_URIS}` · 계정), compose `depends_on: bukang-elasticsearch`
- [결정됨] dev에 ES 요청 · 응답 로그(WIRE trace)는 넣지 않는다. test 프로파일에도 `default_batch_fetch_size: 100`을 넣는다.
- [미정] test 프로파일에서 ES 처리. 저장소에 ES 코드가 없어 선례가 없다. 6단계에서 `@Document`를 넣을 때 정한다(자동 설정 제외 / Testcontainers `disabledWithoutDocker`).
- 확인: `./gradlew :product-service:build`

### 2. ERD 회원 · 판매자 표 확인 (완료, 2026-10-12)

- ERDCloud와 피그잼의 `member` · `seller`는 컬럼 이름 · 타입 · NULL · FK가 같다.
  - 차이는 `member.name` 논리명(이름 / 실명)과 피그잼에 빠진 기본값 표기(`seller.status` 'PENDING', `requested_at`)뿐이다.
- `seller`는 `member`와 다른 테이블이다. `seller.member_id` → `member`, 상태 PENDING / APPROVED / REJECTED. `race.seller_id`는 `seller.seller_id`를 가리킨다. `member.role`은 USER / ADMIN뿐이다.
- 코드와는 차이가 크다: `MEMBER_MEMBER`(`id`, `create_date`, int, `username`), 판매자 엔티티 없음, JWT에 역할 없음.
  - PRO-70(10/12 dev 머지)으로 회원 원본은 ERD 이름(`member_id` · `created_at` · `updated_at`)이 되고 `username`이 빠졌다. ID는 아직 int다.
- [제안] `race` · `race_course`도 6단계 전에 ERDCloud와 피그잼 컬럼을 대조해 기준을 정한다. 10/11 조사 때 `min_age` · `max_age` · `visibility`가 피그잼에만 있었다. 주문(PRO-39)이 종목 나이 제한을 쓴다.

### 3. 구매자 복제본

- common `ReplicaMember` 상속 (`CashMember`와 같은 방식). 공개 정보만 저장한다 (비밀번호 · 이메일 · 실명 · 휴대폰 · 생년월일은 원본에만, `backend/CLAUDE.md`).
- [결정됨] 이름: `ProductMember`, 테이블 `PRODUCT_MEMBER` (`CashMember`와 같은 "서비스 + Member")
- 필드: `id`(원본 회원 ID, PK, 자동 증가 없음) · `createDate` · `modifyDate`(원본 시각) · `nickname`. 저장소는 `out/ProductMemberRepository`
- PRO-70(10/12 dev 머지)이 `username`을 뺐다. 생성자 인자는 4개(`id`, `createDate`, `modifyDate`, `nickname`)다.

### 4. 판매자 복제본

- 공통 부모 클래스가 없다. [미정] common `shared`에 둘지(판매자 ID는 payout 등 여러 서비스가 쓴다), product-service 안에 둘지
- [제안] 저장할 것: `seller_id`, `member_id`, 상태(승인 여부), 표시용 이름(`business_name` / `representative_name`), `seller_type`. 정산 계좌는 저장하지 않는다.
- [미정] 이름

### 5. Kafka 수신 → 복제본 저장

- 회원가입 · 판매자 메시지를 `@KafkaListener`로 받아 3 · 4단계 엔티티에 저장한다. 메시지 DTO는 ERD 컬럼 그대로라고 가정한다.
- 토픽 이름은 `.claude/rules/kafka-topic-convention.md`(`<message-type>.<dataset>.<data>`, 예: `queuing.member.joined`)를 따르고 상수 한곳에 모은다. 실제 이름은 회원 쪽과 통일할 때 확정한다.
- 확인: 더미 메시지를 Redpanda Console(http://localhost:8091)이나 테스트에서 발행 → DB 행 확인. 같은 메시지가 두 번 와도 결과가 같아야 한다(멱등).

### 6. 대회 · 종목 엔티티 + ES 문서

- `Race`, `RaceCourse`. 이름 규칙은 위 결정 사항대로. 클래스 이름 `Race`가 파일 연결 대상 종류(`refType`)가 된다.
- Enum 컬럼은 file-service 방식(`@Enumerated(STRING)` + varchar)
- ES 문서: 사용자가 주는 참고 자료를 따른다. nori 분석기는 compose 이미지에 설치만 되어 있고 매핑 · 설정 파일은 없다.
- [미정] 자리 행(`participation_hold`) 엔티티를 여기서 만들지, 8단계에서 만들지

### 7. 상품 등록 더미 HTML

- 목적: 등록 때 요청에 들어올 정보 파악
- file-service와 같은 방식: `src/main/resources/dev-static/` + `application-dev.yaml`의 `static-locations` → dev에서만 열리고, 같은 주소라 CORS가 필요 없다.
- [제안] 이미지는 file-service의 `upload-test.html`(8083)에서 올리고 받은 fileId를 입력한다. 이 페이지에서 직접 올리면 file-service와 S3 버킷에 CORS를 더해야 한다.
- 참고 정책: `docs/policy/listing-gaps.md`(일정 · 가격 · 환불 기준은 입력 칸, 대표 이미지 1장 필수, 가격 > 0, 종목별 나이 제한), `docs/ideation/02-4-reference-runable-product.md`

### 8. CRUD (정원 포함)

- 등록 · 수정: 대회 · 종목, 정원 설정(크루 몫 · 개인 몫), 이미지 연결
  - 이미지: 대회 저장 → `FileRef.of(race)` → `PUT /internal/v1/file/refs/Race/{raceId}/files` → 커밋. `ownerId`는 받은 `X-Member-Id`. 저장소의 첫 HTTP 클라이언트(RestClient)가 된다.
  - 정책: 판매 시작 뒤 가격 · 일정 · 정원 · 환불 기준 변경 제한, 정원은 판매 · 확보 수량 아래로 줄이지 못함, 0원 등록 불가
- 조회(Read): 공개 목록 · 상세, 판매자의 내 대회, 주문에 주는 정보(가격 · 판매 상태 · 남은 정원 · 나이 제한 · 접수 마감 · 대회일 · sellerId; PRO-39 · 44 · 62)
- 삭제: [제안] 지우지 않고 취소 · 판매 중지로 처리
- 정원 변경 (주문 전용 내부 API, PRO-41 · 42 · 44 · 46)
  - 확보(개인 자리 1석, 10분) · 연장(+2분 1회) · 판매(HELD → SOLD, 기한 안일 때만) · 만료 해제와 취소 때 복구
  - 조건부 UPDATE로 마지막 자리를 지킨다 (크루 예매 ERD v5)
  - [미정] 만료 해제를 누가 실행할지
- 판매자 확인: `X-Member-Id` + 4단계 판매자 복제본(승인 상태)
- [미정] 게이트웨이 라우팅: PRO-17은 member · cash만 라우팅한다. 옥지훈 님과 협의
- 동시성 테스트: PRO-47(10/18)과 함께 검증

### 9. 더미 HTML로 확인

- Playwright MCP로 하거나 사용자가 직접 동작을 확인한다.

### 10. 테스트 코드

- 테스트 상황 설명과 함께 작성한다 (기존 관례: `@DisplayName` 한글, 메서드 이름 영어).
- 구조는 file-service 방식: API 테스트는 `@SpringBootTest` + MockMvc + H2, 외부 호출(file-service)은 Fake로 바꿔 끼운다.
- [결정됨] 테스트는 이 단계에서 모아 쓴다 (단계별 테스트 제안은 채택하지 않음). 정원 API 동시성 테스트는 8단계 범위라 8단계에서 쓴다.

## 남은 일 (단계 밖)

- Linear (2026-10-12 생성): 부모 PRO-87. 하위 PRO-88(1단계) · PRO-89(3) · PRO-90(4) · PRO-91(5) · PRO-92(6) · PRO-93(7) · PRO-94(8 등록 · 수정) · PRO-95(8 조회) · PRO-96(8 자리 API) · PRO-97(9) · PRO-98(10)
- 뼈대 커밋: `feat/PRO-88-product-skeleton` 브랜치. PRO-38(order-service) 브랜치와 `settings.gradle.kts` · `compose.yml` · `Dockerfile` · 문서가 충돌한다(양쪽 모두 남기면 됨).
- dev의 `.dockerignore`에 `payout-service`가 빠져 이미지 빌드가 실패한다. PRO-38이 고친다.

## 논의 기록

### 2026-10-12 구현 순서 논의

- 사용자 순서안: ① 의존성 · yaml ② ERD 회원 · 판매자 표 확인 ③ 구매자 복제본 ④ 판매자 복제본 ⑤ 대회 · 종목 엔티티 + ES ⑥ 등록 더미 HTML ⑦ CRUD ⑧ HTML 테스트 ⑨ 테스트 코드 (원문 번호가 2번이 두 개라 여기서 다시 붙임)
- Claude 의견: 주문 일정 때문에 정원 API를 앞당기자, 복제본은 세미에서 미루자, ES는 검색 단계로 미루자, 테스트는 단계마다 쓰자
- 결정
  - 정원: CRUD에서 함께 (정원 정보는 등록 · 수정에, 주문용 정보 API는 Read에 포함된다는 사용자 판단)
  - 복제본: 원안대로 만들고, Kafka로 회원가입 · 판매자 메시지를 받는다고 가정해 저장 기능을 3 · 4단계 뒤에 둔다(5단계). 메시지는 ERD 그대로 DTO로 가정하고 나중에 통일
  - 이름: ERD 이름 + common 상속
  - ES: 엔티티와 함께, 참고 자료는 사용자 제공

### 2026-10-12 작업 목록과 Linear 이슈

- shrimp-task-manager에 12개 작업으로 나눴다 (2단계는 완료라 뺌, 8단계는 등록 · 수정 / 조회 / 자리 API로 나눔).
- Linear 이슈 PRO-87~98을 만들었다. 1단계(PRO-88)는 In Progress로 현재 사이클에 넣었다.
- [제안] 두 가지는 채택하지 않음: 단계별 테스트(→ 10단계에서 모아 씀), 10/14 전 계약 공유(→ 10단계 뒤 사용자가 공유).
- 진행 흐름(사용자 결정): 브랜치 생성 → ROADMAP 수정 → 작업 → 커밋 → 이슈 정리. 뼈대는 1단계 작업(ES 설정)까지 마친 뒤 함께 커밋한다.
  - 푸시 · PR · Linear 상태 정리는 10단계까지 끝낸 뒤 한 번에 한다. 그동안 브랜치는 이슈마다 쌓는다 (dev ← `feat/PRO-88-product-skeleton` ← `feat/PRO-89-product-member` ← …).
- file-service의 `default_batch_fetch_size` 설정은 같은 브랜치에 커밋한다 (설정 변경이라서).

### 2026-10-12 3단계 구매자 복제본

- PRO-70이 dev에 머지돼 `feat/PRO-88-product-skeleton`을 최신 dev 위로 rebase했다. 충돌은 `settings.gradle.kts` · `backend/CLAUDE.md`의 모듈 목록 한 줄씩이었고, `jwt`와 `product-service`를 둘 다 남겼다.
- 이름은 `ProductMember`(테이블 `PRODUCT_MEMBER`). `MarketMember`는 테이블 접두사 규칙(`<도메인>_<엔티티>`)과 맞지 않아 고르지 않았다.
