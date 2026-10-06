# CLAUDE.md

이 파일은 모노레포의 `backend/` 폴더에서 작업하는 Claude Code(claude.ai/code)를 위한 가이드입니다. 아래 명령은 모두 `backend/`에서 실행합니다.
실행 환경(compose), 민감정보 암호화, Kafka 메시지, Swagger의 자세한 사용법은 `README.md`에 있습니다.

## 프로젝트 개요

크루런의 Spring Boot 백엔드입니다. 도메인별 모듈로 나눈 단일 앱(모듈러 모놀리스)이며, 현재 회원(`member`) 도메인만 있습니다.

- Java 25 (Gradle toolchain), Spring Boot 4.1.1, Gradle 9.7.1 (Kotlin DSL)
- 루트 패키지: `com.bukang` (`group`은 `com`)
- 운영에서는 Nginx가 HTTP 요청을 받아 `/api`를 이 앱으로 라우팅합니다. 별도 API 게이트웨이는 아직 없습니다.

## 빌드 및 실행 명령어

Windows에서는 `gradlew.bat`, Git Bash에서는 `./gradlew`를 사용합니다.

```bash
./gradlew build                 # 컴파일 + 테스트 + 패키징 (CI: .github/workflows/backend-ci.yml)
./gradlew bootRun               # 애플리케이션 실행 (dev 프로파일, compose.yml 인프라 자동 실행)
./gradlew test                  # 전체 테스트 (test 프로파일, H2)
./gradlew test --tests "com.bukang.BukangApplicationTests"            # 단일 테스트 클래스
./gradlew test --tests "com.bukang.BukangApplicationTests.contextLoads" # 단일 테스트 메서드
./gradlew checkstyleMain checkstyleTest                                # 컨벤션 검사 (경고만 출력)
./gradlew clean
```

## 패키지 구조 (`com.bukang`)

| 패키지 | 역할 |
|---|---|
| `boundedcontext/<도메인>/` | 도메인 모듈. `in`(컨트롤러, 초기 데이터), `app`(Facade, UseCase), `domain`(엔티티, 도메인 예외), `out`(Repository) |
| `shared/<도메인>/` | 다른 도메인과 함께 쓰는 것: DTO, 공통 예외, 엔티티 부모 클래스(`BaseMember`/`SourceMember`/`ReplicaMember`) |
| `global/` | 전역 설정: `config`(암호화), `security`, `jpa`(엔티티 부모), `exception`(`GlobalExceptionHandler`), `rsdata`(공통 응답), `json` |
| `standard/` | 프레임워크와 무관한 공통 인터페이스 |

- 다른 도메인은 `boundedcontext/member/app`의 Facade를 통해서만 회원 기능을 씁니다.
- 다른 도메인이 회원 정보를 가져야 하면 `ReplicaMember`를 상속한 엔티티에 Kafka 이벤트로 복제해 보관합니다.
  `BaseMember`(원본·복제본 공통)에는 공개 정보(username, nickname)만 두고, 비밀번호·이메일·휴대폰 번호는 `SourceMember`에만 둡니다.
  unique 제약은 원본 엔티티(`Member`)에만 겁니다.
- 도메인 예외는 `global/exception/BusinessException`을 상속하고 생성자에서 `HttpStatus`를 정합니다.
  `GlobalExceptionHandler`가 한 번에 처리하므로 도메인을 추가해도 핸들러는 고치지 않습니다.
- API 응답은 성공·실패 모두 `RsData {status, message, data}` 형식입니다.
- 엔티티 테이블 이름은 `<도메인>_<엔티티>` 형식입니다 (예: `MEMBER_MEMBER`).

## 의존성 구성 (build.gradle.kts)

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

- 프로파일: `dev`(기본, 로컬 MySQL 등 compose 인프라), `test`(H2, Kafka 리스너 꺼짐), `prod`(값은 `.env` 환경변수).
- dev/test의 암호화 키와 JWT 키는 개발용으로 yaml에 들어 있습니다. prod 키는 `.env`에만 두고 dev와 다른 값을 씁니다.
- 인증은 JWT + Redis(Stateless)가 목표지만, JWT 필터를 구현하기 전까지는 세션으로 로그인 상태를 유지합니다 (`global/security/WebConfig`).
- 개인정보(휴대폰 번호)는 AES로 암호화해 저장하고 해시 컬럼으로 조회합니다. 암호화한 필드로 직접 조회하지 않습니다 (README 참고).

## 코드 컨벤션

코드 컨벤션은 저장소 루트 `.claude/rules/`에 있습니다 (루트에서 Claude Code를 실행하면 자동으로 로드됨).

- `java-convention.md`: Java 코딩 컨벤션 (캠퍼스 핵데이) 및 Checkstyle 검사
- `git-commit-convention.md`: 커밋 메시지 규칙 (Conventional Commits 1.0.0)
- `swagger-convention.md`: 컨트롤러와 요청 DTO의 Swagger 애노테이션 규칙 (`in`, `dto` 패키지 파일 작업 시 로드)
- `redis-key-convention.md`: Redis 키 네이밍 규칙 (`{엔티티}:{ID}:{속성}`, 콜론 구분)
- `kafka-topic-convention.md`: Kafka 토픽 네이밍 규칙 (`<message-type>.<dataset-name>.<data-name>`, 계층은 `.`으로만 구분)

컨트롤러에 Swagger 문서를 작성하거나 보완할 때는 `/swagger-annotate` 스킬(루트 `.claude/skills/swagger-annotate`)을 사용합니다.
