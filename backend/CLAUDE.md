# CLAUDE.md

이 파일은 모노레포의 `backend/` 폴더에서 작업하는 Claude Code(claude.ai/code)를 위한 가이드입니다. 아래 명령은 모두 `backend/`에서 실행합니다.
실행 환경(compose), 민감정보 암호화, Kafka 메시지, Swagger의 자세한 사용법은 `README.md`에 있습니다.

## 프로젝트 개요

크루런의 Spring Boot 백엔드입니다. 하나의 Gradle 멀티 모듈 프로젝트(모노레포) 안에 서비스별 Spring Boot 앱을 둡니다 (MSA 전환 중).

| 모듈 | 종류 | 포트 | 역할 |
|---|---|---|---|
| `common` | 라이브러리(jar) | - | 모든 서비스가 함께 쓰는 코드 (응답 형식, 예외, JPA 부모 클래스, 서비스 간 이벤트) |
| `jwt` | 라이브러리(jar) | - | JWT 발급·검증 (`JwtProvider`). 웹·JPA 의존성이 없어 게이트웨이(WebFlux)에서도 쓴다 |
| `gateway` | Spring Boot 앱 (WebFlux) | 8000 | API 게이트웨이. Access Token을 검사하고 `/api/v1/<도메인>/**`를 경로에 맞는 서비스로 넘긴다 (Spring Cloud Gateway) |
| `member-service` | Spring Boot 앱 | 8080 | 회원가입, 로그인, 인증 |
| `cash-service` | Spring Boot 앱 | 8081 | 회원 복제본(`CashMember`), 캐시 (뼈대만 있음) |
| `payout-service` | Spring Boot 앱 | 8082 | 정산 내역(`Payout`), 정산 항목, 지급보류 (엔티티만 있음) |
| `file-service` | Spring Boot 앱 | 8083 | 대회 이미지 등 파일 저장 (S3, presigned URL) |
| `order-service` | Spring Boot 앱 | 8084 | 참가 신청, 장바구니, 바로 결제 예매(자리 확보·결제 기한 15분), 주문 |
| `product-service` | Spring Boot 앱 | 8085 | 대회(상품), 종목(참가권), 정원 (뼈대만 있음) |
| `queue-service` | Spring Boot 앱 | 8086 | 예매 대기열 (뼈대만 있음) |

인프라(`compose.yml`: MySQL, Kafka, Elasticsearch, Redis, 로컬 S3 대체재 S3Mock)는 모든 서비스가 공용으로 씁니다.

- Java 25 (Gradle toolchain), Spring Boot 4.1.1, Gradle 9.7.1 (Kotlin DSL)
- 루트 패키지: `com.bukang` (`group`은 `com`). 모듈별 패키지는 `com.bukang.common`, `com.bukang.member`, `com.bukang.cash`, `com.bukang.payout`, `com.bukang.file`, `com.bukang.order`, `com.bukang.product`, `com.bukang.queue`
  (모듈 폴더 이름은 케밥 케이스 `<도메인>-service`, Java 패키지는 하이픈을 쓸 수 없으므로 도메인 한 단어)
- 공통 빌드 설정(Java 버전, Spring Boot BOM, Lombok, Checkstyle)은 루트 `build.gradle.kts`, 모듈별 의존성은 각 모듈의 `build.gradle.kts`에 있습니다.
- 운영에서는 Nginx가 HTTP 요청을 받아 `/api`를 `gateway`(8000)로, 나머지를 프론트(`web`, Next.js 3000)로 넘기고, 게이트웨이가 경로를 보고 각 서비스로 보냅니다.
  Nginx 라우팅은 `docker/nginx/routes.conf`(로컬 `default.conf`와 운영 `*.conf.template`이 include), 컨테이너는 `compose.yml`의 `nginx`·`web`입니다.
  운영(EC2)은 `compose.prod.yml`이 443과 Let's Encrypt 인증서(certbot 컨테이너)를 덧붙입니다. Nginx도 주소를 자르지 않고 `/internal/`은 404로 막습니다.
  게이트웨이는 주소 앞부분을 자르지 않고(StripPrefix 없음) `/internal/**`는 라우팅하지 않습니다. 새 서비스를 추가하면 `gateway`의 라우팅 규칙도 추가합니다.
  `gateway`는 WebFlux 기반이라 webmvc를 가져오는 `common`에 의존하지 않습니다.

