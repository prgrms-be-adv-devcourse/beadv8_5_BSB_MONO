# backend

크루런의 Spring Boot 백엔드입니다. Gradle 멀티 모듈 하나에 서비스별 Spring Boot 앱이 있습니다. 명령은 모두 `backend/`에서 실행합니다.
실행 환경(compose), 민감정보 암호화, Kafka 메시지, Swagger, 파일 저장소, 크루런 AI의 자세한 사용법은 `README.md`에 있습니다.
코드 컨벤션은 저장소 루트 `.claude/rules/`에 있습니다. Claude Code는 자동으로 읽고, 다른 도구는 작업 전에 직접 읽습니다.

## 모듈

| 모듈 | 포트 | 역할 |
|---|---|---|
| `common` | - | 모든 서비스가 함께 쓰는 jar (응답 형식, 예외, JPA 부모 클래스, 서비스 간 이벤트) |
| `jwt` | - | JWT 발급·검증 jar. 웹·JPA 의존성이 없어 게이트웨이에서도 쓴다 |
| `observability-dev` | - | dev bootRun에서만 로그를 OTLP로 보내는 jar. 운영 jar와 테스트에는 없다 |
| `gateway` | 8000 | API 게이트웨이(WebFlux). Access Token을 검사하고 `/api/v1/<도메인>/**`를 서비스로 넘긴다 |
| `member-service` | 8080 | 회원가입, 로그인, 인증 |
| `cash-service` | 8081 | 예치금 |
| `payout-service` | 8082 | 정산 |
| `file-service` | 8083 | 파일 저장 (S3 presigned URL, 정리 배치) |
| `order-service` | 8084 | 참가 신청, 장바구니, 주문·결제 |
| `product-service` | 8085 | 대회, 종목, 정원 |
| `queue-service` | 8086 | 예매 대기열 |
| `notification-service` | 8087 | 내 알림 |
| `inquiry-service` | 8088 | 대회 문의 |
| `board-service` | 8089 | 커뮤니티 게시판 |
| `ai-service` | 8092 | 판매자·관리자 챗봇 「크루런 AI」. DB 없음, 대화 기록은 Redis |

- Java 25, Spring Boot 4.1.1, Gradle 9.7.1 (Kotlin DSL). 패키지는 `com.bukang.<도메인 한 단어>`입니다.
- 공통 빌드 설정은 루트 `build.gradle.kts`, 모듈별 의존성은 각 모듈의 `build.gradle.kts`에 있습니다.
- 운영은 k3s입니다(`infra/README.md`, `k8s/`, 배포 `.github/workflows/k8s-cd.yml`). 요청은 Traefik → nginx(`docker/nginx/routes.conf`) → `gateway` → 서비스 순서로 갑니다.
- 새 서비스를 더하면 `gateway`의 라우팅 규칙도 더합니다. 게이트웨이와 Nginx는 `/internal/**`를 밖에 열지 않습니다.

## 명령어

```bash
./gradlew build                          # 전체 컴파일 + 테스트 + 패키징 (CI: .github/workflows/backend-ci.yml)
./gradlew :member-service:bootRun        # 서비스 하나 실행 (dev 프로파일, compose.yml 인프라 자동 실행)
./gradlew :member-service:test           # 한 모듈만 테스트 (test 프로파일, H2)
./gradlew :member-service:test --tests "com.bukang.member.MemberApplicationTests"
./gradlew checkstyleMain checkstyleTest  # 컨벤션 검사. 경고만 출력하므로 출력을 직접 확인한다
```

Testcontainers를 쓰는 테스트(cash의 Flyway, order의 `@MySqlContainerTest`, ai-service의 Redis)는 Docker가 필요합니다.

## 서비스 경계

- **서비스끼리는 서로 의존하지 않습니다.** 다른 서비스의 클래스가 필요하면 `common`에 계약(이벤트, 공개 필드)으로 올릴지 먼저 검토합니다.
- `common`에는 특정 서비스 전용 코드(Spring Security 인증·필터, 회원 엔티티 등)를 넣지 않습니다.
- `common` 안의 의존 방향은 `shared`(서비스 간 계약) → `global`(기술 공통) → `standard`입니다. `global`은 `shared`를 참조하지 않습니다.
- 서비스는 `@SpringBootApplication(scanBasePackages = {"com.bukang.<서비스>", "com.bukang.common"})`로 common 빈을 등록합니다.
  DB가 없는 `ai-service`만 필요한 패키지만 고릅니다. common에 모든 서비스가 써야 하는 빈을 새 패키지로 더하면 `AiApplication`에도 더합니다.
- `gateway`는 WebFlux라 webmvc를 가져오는 `common`에 의존하지 않습니다.
- 다른 서비스가 회원 정보를 가져야 하면 `ReplicaMember`를 상속한 엔티티에 Kafka 이벤트(`MemberJoinedEvent`)로 복제합니다.
  공개 정보(nickname)만 복제하고, 비밀번호·이메일·실명·휴대폰 번호·생년월일은 `SourceMember`에만 둡니다. unique 제약은 원본(`Member`)에만 겁니다.
- 다른 서비스는 파일을 파일 ID로만 참조합니다. 파일 연결은 아웃박스 → Kafka(`race.filelink.requested`)로 보냅니다.
- 다른 서비스 테이블을 읽는 SQL은 쓰지 않습니다. 로컬은 DB 하나를 함께 써서 돌지만, 운영은 서비스마다 DB가 달라 실패합니다.

## 인증과 요청

