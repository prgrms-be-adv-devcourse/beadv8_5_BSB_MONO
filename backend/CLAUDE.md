# CLAUDE.md

이 파일은 모노레포의 `backend/` 폴더에서 작업하는 Claude Code(claude.ai/code)를 위한 가이드입니다. 아래 명령은 모두 `backend/`에서 실행합니다.
실행 환경(compose), 민감정보 암호화, Kafka 메시지, Swagger의 자세한 사용법은 `README.md`에 있습니다.

## 프로젝트 개요

크루런의 Spring Boot 백엔드입니다. 하나의 Gradle 멀티 모듈 프로젝트(모노레포) 안에 서비스별 Spring Boot 앱을 둡니다 (MSA 전환 중).

| 모듈 | 종류 | 포트 | 역할 |
|---|---|---|---|
| `common` | 라이브러리(jar) | - | 모든 서비스가 함께 쓰는 코드 (응답 형식, 예외, JPA 부모 클래스, 서비스 간 이벤트) |
| `member-service` | Spring Boot 앱 | 8080 | 회원가입, 로그인, 인증 |
| `cash-service` | Spring Boot 앱 | 8081 | 회원 복제본(`CashMember`), 캐시 (뼈대만 있음) |
| `payout-service` | Spring Boot 앱 | 8082 | 정산 내역(`Payout`), 정산 항목, 지급보류 (엔티티만 있음) |
| `file-service` | Spring Boot 앱 | 8083 | 대회 이미지 등 파일 저장 (뼈대만 있음) |

인프라(`compose.yml`: MySQL, Kafka, Elasticsearch, Redis)는 모든 서비스가 공용으로 씁니다.

- Java 25 (Gradle toolchain), Spring Boot 4.1.1, Gradle 9.7.1 (Kotlin DSL)
- 루트 패키지: `com.bukang` (`group`은 `com`). 모듈별 패키지는 `com.bukang.common`, `com.bukang.member`, `com.bukang.cash`, `com.bukang.payout`, `com.bukang.file`
  (모듈 폴더 이름은 케밥 케이스 `<도메인>-service`, Java 패키지는 하이픈을 쓸 수 없으므로 도메인 한 단어)
- 공통 빌드 설정(Java 버전, Spring Boot BOM, Lombok, Checkstyle)은 루트 `build.gradle.kts`, 모듈별 의존성은 각 모듈의 `build.gradle.kts`에 있습니다.
- 운영에서는 Nginx가 HTTP 요청을 받아 `/api`를 이 앱으로 라우팅합니다. 별도 API 게이트웨이는 아직 없습니다.

## 빌드 및 실행 명령어

Windows에서는 `gradlew.bat`, Git Bash에서는 `./gradlew`를 사용합니다.

```bash
./gradlew build                          # 전체 모듈 컴파일 + 테스트 + 패키징 (CI: .github/workflows/backend-ci.yml)
./gradlew :member-service:bootRun        # 회원 서비스 실행 (dev 프로파일, compose.yml 인프라 자동 실행)
./gradlew :cash-service:bootRun          # 캐시 서비스 실행 (8081)
./gradlew :payout-service:bootRun        # 정산 서비스 실행 (8082)
./gradlew :file-service:bootRun          # 파일 서비스 실행 (8083)
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
├── settings.gradle.kts        include("common", "member-service", "cash-service", "payout-service", "file-service")
├── build.gradle.kts           모든 모듈 공통 설정
├── compose.yml                공용 인프라 (MySQL, Kafka, Elasticsearch, Redis) + 서비스별 앱 컨테이너
├── common/      com.bukang.common
│   ├── global/                기술 공통 (도메인 지식 없음)
│   │   ├── config/            LocalDevEnvironmentPostProcessor (compose.yml, .env 위치 탐색)
│   │   ├── exception/         BusinessException, GlobalExceptionHandler
│   │   ├── jpa/entity/        BaseEntity, BaseIdAndTime, BaseManualIdAndTime
│   │   ├── json/              JsonConverter
│   │   └── rsdata/            RsData (공통 응답)
│   ├── shared/                서비스 간 계약 (도메인 지식 있음)
│   │   └── member/
│   │       ├── domain/        BaseMember, ReplicaMember (공개 필드만)
│   │       └── event/         MemberJoinedEvent
│   └── standard/              프레임워크와 무관한 인터페이스
│       ├── modeltype/         HasModelTypeCode
│       └── resulttype/        ResultType
├── member-service/  com.bukang.member
│   ├── in/ app/ domain/ out/  컨트롤러 / Facade·UseCase / 엔티티·도메인 예외 / Repository
│   ├── domain/                SourceMember, Member, exception/
│   ├── security/              WebConfig, CustomUserDetailService, AuthExceptionHandler (JWT 발급 예정)
│   ├── config/crypto/         민감정보 암호화 (AES, 블라인드 인덱스)
│   └── dto/                   MemberDto, MemberJoinRequestDto, MemberLoginRequestDto, MemberSearchCondition
├── cash-service/    com.bukang.cash
│   └── domain/                CashMember extends ReplicaMember
├── payout-service/  com.bukang.payout
│   ├── domain/                Payout, PayoutItem, PayoutHold, 상태·사유 enum
│   └── out/                   PayoutRepository
└── file-service/    com.bukang.file
    └── FileApplication        (뼈대만 있음)
```