## 빌드 및 실행 명령어

Windows에서는 `gradlew.bat`, Git Bash에서는 `./gradlew`를 사용합니다.

```bash
./gradlew build                          # 전체 모듈 컴파일 + 테스트 + 패키징 (CI: .github/workflows/backend-ci.yml)
./gradlew :gateway:bootRun               # 게이트웨이 실행 (8000, localhost:8080~8086로 라우팅)
./gradlew :member-service:bootRun        # 회원 서비스 실행 (dev 프로파일, compose.yml 인프라 자동 실행)
./gradlew :cash-service:bootRun          # 캐시 서비스 실행 (8081)
./gradlew :payout-service:bootRun        # 정산 서비스 실행 (8082)
./gradlew :file-service:bootRun          # 파일 서비스 실행 (8083)
./gradlew :order-service:bootRun         # 주문 서비스 실행 (8084)
./gradlew :product-service:bootRun       # 상품 서비스 실행 (8085)
./gradlew :queue-service:bootRun         # 대기열 서비스 실행 (8086)
./gradlew test                           # 전체 테스트 (test 프로파일, H2)
./gradlew :member-service:test           # 한 모듈만 테스트
./gradlew :member-service:test --tests "com.bukang.member.MemberApplicationTests"              # 단일 테스트 클래스
./gradlew :member-service:test --tests "com.bukang.member.MemberApplicationTests.contextLoads" # 단일 테스트 메서드
./gradlew checkstyleMain checkstyleTest  # 컨벤션 검사 (경고만 출력)
./gradlew clean
```

## 모듈 / 패키지 구조