- 회원 ID는 게이트웨이가 Access Token에서 꺼내 `X-Member-Id` 헤더로 넣습니다. 클라이언트가 보낸 같은 헤더는 게이트웨이가 지웁니다.
- 컨트롤러는 헤더를 직접 읽지 않고 `@LoginMemberId long memberId`로 받습니다(int·Integer 금지). 헤더가 없으면 401 `LOGIN_REQUIRED`, 잘못된 값이면 400 `INVALID_REQUEST`입니다.
- 서비스는 이 헤더를 그대로 믿으므로 `compose.yml`은 서비스 포트를 호스트에 열지 않습니다. 직접 부를 때는 bootRun으로 띄운 포트로 부릅니다.
- 로그인 없이 쓰는 API를 더하면 `gateway`의 `application.yaml` `gateway.auth.public-paths`에도 더합니다. 빠뜨리면 401로 막힙니다.
- 두 번 처리하면 안 되는 요청(대회 등록, 주문 생성, 장바구니 결제, 예치금 환급 신청)은 `Idempotency-Key` 헤더를 받습니다. 이름·최대 길이(64)·오류 문구는 common의 `IdempotencyKeyHeader`를 씁니다.
- 서비스는 세션을 만들지 않습니다. 휴대폰 번호는 AES로 암호화해 저장하고 해시 컬럼으로 조회합니다. 암호화한 필드로 직접 조회하지 않습니다.

## 응답과 예외

- API 응답은 성공·실패 모두 `RsData {status, code, message, data}`입니다. 오류 코드가 없으면 JSON에서 `code`가 빠집니다.
- 도메인 예외는 common의 `BusinessException`을 상속하고 생성자에서 `HttpStatus`를 정합니다. `GlobalExceptionHandler`가 한 번에 처리하므로 핸들러는 고치지 않습니다.
- 오류 코드는 `BusinessException(status, code, message)`로 붙이고 `도메인_상황` 대문자 스네이크로 짓습니다(예: `MEMBER_DUPLICATE_EMAIL`).

## 이벤트

- 알림은 트랜잭션 안에서 `eventPublisher.publish(NotificationRequestedEvent.of(회원 ID, NotificationKind.*, 제목, 내용, 링크))` 한 줄로 보냅니다.
  커밋된 뒤에만 Kafka로 가고 실패하면 로그만 남깁니다(아웃박스 없음). AFTER_COMMIT 리스너 안에서 부르면 보내지지 않습니다.
- 토픽 이름은 `.claude/rules/kafka-topic-convention.md`를 따르고, 상수는 common의 `KafkaTopics`에 모읍니다.

## DB와 Flyway

- 테이블 이름은 `<서비스>_<엔티티>`입니다(예: `MEMBER_MEMBER`, `ORDER_CART`). 실제 DB에는 소문자로 만들어지므로 SQL은 소문자로 씁니다.
- 로컬(dev bootRun, compose, k3d)은 모든 서비스가 `bukang-db` 하나를 씁니다. 운영은 서비스마다 `crewrun_<서비스>` DB를 씁니다.
  서비스를 더하면 `docker/mysql/initdb/service-dbs.sh` 목록과 그 서비스의 `application-prod.yaml` 기본값을 함께 고칩니다.
- DB를 쓰는 모든 서비스가 Flyway를 씁니다. 엔티티를 고치면 같은 커밋에 `src/main/resources/db/migration/V<날짜시각>__설명.sql`을 더합니다.
  이미 돈 SQL 파일은 고치지 않습니다. 다른 PR과 버전 번호가 겹치지 않는지 확인합니다.
- dev·prod는 `ddl-auto: validate`라 SQL을 빠뜨리면 앱이 뜨지 않습니다. `Flyway*ValidateTest`가 칸 이름·타입을 먼저 확인합니다.
  길이·not null·인덱스·제약은 확인하지 않으므로 리뷰로 봅니다.
- `payout-service`·`cash-service`의 enum 칸은 DB ENUM이라 enum 값을 더할 때 ENUM 목록을 바꾸는 SQL도 필요합니다.
- `file-service`의 Spring Batch 기록 테이블(`BATCH_`)은 Flyway가 아니라 `spring.batch.jdbc.initialize-schema`가 만듭니다.

## ai-service

- 코드는 Spring AI의 `ChatClient`만 씁니다. 모델 회사는 설정 `spring.ai.model.chat`이 정합니다(prod `openai`, dev·test `ollama`).
- `ChatMemory`를 빈으로 등록하지 않습니다. 등록하면 Redis 대화 기록 대신 메모리 저장소로 바뀝니다.
- Redis 저장소의 `metadata-fields`를 비우지 않습니다. 비우면 OpenAI 답이 대화 기록에서 빠집니다. 인덱스 모양을 바꾸면 `index-name`도 바꿉니다.
- 도구(`@Tool`)는 화면별 묶음(`SellerTools`, `AdminTools`)에 두고 인자를 받지 않습니다. 회원 번호는 `ToolContext`의 `AiToolTrace`에서 꺼냅니다.
- 도구는 개인정보(구매자 이름·배송지·신청자 이름)를 모델에 보내지 않습니다. 도구를 더하면 `AiSource`와 `AiApiExamples`도 함께 고칩니다.

## 설정

- 프로파일은 `dev`(기본, compose 인프라), `test`(H2, Kafka 리스너 꺼짐), `prod`(값은 환경변수)입니다.
- `backend/.env`와 `compose.yml`은 common의 `LocalDevEnvironmentPostProcessor`가 실행 위치와 상관없이 찾습니다.
- dev도 키를 `.env`에서 읽습니다(`JWT_SECRET`, `CRYPTO_*`, `MARKETING_UNSUBSCRIBE_KEY`, `INTERNAL_API_KEY`). 값이 없으면 서버가 뜨지 않습니다. test 키만 yaml에 있습니다.
- 컨트롤러에 Swagger 문서를 쓸 때는 `/swagger-annotate` 스킬을 씁니다.