- `common`은 실행 앱이 아니라 jar 라이브러리입니다. 각 서비스는 `implementation(project(":common"))`으로 의존합니다.
  서비스의 `@SpringBootApplication(scanBasePackages = {"com.bukang.<서비스>", "com.bukang.common"})`로 common의 빈도 등록합니다.
- **서비스끼리는 서로 의존하지 않습니다.** 다른 서비스의 클래스가 필요하면 `common`에 올릴지(계약: 이벤트, 공개 필드) 먼저 검토합니다.
  `common`에는 특정 서비스 전용 코드(Spring Security, 회원 엔티티 등)를 넣지 않습니다.
- `common` 안에서 `global`(기술 공통)은 `shared`(서비스 간 계약)를 참조하지 않습니다. 의존 방향은 `shared` → `global` → `standard`입니다.
- 다른 서비스가 회원 정보를 가져야 하면 `ReplicaMember`를 상속한 엔티티에 Kafka 이벤트(`MemberJoinedEvent`)로 복제해 보관합니다.
  `BaseMember`(원본·복제본 공통)에는 공개 정보(username, nickname)만 두고, 비밀번호·이메일·휴대폰 번호는 `SourceMember`에만 둡니다.
  unique 제약은 원본 엔티티(`Member`)에만 겁니다.
- 도메인 예외는 `common`의 `global/exception/BusinessException`을 상속하고 생성자에서 `HttpStatus`를 정합니다.
  `GlobalExceptionHandler`가 한 번에 처리하므로 도메인을 추가해도 핸들러는 고치지 않습니다.
  Spring Security 예외는 `member-service`의 `AuthExceptionHandler`가 처리합니다.
- API 응답은 성공·실패 모두 `RsData {status, message, data}` 형식입니다.
- 엔티티 테이블 이름은 `<도메인>_<엔티티>` 형식입니다 (예: `MEMBER_MEMBER`, `CASH_MEMBER`). 지금은 모든 서비스가 같은 DB(`bukang-db`)를 쓰고 접두사로 구분합니다.

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
- 인증은 JWT + Redis(Stateless)가 목표지만, JWT 필터를 구현하기 전까지는 세션으로 로그인 상태를 유지합니다 (`member-service`의 `security/WebConfig`).
- 개인정보(휴대폰 번호)는 AES로 암호화해 저장하고 해시 컬럼으로 조회합니다. 암호화한 필드로 직접 조회하지 않습니다 (README 참고).

## 코드 컨벤션

코드 컨벤션은 저장소 루트 `.claude/rules/`에 있습니다 (루트에서 Claude Code를 실행하면 자동으로 로드됨).

- `java-convention.md`: Java 코딩 컨벤션 (캠퍼스 핵데이) 및 Checkstyle 검사
- `git-commit-convention.md`: 커밋 메시지 규칙 (Conventional Commits 1.0.0)
- `swagger-convention.md`: 컨트롤러와 요청 DTO의 Swagger 애노테이션 규칙 (`in`, `dto` 패키지 파일 작업 시 로드)
- `redis-key-convention.md`: Redis 키 네이밍 규칙 (`{엔티티}:{ID}:{속성}`, 콜론 구분)
- `kafka-topic-convention.md`: Kafka 토픽 네이밍 규칙 (`<message-type>.<dataset-name>.<data-name>`, 계층은 `.`으로만 구분)

컨트롤러에 Swagger 문서를 작성하거나 보완할 때는 `/swagger-annotate` 스킬(루트 `.claude/skills/swagger-annotate`)을 사용합니다.