```
backend/
├── settings.gradle.kts        include("common", "jwt", "gateway", "member-service", "cash-service", "payout-service", "file-service", "order-service", "product-service", "queue-service")
├── build.gradle.kts           모든 모듈 공통 설정
├── compose.yml                공용 인프라 (MySQL, Kafka, Elasticsearch, Redis) + 서비스별 앱 컨테이너 + nginx·web(프론트)
├── compose.prod.yml           운영(EC2) 덮어쓰기: Nginx HTTPS + certbot (인증서 자동 발급·갱신)
├── docker/nginx/              Nginx 설정 (routes.conf 라우팅: /api → gateway, 나머지 → web)
├── common/      com.bukang.common
│   ├── global/                기술 공통 (도메인 지식 없음)
│   │   ├── config/            LocalDevEnvironmentPostProcessor (compose.yml, .env 위치 탐색)
│   │   ├── crypto/            민감정보 암호화 (AES, 블라인드 인덱스). crypto.enabled: true인 서비스(member, cash)에서만 빈 등록
│   │   ├── exception/         BusinessException, GlobalExceptionHandler
│   │   ├── jpa/entity/        BaseEntity, BaseIdAndTime, BaseManualIdAndTime
│   │   ├── json/              JsonConverter
│   │   ├── rsdata/            RsData (공통 응답)
│   │   └── web/               IdempotencyKeyHeader (중복 요청 방지 키 헤더 이름·최대 길이·오류 문구)
│   ├── shared/                서비스 간 계약 (도메인 지식 있음)
│   │   ├── file/domain/       FileType·FileKind (파일 용도·종류), FileRef (파일 연결 대상, 엔티티로만 만듦)
│   │   └── member/
│   │       ├── domain/        BaseMember, ReplicaMember (공개 필드만)
│   │       ├── event/         MemberJoinedEvent
│   │       └── web/           MemberIdHeader (회원 ID 헤더 이름·오류 문구), @LoginMemberId·LoginMemberIdArgumentResolver (헤더 읽기·검사)
│   └── standard/              프레임워크와 무관한 인터페이스
│       ├── modeltype/         HasModelTypeCode
│       └── resulttype/        ResultType
├── jwt/         com.bukang.jwt     JwtProvider, JwtProperties(jwt.*), JwtAutoConfiguration(빈 자동 등록)
├── gateway/     com.bukang.gateway GatewayApplication, auth/(JwtAuthenticationFilter, PublicPaths). 라우팅 규칙·공개 주소(gateway.auth.*)는 application.yaml, 서비스 주소는 프로파일별 yaml(gateway.services.*)
├── member-service/  com.bukang.member
│   ├── in/ app/ domain/ out/  컨트롤러 / Facade·UseCase / 엔티티·도메인 예외 / Repository
│   ├── domain/                SourceMember, Member, exception/
│   ├── security/              WebConfig, CustomUserDetailService, AuthExceptionHandler (JWT 발급 예정)
│   └── dto/                   MemberDto, MemberJoinRequestDto, MemberLoginRequestDto, MemberSearchCondition
├── cash-service/    com.bukang.cash
│   ├── in/                    CashExceptionHandler (헤더 누락·형식 오류를 RsData로 응답)
│   └── domain/                CashMember extends ReplicaMember
├── payout-service/  com.bukang.payout
│   ├── domain/                Payout, PayoutItem, PayoutHold, 상태·사유 enum
│   └── out/                   PayoutRepository
├── file-service/    com.bukang.file
│   ├── in/                    ApiV1FileController(업로드 URL 발급·완료, 조회), InternalV1FileController(대상에 연결, 내부 전용),
│   │                          FileCleanupBatchJobConfig·FileCleanupScheduler(정리 배치, prod에서 매일 05:00)
│   ├── app/                   FileFacade, FileUploadUseCase, FileLinkUseCase, FileCleanupUseCase
│   ├── domain/                StoredFile(FILE_FILE), FileStatus, FileFormat, exception/
│   ├── config/                S3Properties(file.storage.s3), S3Config(S3Client, S3Presigner)
│   └── out/                   StoredFileRepository, storage/(FileStorage, S3FileStorage)
├── order-service/   com.bukang.order
│   ├── in/                    ApiV1ParticipationController, ApiV1CartController, ApiV1OrderController,
│   │                          InternalV1OrderController(정산용 판매 내역, 내부 전용 /internal/v1/order),
│   │                          OrderExpiryScheduler(1분마다 결제 기한 지난 건 정리, test 프로파일 제외),
│   │                          OrderExceptionHandler (헤더 누락·형식 오류를 RsData로 응답)
│   ├── app/                   ParticipationFacade·ParticipationApplyUseCase, CartFacade·CartUseCase·CartCheckoutUseCase(장바구니 결제),
│   │                          OrderFacade·OrderCreateUseCase·OrderPayUseCase·OrderCancelUseCase·OrderExpiryUseCase·
│   │                          OrderQueryUseCase, SoldEntryQueryUseCase
│   ├── domain/                Participation(ORDER_PARTICIPATION), Cart·CartItem(ORDER_CART·_ITEM),
│   │                          Order·OrderItem(ORDER_ORDER·_ITEM), Shipment·ShipmentItem(ORDER_SHIPMENT·_ITEM, 키트 배송),
│   │                          상태 enum, exception/
│   └── out/                   Repository, OrderQueryRepository(QueryDSL 내 주문 목록),
│                              race/(RaceCoursePort 종목 정보), seat/(SeatHoldPort 자리 확보·판매·복구·해제),
│                              wallet/(WalletPort 예치금 결제·환원·환불), payout/(RefundEligibilityPort 환불 가능 여부)
│                              상품(B)·예치금(D)은 연결 전이라 Fake*Adapter가 가짜 응답을 준다. 정산(E)은 order.clients.payout.mode가
│                              http면 HttpRefundEligibilityAdapter(RestClient, 2초 제한, 실패 시 503)로 부른다 (prod=http, 그 밖=fake)
├── product-service/ com.bukang.product
│   └── ProductApplication     (뼈대만 있음)
└── queue-service/   com.bukang.queue
    ├── QueueApplication       (뼈대만 있음)
    └── in/                    QueueExceptionHandler (헤더 누락·형식 오류를 RsData로 응답)
```

- 파일은 브라우저가 presigned PUT으로 S3 `{환경}/uploads/`에 직접 올리고, 완료 API가 `{환경}/files/`로 옮긴 뒤 형식·크기를 확인한다 (README "파일 저장소" 참고).
  `{환경}`은 `file.storage.s3.key-prefix`로, 프로파일별 yaml에 적는다(dev=`dev`, prod=`prod`, 테스트=`test`). 로컬과 운영이 같은 버킷을 써도 경로가 나뉜다.
- 다른 서비스는 파일을 파일 ID로만 참조한다. 대상(대회 등)을 저장할 때 `/internal/v1/file/refs/{refType}/{refId}/files`를 동기 호출해 연결하고, `/internal/**`은 게이트웨이에 노출하지 않는다.
- 회원 ID는 `X-Member-Id` 헤더로 받는다. 컨트롤러는 헤더를 직접 읽지 않고 `@LoginMemberId int memberId`로 받는다. 헤더가 없으면 401 `LOGIN_REQUIRED`, 숫자가 아니거나 0 이하면 400 `INVALID_REQUEST`로 common이 응답한다(`shared/member/web`). 게이트웨이의 `auth/JwtAuthenticationFilter`가 클라이언트가 보낸 이 헤더를 지우고, Access Token을 검사해 토큰 속 회원 ID로 다시 넣는다.
  토큰 없이 통과하는 주소는 `gateway`의 `application.yaml` `gateway.auth.public-paths`에 있다. 로그인 전에 쓰는 API를 추가하면 여기에도 추가한다(빠뜨리면 401로 막힌다).
  서비스는 이 헤더를 그대로 믿으므로, `compose.yml`은 서비스 포트를 호스트에 열지 않는다. 서비스를 직접 부르며 확인할 때는 `bootRun`(dev)으로 띄운 서비스 포트(예: 8084)로 부른다.
- 두 번 처리하면 안 되는 요청(대회 등록, 주문 생성, 장바구니 결제, 예치금 환급 신청)은 중복 요청 방지 키를 `Idempotency-Key` 헤더로 받는다.
  헤더 이름·최대 길이(64)·오류 문구는 common의 `global/web/IdempotencyKeyHeader`를 쓴다.
- 대상에 붙지 않은 파일(PENDING·UPLOADED·DELETED)은 마지막으로 바뀐 뒤 1일이 지나면 Spring Batch 정리 배치가 DB 행을 지우고 커밋한 뒤 S3 객체를 지운다.
  실행 기록은 DB의 `BATCH_` 테이블에 남는다 (`file-service/BATCH-ROADMAP.md`).

- `common`은 실행 앱이 아니라 jar 라이브러리입니다. 각 서비스는 `implementation(project(":common"))`으로 의존합니다.
  서비스의 `@SpringBootApplication(scanBasePackages = {"com.bukang.<서비스>", "com.bukang.common"})`로 common의 빈도 등록합니다.
- **서비스끼리는 서로 의존하지 않습니다.** 다른 서비스의 클래스가 필요하면 `common`에 올릴지(계약: 이벤트, 공개 필드) 먼저 검토합니다.
  `common`에는 특정 서비스 전용 코드(Spring Security 인증·필터, 회원 엔티티 등)를 넣지 않습니다.
  단, 암호화 유틸(`spring-security-crypto`)은 `global/crypto`에 두고, yaml에 `crypto.enabled: true`를 넣은 서비스에서만 빈을 만듭니다(`@ConditionalOnProperty`).
- `common` 안에서 `global`(기술 공통)은 `shared`(서비스 간 계약)를 참조하지 않습니다. 의존 방향은 `shared` → `global` → `standard`입니다.
- 다른 서비스가 회원 정보를 가져야 하면 `ReplicaMember`를 상속한 엔티티에 Kafka 이벤트(`MemberJoinedEvent`)로 복제해 보관합니다.
  `BaseMember`(원본·복제본 공통)에는 공개 정보(nickname)만 두고, 비밀번호·이메일·실명·휴대폰 번호·생년월일은 `SourceMember`에만 둡니다.
  unique 제약은 원본 엔티티(`Member`)에만 겁니다.
- 도메인 예외는 `common`의 `global/exception/BusinessException`을 상속하고 생성자에서 `HttpStatus`를 정합니다.
  `GlobalExceptionHandler`가 한 번에 처리하므로 도메인을 추가해도 핸들러는 고치지 않습니다.
  Spring Security 예외는 `member-service`의 `AuthExceptionHandler`가 처리합니다.
- API 응답은 성공·실패 모두 `RsData {status, code, message, data}` 형식입니다.
  `code`는 오류 응답에 붙는 오류 코드입니다 (예: `MEMBER_DUPLICATE_EMAIL`, 입력값 오류는 `INVALID_REQUEST`, 예상 못 한 오류는 500 `INTERNAL_ERROR`).
  코드가 없으면 JSON에서 `code` 칸이 빠집니다.
  도메인 예외에 코드를 붙이려면 `BusinessException(status, code, message)` 생성자를 쓰고, 코드 이름은 `도메인_상황` 대문자 스네이크로 짓습니다.
- 엔티티 테이블 이름은 `<서비스>_<엔티티>` 형식입니다 (예: `MEMBER_MEMBER`, `CASH_MEMBER`, `ORDER_CART`).
  접두사는 서비스 모듈 이름(`member`, `cash`, `payout`, `file`, `order`, `product`, `queue`)을 대문자로 씁니다.
  지금은 모든 서비스가 같은 DB(`bukang-db`)를 쓰고 접두사로 구분합니다.
  테이블 이름 규칙(naming strategy)을 따로 두지 않아 실제 DB에는 소문자(`member_member`)로 만들어지므로, SQL을 직접 쓸 때는 소문자로 씁니다.
  규칙과 다른 기존 테이블(상품의 `race`, `race_course`, `participation_hold`)은 서비스별로 DB를 나눌 때(최종, Flyway) 맞춥니다.
  테이블 이름을 바꿀 때는 Flyway가 없는 서비스라면 배포 전에 서버 DB에서 `RENAME TABLE`을 먼저 실행합니다.
  compose가 `ddl-auto: update`로 띄워서, 이름만 바꿔 배포하면 빈 새 테이블이 생기고 기존 데이터는 옛 테이블에 남습니다.
- 표 변경은 Flyway SQL로 합니다. 지금 Flyway를 쓰는 서비스는 `member-service`, `order-service`이고, 나머지는 compose가 `ddl-auto: update`로 띄웁니다.
  - Flyway 서비스: 엔티티를 고치면 같은 커밋에 `src/main/resources/db/migration/V<날짜시각>__설명.sql`을 더합니다. 이미 돈 SQL 파일은 고치지 않습니다.
    dev·prod는 `ddl-auto: validate`라 SQL을 빠뜨리면 앱이 뜨지 않습니다. `Flyway*ValidateTest`가 빈 DB에 SQL을 모두 돌린 뒤 칸 이름·타입을
    엔티티와 맞춰 보므로, 칸을 빠뜨리면 테스트에서 먼저 실패합니다(길이·not null·인덱스·제약은 Hibernate validate가 보지 않으니 리뷰로 봅니다).
    같은 DB를 함께 쓰므로 기록 표 이름을 서비스마다 다르게 둡니다(`flyway_schema_history_<서비스>`). 설정은 `member-service`의 `application-dev.yaml`을 따릅니다.
  - `order-service`는 전환 중입니다(BUK-29). V1은 `create table if not exists`라 표가 이미 있으면 통째로 건너뛰어, 지난 배포 뒤 엔티티에 더한 칸이
    서버·로컬 표에 없으면 `validate`로는 뜨지 않습니다. 그래서 서버에 한 번 배포할 때까지 dev와 compose는 `update`로 둡니다
    (Flyway가 먼저 돌고 Hibernate가 빠진 칸을 채움). 그 뒤 dev는 `validate`로, compose의 `order-service` 줄은 지웁니다.
  - **다른 서비스도 Flyway를 넣기를 권합니다.** `update`는 표·칸을 더하기만 해서 이름 바꾸기·칸 지우기·제약 바꾸기·데이터 옮기기를 못 하고,
    그때마다 서버 DB에서 SQL을 손으로 돌려야 합니다(주문 BUK-6). 또 서비스별로 DB를 나눌 때(최종) Flyway가 있으면 새 DB에 SQL을 처음부터 돌려
    표를 그대로 만들고 접속 주소만 바꾸면 되지만, 없으면 표 구조를 손으로 옮겨야 합니다.
    넣는 방법: 의존성 2줄(`spring-boot-starter-flyway`, `flyway-mysql`), 지금 표를 `create table if not exists`로 적은 `V1__baseline.sql`
    (인덱스·제약은 create table 안에. 밖에 두면 표가 이미 있는 DB에서 "이미 있음"으로 실패), dev·prod의 Flyway 설정, 테스트의
    `flyway.enabled: false`, `Flyway*ValidateTest`·`FlywayExistingTablesTest`. 서버 표에 빠진 칸이 있을 수 있으니 첫 배포는 `update`로 두고,
    한 번 배포한 뒤 `validate`로 바꾸고 compose에서 그 서비스의 `SPRING_JPA_HIBERNATE_DDL_AUTO` 줄을 지웁니다. `order-service`(BUK-29)가 본보기입니다.

## 의존성 구성 (`member-service/build.gradle.kts`)

Spring Boot 4의 모듈화된 스타터를 사용합니다. 테스트 지원도 스타터별 `*-test` 모듈로 나뉘어 있습니다.

| 영역 | 의존성 |
|---|---|
| Web | `spring-boot-starter-webmvc`, `spring-boot-starter-validation`, springdoc(Swagger) |
| 보안 | `spring-boot-starter-security`, jjwt 0.13 (JWT) |
| RDB | `spring-boot-starter-data-jpa` + MySQL 드라이버, QueryDSL, H2(테스트) |
| 검색 | `spring-boot-starter-data-elasticsearch` |
| 메시징 | `spring-boot-starter-kafka`, `kafka-streams` |
| 캐시 | `spring-boot-starter-data-redis` |
| 기타 | Lombok, devtools, `spring-boot-docker-compose`(개발 실행 시 인프라 자동 실행), Testcontainers |

## 설정 관련 주의사항

- `backend/.env`와 `backend/compose.yml`은 `common`의 `global/config/LocalDevEnvironmentPostProcessor`가 실행 위치(working directory)와 상관없이 찾아 적용합니다.
- 프로파일: `dev`(기본, 로컬 MySQL 등 compose 인프라), `test`(H2, Kafka 리스너 꺼짐), `prod`(값은 `.env` 환경변수).
- dev/test의 암호화 키와 JWT 키는 개발용으로 yaml에 들어 있습니다. prod 키는 `.env`에만 두고 dev와 다른 값을 씁니다.
- 인증은 JWT + Redis(Stateless)입니다. Access Token은 게이트웨이가 검사하고, 서비스는 게이트웨이가 넣어 준 `X-Member-Id` 헤더로 회원을 알아봅니다. 서비스는 세션을 만들지 않습니다 (`member-service`의 `security/WebConfig`).
- 개인정보(휴대폰 번호)는 AES로 암호화해 저장하고 해시 컬럼으로 조회합니다. 암호화한 필드로 직접 조회하지 않습니다 (README 참고).

## 코드 컨벤션

코드 컨벤션은 저장소 루트 `.claude/rules/`에 있습니다 (루트에서 Claude Code를 실행하면 자동으로 로드됨).

- `java-convention.md`: Java 코딩 컨벤션 (캠퍼스 핵데이) 및 Checkstyle 검사
- `git-commit-convention.md`: 커밋 메시지 규칙 (Conventional Commits 1.0.0)
- `swagger-convention.md`: 컨트롤러와 요청 DTO의 Swagger 애노테이션 규칙 (`in`, `dto` 패키지 파일 작업 시 로드)
- `redis-key-convention.md`: Redis 키 네이밍 규칙 (`{엔티티}:{ID}:{속성}`, 콜론 구분)
- `kafka-topic-convention.md`: Kafka 토픽 네이밍 규칙 (`<message-type>.<dataset-name>.<data-name>`, 계층은 `.`으로만 구분)

컨트롤러에 Swagger 문서를 작성하거나 보완할 때는 `/swagger-annotate` 스킬(루트 `.claude/skills/swagger-annotate`)을 사용합니다.
