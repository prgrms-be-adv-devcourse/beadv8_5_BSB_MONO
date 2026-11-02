# backend

Spring Boot 4.1 / Java 25 백엔드입니다. 명령은 이 폴더(`backend/`)에서 실행합니다.

Gradle 멀티 모듈로 `common`·`jwt`(공통 라이브러리), `gateway`(API 게이트웨이, 8000), `member-service`(8080), `cash-service`(8081), `payout-service`(8082), `file-service`(8083), `order-service`(8084), `product-service`(8085), `queue-service`(8086)가 있습니다. 구조는 [`CLAUDE.md`](CLAUDE.md)를 참고합니다.

## 실행 환경

### 컨테이너 구성 (`compose.yml`)

| 서비스 | 용도 | 주소 |
|---|---|---|
| `bukang-db` | MySQL 8.4 | `localhost:3306` |
| `bukang-kafka` | Kafka 호환 브로커 (Redpanda) | `localhost:9092` |
| `bukang-kafka-console` | Kafka 관리 화면 (Redpanda Console) | http://localhost:8091 |
| `bukang-elasticsearch` | Elasticsearch 9.4.5 + nori 한글 분석기 | `localhost:9200` |
| `bukang-elasticvue` | Elasticsearch 관리 화면 (Elasticvue) | http://localhost:8090 |
| `bukang-redis` | Redis 8.2 | `localhost:6379` |
| `bukang-s3mock` | 로컬 S3 대체재 (Adobe S3Mock, 버킷 `bukang-file`) | http://localhost:9090 |
| `nginx` | 입구 (`/api`는 `gateway`, 나머지는 `web`으로 넘김, 설정 `docker/nginx/default.conf`) | http://localhost |
| `web` | 프론트엔드 Next.js (`../frontend` 이미지, nginx를 거쳐서만 접속) | - |
| `gateway` | API 게이트웨이 (prod 프로파일, `/api` 요청을 각 서비스로 넘김) | http://localhost:8000 |
| `member-service` | 회원 서비스 (prod 프로파일) | 호스트에 열지 않음 (`bukang-member-service:8080`) |
| `cash-service` | 캐시 서비스 (prod 프로파일) | 호스트에 열지 않음 (`bukang-cash-service:8081`) |
| `payout-service` | 정산 서비스 (prod 프로파일) | 호스트에 열지 않음 (`bukang-payout-service:8082`) |
| `file-service` | 파일 서비스 (prod 프로파일) | 호스트에 열지 않음 (`bukang-file-service:8083`) |
| `order-service` | 주문 서비스 (prod 프로파일) | 호스트에 열지 않음 (`bukang-order-service:8084`) |
| `product-service` | 상품 서비스 (prod 프로파일) | 호스트에 열지 않음 (`bukang-product-service:8085`) |
| `queue-service` | 대기열 서비스 (prod 프로파일) | 호스트에 열지 않음 (`bukang-queue-service:8086`) |

앱 서비스(`nginx`, `web`, `gateway`, `member-service`, `cash-service`, `payout-service`, `file-service`, `order-service`, `product-service`, `queue-service`)는 `app` 프로파일로 분리되어 있어서 `--profile app`을 붙일 때만 실행됩니다.
서비스 7개는 호스트에 포트를 열지 않고 `gateway`를 거쳐서만 부를 수 있습니다. 서비스는 `X-Member-Id` 헤더를 로그인한 회원으로 믿기 때문에,
서비스 포트로 직접 오면 헤더를 위조할 수 있어서입니다. 서비스를 직접 부르며 확인하려면 컨테이너 대신 `bootRun`으로 띄웁니다.
시작 순서는 서비스 7개 → `gateway` → `web` → `nginx`입니다. 앞 컨테이너가 healthy(포트가 열려 요청을 받을 수 있는 상태)가 된 뒤에 다음 컨테이너가 시작합니다.

### 개발 실행 (앱 컨테이너 제외)

IDE나 `./gradlew :member-service:bootRun`(또는 `:cash-service:bootRun`, `:payout-service:bootRun`, `:file-service:bootRun`, `:order-service:bootRun`, `:product-service:bootRun`, `:queue-service:bootRun`)으로 서비스를 실행하면 Spring Boot가 `compose.yml`의 인프라 컨테이너를 자동으로 띄웁니다. 이미 실행 중이면 그대로 사용합니다.
게이트웨이(`./gradlew :gateway:bootRun`, 8000)는 인프라를 쓰지 않으므로 컨테이너를 띄우지 않고, `localhost:8080~8086`의 서비스로 요청을 넘깁니다.
여러 서비스가 같은 인프라를 쓰므로 서비스를 종료해도 컨테이너는 멈추지 않습니다(`lifecycle-management: start-only`). 정리할 때는 `docker compose down`을 실행합니다.

IntelliJ에서는 `MemberApplication`, `CashApplication`, `PayoutApplication`, `FileApplication`, `OrderApplication`, `ProductApplication`, `QueueApplication`을 바로 실행하면 됩니다. 실행 위치(working directory)가 저장소 루트, `backend/`, 모듈 폴더 중 어디여도
`common`의 `global/config/LocalDevEnvironmentPostProcessor`가 `backend/compose.yml`과 `backend/.env`를 찾아 적용합니다.

`docker/elasticsearch/Dockerfile`을 수정했다면 `docker compose up -d --build`로 이미지를 다시 만듭니다.

### 운영(prod) 앱 컨테이너 실행

앱을 운영용 이미지로 빌드해서 인프라와 함께 컨테이너로 실행합니다.

1. `.env.example`을 복사해 `.env`를 만들고 값을 채웁니다. 접속 정보는 `compose.yml`의 `bukang-db` 설정과 맞춥니다.

   ```bash
   cp .env.example .env
   ```

   | 변수 | 값 |
   |---|---|
   | `CRYPTO_HMAC_KEY` | `openssl rand -base64 32`로 생성 (아래 [민감정보 암호화](#민감정보-암호화) 참고) |
   | `CRYPTO_PASSWORD` | `openssl rand -base64 32`로 생성 (암호화를 켠 모든 서비스가 같은 키를 씀) |
   | `CRYPTO_SALT` | `openssl rand -hex 8`로 생성 |
   | `MYSQL_USER` / `MYSQL_PASS` / `MYSQL_DB` | `compose.yml`의 `bukang-db` 값과 동일 |
   | `JWT_SECRET` | 32자 이상의 임의 문자열 |

2. IDE나 `bootRun`으로 실행 중인 게이트웨이가 있으면 종료합니다. 같은 포트(8000)를 사용합니다. 서비스 컨테이너는 호스트 포트를 쓰지 않아 `bootRun` 서비스와 겹치지 않습니다.

3. 앱 이미지를 빌드하고 전체 컨테이너를 실행합니다.

   ```bash
   docker compose --profile app up -d --build   # 서비스 + 인프라 실행 (인프라가 healthy가 된 뒤 서비스 시작)
   docker compose logs -f member-service        # 로그 확인 ("Started MemberApplication"이면 성공)
   docker compose --profile app ps              # 상태 확인 (서비스·gateway가 healthy, nginx가 Up이면 http://localhost 접속)
   docker compose --profile app down            # 앱 포함 전체 중지 + 삭제
   ```

앱 컨테이너는 Docker 내부 서비스 이름(`bukang-db`, `bukang-kafka:29092` 등)으로 인프라에 접속하므로, `.env`의 주소에는 `localhost` 대신 서비스 이름을 사용합니다. 운영 프로파일에서는 Swagger가 비활성화됩니다.

#### 빈 DB에서 표가 만들어지는 방법

모든 서비스의 `application-prod.yaml`은 `ddl-auto: validate`(표를 만들지 않고 엔티티와 맞는지만 확인)입니다.

- `member-service`는 Flyway(`db/migration`, 기록 표 `flyway_schema_history_member`)가 먼저 표를 만들고 validate로 확인합니다.
- `payout-service`도 같은 방식입니다(기록 표 `flyway_schema_history_payout`, PR #265). V1은 2026-11-02 운영 표 그대로이고,
  이후 SQL이 ID를 bigint로, enum 칸을 DB ENUM으로 맞춥니다. enum 값을 더하면 ENUM 목록을 바꾸는 SQL도 같이 넣습니다(`PayoutEnumColumnTest`가 확인).
  로컬에서 `payout-service`가 `Schema-validation`으로 뜨지 않으면 로컬 DB의 `payout_*` 표를 지우고 다시 띄웁니다.
- `cash`·`file`·`order`·`product`는 아직 Flyway가 없습니다. 그래서 `compose.yml`이 이 네 서비스에만 `SPRING_JPA_HIBERNATE_DDL_AUTO=update`를 넘겨,
  dev처럼 Hibernate가 없는 표·칼럼을 만들게 합니다(`x-jpa-ddl-auto`). `update`는 칼럼을 지우거나 타입을 바꾸지 않습니다.
  어떤 서비스에 Flyway가 생기면 `compose.yml`에서 그 서비스의 `SPRING_JPA_HIBERNATE_DDL_AUTO` 줄을 지웁니다.
- prod 프로파일은 dev 시작 데이터(`*DataInit`: 기본 회원·관리자, 대회·정산 샘플)를 만들지 않습니다. 빈 DB로 띄우면 회원·대회가 하나도 없습니다.

#### 처음 띄운 뒤 확인할 것 (체크리스트)

빈 DB에서 확인하려면 먼저 볼륨까지 지웁니다: `docker compose --profile app down -v` (기존 dev 데이터도 지워지니 주의).

1. [ ] `docker compose --profile app up -d --build`가 오류 없이 끝남
2. [ ] `docker compose --profile app ps`에서 서비스 6개·`gateway`가 `healthy`, `web`·`nginx`가 `Up`
3. [ ] http://localhost 접속 시 홈 화면이 보임
4. [ ] 회원가입 → 로그인 성공
5. [ ] 새로고침해도 로그인이 유지됨 (Refresh Token 쿠키로 재발급. 아래 "Secure 쿠키" 참고)
6. [ ] 대회 목록 화면이 오류 없이 열림 (빈 DB면 빈 목록이 정상)
7. [ ] 판매자 대회 등록 화면에서 이미지 업로드 성공 (브라우저가 `http://localhost:9090`의 S3Mock으로 직접 올림)

실패하면 볼 로그:

```bash
docker compose --profile app ps -a                          # 어느 컨테이너가 unhealthy·Exited인지
docker compose logs --tail=200 <서비스>                      # 예: member-service, order-service, gateway, web, nginx
docker compose logs <서비스> 2>&1 | grep -iE "missing table|Schema-validation|Flyway|APPLICATION FAILED|Caused by"
docker compose exec bukang-db mysql -ubukang2 -pbukang2 bukang-db -e "show tables;"
docker compose exec bukang-db mysql -ubukang2 -pbukang2 bukang-db -e "select version, description, success from flyway_schema_history_member;"
```

`member-service` Flyway(`flyway_schema_history_member`, 기준선 0)는 실제 MySQL에서 아래 세 경우를 확인합니다.

| 경우 | 만드는 방법 | 기대 결과 |
|---|---|---|
| 빈 DB | `down -v` 뒤 `up` | V1부터 모든 SQL이 돌고 `MEMBER_*` 표가 생김, member-service가 healthy |
| 다른 서비스 표만 있는 DB | 빈 DB에서 `member-service`만 빼고 띄운 뒤(다른 서비스가 `CASH_*` 등을 만듦) `member-service`를 띄움 | 기준선 0이라 V1이 돌고 `MEMBER_*` 표가 생김 |
| 기존 dev DB (Flyway 전 `ddl-auto: update`로 만든 `MEMBER_*`가 있음) | 기존 볼륨 그대로 `up` | V1(`create table if not exists`)은 기존 표를 건드리지 않고, 이후 SQL이 돌아 validate 통과. 단, 예전에 `update`로 `member_terms` 표나 `role`·`status` 칼럼이 이미 생겼다면 `V202610161100` 이후 SQL이 "already exists"·"Duplicate column"으로 실패할 수 있어 이 경우를 꼭 확인 |

#### Secure 쿠키와 http://localhost

Refresh Token 쿠키는 항상 `Secure`입니다(`ApiV1AuthController.refreshTokenCookie`). Nginx는 HTTP(80)만 받지만,
Chrome·Firefox·Edge는 `http://localhost`를 안전한 주소로 보고 `Secure` 쿠키를 저장·전송하므로 로컬에서는 로그인이 유지됩니다.
Safari는 버전에 따라 `http://localhost`의 `Secure` 쿠키를 저장하지 않을 수 있어, 로컬 확인은 Chrome으로 합니다.
서버(EC2)에 IP나 도메인으로 HTTP 접속하면 모든 브라우저가 이 쿠키를 버려 새로고침하면 로그아웃되므로, 서버는 HTTPS로 엽니다.

## 파일 저장소 (S3)

`file-service`는 대회 이미지·소개 영상·크루원 명단(엑셀) 파일을 S3에 둡니다. 파일 본문은 서버를 거치지 않고, 브라우저가 서버에서 받은 presigned URL로 S3에 직접 올리고 받습니다.
접속 설정은 `file-service`의 `application.yaml`(`file.storage.s3`)에 있습니다.
API 사용법(단계별 요청·응답, 파일 종류, 연결 규칙)은 [`file-service/README.md`](file-service/README.md)에 있습니다.

| 실행 방법 | 쓰는 S3 | 설정 | 키 앞 환경 이름 |
|---|---|---|---|
| `bootRun`, IDE | 로컬 S3Mock (`localhost:9090`) | 기본값 그대로 | `dev/` |
| `docker compose --profile app up` | 로컬 S3Mock (컨테이너 안에서는 `bukang-s3mock:9090`) | `compose.yml`이 넘긴다 | 컨테이너 프로파일을 따른다 (지금은 prod라 `prod/`) |
| 실제 AWS S3로 확인 | 실제 버킷 | `.env`에 `S3_*` 값을 채운다 (`.env.example` 참고) | 실행 방법의 프로파일을 따른다 |
| 운영 서버 | 실제 버킷 | 환경변수. 키 대신 EC2 IAM 역할 | `prod/` |

- 키 앞의 환경 이름은 `file.storage.s3.key-prefix`이고, 환경변수가 아니라 프로파일별 yaml(`application-dev.yaml`, `application-prod.yaml`)에 적습니다.
  로컬과 운영이 같은 버킷을 써도 `dev/…`와 `prod/…`로 나뉩니다. 앱 컨테이너를 실제 버킷으로 띄우면 prod 프로파일이라 `prod/`에 쓰므로 주의합니다.

- S3Mock은 path-style 주소(`http://localhost:9090/<버킷>/<키>`)만 지원하고, presigned URL의 서명과 만료를 검사하지 않습니다. 서명 관련 동작은 실제 S3에서 확인합니다.
- 키가 비어 있으면 AWS SDK 기본 자격 증명 체인(환경변수, `~/.aws`, EC2 역할)을 씁니다. AWS 키는 `.env`에만 두고 커밋하지 않습니다.
- 버킷은 퍼블릭 액세스 차단을 켠 비공개 버킷입니다. 이미지는 짧게 만료되는 presigned GET URL로 보여 줍니다.

### 업로드 흐름과 객체 경로

```
① POST /api/v1/file/files               업로드 URL 발급 (파일은 PENDING, 키 {환경}/uploads/{uuid}.{확장자})
② 브라우저 → S3 PUT                       응답의 headers(content-type, content-length)를 그대로 붙인다
③ POST /api/v1/file/files/{id}/complete  서버가 {환경}/uploads/ → {환경}/files/로 복사하고, 복사본의 크기·앞부분(매직 바이트)을 확인 → UPLOADED
```

| 경로 | 내용 | 정리 |
|---|---|---|
| `{환경}/uploads/` | 브라우저가 presigned URL로 올린 원본. 확인이 끝나면 지운다 | 확인하지 않고 남은 파일(PENDING)의 객체는 1일 뒤 정리 배치가 행과 함께 지운다. 확인 뒤 같은 URL로 다시 올린 객체는 아직 지우지 않는다 (S3 수명 주기 규칙 미설정) |
| `{환경}/files/` | 서버가 확인을 마친 파일. 업로드 URL을 주지 않는다 | 대상에 붙지 않았거나 대상에서 빠진 파일은 1일 뒤 정리 배치가 지운다 (file-service `BATCH-ROADMAP.md`). 정리 배치는 prod 프로파일에서만 돌아 `dev/` 객체는 지우지 않는다 |

presigned URL은 만료 전까지 여러 번 쓸 수 있어서, 확인을 마친 뒤 같은 URL로 다른 내용을 덮어쓸 수 있습니다.
확인한 파일을 업로드 URL이 없는 `{환경}/files/`로 옮겨 두면 확인한 내용이 바뀌지 않습니다.
환경 이름을 붙이기 전에 만든 객체는 버킷 루트의 `uploads/`·`files/`에 있습니다.
업로드 URL에는 `content-type`과 `content-length`가 서명되므로, 다른 형식이나 크기로 올리면 실제 S3가 거절합니다.

## 민감정보 암호화

복호화가 필요한 민감정보(회원 휴대폰 번호, cash-service의 계좌번호·PG 결제 키)는 DB에 **AES-256-GCM 암호문**으로 저장합니다.
그 값으로 조회하거나 중복을 검사해야 하면 **HMAC-SHA256 해시(블라인드 인덱스)**를 별도 컬럼에 함께 저장합니다.
비밀번호는 복호화할 필요가 없으므로 이 방식이 아니라 `PasswordEncoder`(BCrypt)로 해시합니다.

| 컬럼 | 저장 값 | 용도 |
|---|---|---|
| `phone` | AES 암호문 (같은 값도 매번 다른 암호문) | 꺼내서 보여 주거나 발송할 때 (복호화) |
| `phone_hash` | HMAC 해시 (같은 값이면 항상 같은 해시, unique) | 조회, 중복 검사 (`WHERE phone_hash = ?`) |

### 구성 요소 (`common`의 `global/crypto`)

| 클래스 | 역할 |
|---|---|
| `CryptoConfig` | `crypto.password`, `crypto.salt`로 `TextEncryptor`(AES-256-GCM) 빈을 한 번만 생성 |
| `Base64TextEncryptor` | 암호화 결과(바이트)를 Base64 문자열로 바꿔 DB 컬럼에 저장할 수 있게 함 |
| `EncryptedStringConverter` | JPA `AttributeConverter`. DB에 저장할 때 암호화하고, 조회할 때 복호화 |
| `BlindIndexGenerator` | `crypto.hmac-secret`으로 조회용 해시 생성. 필드별 메서드에서 정규화 후 해시 (`generatePhone`은 숫자만 남김) |

모든 서비스가 common을 스캔하므로, yaml에 `crypto.enabled: true`를 넣은 서비스에서만 `TextEncryptor`·`EncryptedStringConverter`가,
`crypto.hmac-secret`을 설정한 서비스에서만 `BlindIndexGenerator`가 빈으로 등록됩니다.
지금은 member-service(휴대폰 번호)와 cash-service(계좌번호, PG 결제 키. 블라인드 인덱스는 쓰지 않음)가 켜 두었습니다.

- `crypto.password` 유무로 켜지 않는 이유: compose는 모든 앱 컨테이너에 같은 `.env`를 넣고, Spring은 환경 변수 `CRYPTO_PASSWORD`를
  `crypto.password`로 읽습니다. 그래서 키 유무로 정하면 켜지 않은 서비스에서도 빈이 만들어집니다.
- 다른 서비스에서 쓰려면 그 서비스의 `application-dev/test/prod.yaml`에 `crypto.enabled: true`와 아래 키 설정을 넣습니다.

### 키 설정

| 설정 키 | dev / test | prod | 생성 명령 |
|---|---|---|---|
| `crypto.password` | `application-dev.yaml`, `application-test.yaml` | `.env`의 `CRYPTO_PASSWORD` | `openssl rand -base64 32` |
| `crypto.salt` | 〃 | `.env`의 `CRYPTO_SALT` | `openssl rand -hex 8` (16진수만 가능) |
| `crypto.hmac-secret` | 〃 | `.env`의 `CRYPTO_HMAC_KEY` | `openssl rand -base64 32` |

- prod에서는 암호화를 켠 모든 서비스가 `.env`의 같은 `CRYPTO_*` 키를 씁니다. 각 서비스의 `application-prod.yaml`은 `${CRYPTO_PASSWORD}`, `${CRYPTO_SALT}`를 참조합니다.
- dev/test 키는 개발용이라 서비스마다 yaml에 들어 있습니다(지금은 member와 cash 값이 다릅니다). prod 키는 dev와 **다른 값**으로 만들고 `.env`에만 둡니다.
- 세 키는 서로 다른 값이어야 합니다. `JWT_SECRET`과도 같은 값을 쓰지 않습니다.
- `openssl`은 Windows의 Git Bash와 macOS 터미널에 기본으로 들어 있습니다.

### 새 필드에 적용하는 방법

1. 엔티티 필드에 `@Convert`를 붙입니다. 암호문이 원문보다 길어지므로 컬럼 길이를 넉넉히 잡습니다.

   ```java
   @Convert(converter = EncryptedStringConverter.class)
   @Column(length = 255)
   private String phone;
   ```

2. 그 값으로 조회하거나 중복을 검사해야 하면 해시 컬럼을 추가하고, `BlindIndexGenerator`에 필드 전용 메서드를 만듭니다.
   정규화 방식(예: 숫자만 남기기, 소문자로 바꾸기)과 필드 이름 접두사(`"phone:"`)를 필드마다 정합니다.

   ```java
   @Column(name = "phone_hash", unique = true, length = 64)
   private String phoneHash;
   ```

3. 서비스(`app` 계층)에서 해시를 계산해 엔티티에 원문과 함께 넘깁니다. 원문은 평문 그대로 넘기면 저장할 때 자동으로 암호화됩니다.

   ```java
   String phoneHash = blindIndexGenerator.generatePhone(phone);
   if (memberRepository.existsByPhoneHash(phoneHash)) {
   	throw new DuplicatePhoneException("이미 사용 중인 전화번호입니다.");
   }
   new Member(email, passwordEncoder.encode(password), name, nickname, phone, phoneHash, birthDate, gender);
   ```

4. 값을 바꾸는 메서드도 원문과 해시를 **함께** 받아 둘 다 바꿉니다. 해시는 자동으로 갱신되지 않습니다.

### 주의 사항

- **키를 바꾸면 기존 데이터를 읽을 수 없습니다.** 데이터가 쌓인 뒤에는 세 키 모두 바꾸지 않습니다. prod 키는 `.env` 외에 안전한 곳에도 백업합니다.
- **암호화한 필드로 직접 조회하지 않습니다.** `findByPhone(...)`은 결과가 나오지 않습니다(암호문이 매번 다름). 반드시 해시 컬럼으로 조회합니다.
- **엔티티 안에서는 항상 평문입니다.** `getPhone()`은 복호화된 원문을 반환하므로, API 응답 DTO에 그대로 넣거나 로그·예외 메시지에 남기지 않습니다. 화면에 보여 줄 때는 필요하면 마스킹합니다.
- **native query와 JDBC는 Converter를 거치지 않습니다.** 직접 SQL로 다루면 암호화/복호화가 일어나지 않습니다.
- **DB를 직접 조회하면 암호문이 보이는 게 정상입니다.**
- 주민등록번호는 법령 근거 없이 수집할 수 없습니다(개인정보 보호법 제24조의2). 본인 확인이 필요하면 본인인증의 CI 값을 같은 방식으로 저장합니다.

## Kafka 메시지 직렬화 (`JsonConverter`)

Kafka에는 메시지를 **JSON 문자열**로 보냅니다. Kafka 직렬화기는 `StringSerializer`/`StringDeserializer`로 고정하고(`application.yaml`),
객체와 JSON 사이의 변환은 애플리케이션 코드에서 `JsonConverter`(`common`의 `global/json`)로 합니다.

### Spring Kafka의 JSON 직렬화기를 쓰지 않는 이유

`application.yaml`의 Kafka 설정 주석에 자세한 내용이 있습니다. 요약하면 다음과 같습니다.

- **역직렬화 실패 시 같은 메시지를 계속 다시 읽습니다 (poison pill).** JSON이 깨진 메시지가 들어오면 역직렬화기(`JacksonJsonDeserializer`)
  단계에서 예외가 나고, 컨슈머가 같은 offset을 반복해서 읽습니다(`ErrorHandlingDeserializer`로 감싸야 피할 수 있습니다).
  문자열로 받으면 Kafka 단계에서는 실패하지 않고, 변환 실패는 리스너 코드의 예외로 다룰 수 있습니다.
- **타입 헤더에 클래스 전체 경로(FQCN)가 들어갑니다.** JSON 직렬화기는 기본적으로 `__TypeId__` 헤더에 FQCN을 넣으므로,
  `spring.json.type.mapping`으로 논리 이름을 따로 정하지 않으면 메시지 클래스의 패키지만 옮겨도 컨슈머가 역직렬화에 실패합니다.

### `JsonConverter`

| 메서드 | 역할 | 실패 시 |
|---|---|---|
| `toJson(Object)` | 객체를 JSON 문자열로 변환 | `IllegalStateException("JSON 변환에 실패했습니다.")` |
| `fromJson(String, Class<T>)` | JSON 문자열을 지정한 타입의 객체로 변환 | `IllegalArgumentException("JSON 파싱에 실패했습니다.")` |

- Spring Boot가 등록한 `ObjectMapper` 빈을 재사용하므로 `spring.jackson.*` 설정이 API 응답 JSON과 똑같이 적용됩니다.
- 실패 예외에는 원인 예외(`JacksonException`)가 함께 들어 있어, 로그에서 어느 필드가 왜 실패했는지 확인할 수 있습니다.
- **`new ObjectMapper()`를 직접 만들지 않습니다.** 만드는 비용이 크고, Spring Boot의 Jackson 설정이 적용되지 않습니다.
  메시지 클래스에 static `fromJson`/`toJson`을 두는 것도 같은 이유로 피합니다. static 메서드에는 빈을 주입할 수 없습니다.

### 사용 방법

아래 코드의 토픽, 메시지 클래스, 그룹 ID는 사용법을 보여 주기 위한 예시입니다.

1. **메시지 클래스**를 만듭니다. 생산자와 소비자(다른 서비스)가 함께 쓰므로 `common`의 `shared/<도메인>/event` 패키지에 두고, `record`로 만들면 별도 설정 없이 변환됩니다.

   ```java
   public record EmailSendMessage(
   	String to,
   	String subject,
   	String content
   ) {
   }
   ```

2. **토픽 이름**은 [Kafka 토픽 네이밍 컨벤션](../.claude/rules/kafka-topic-convention.md)(`<message-type>.<dataset-name>.<data-name>`)을 따르고,
   상수로 한곳에 모아 생산자와 소비자가 같은 상수를 참조합니다. 오타가 나면 오류 없이 다른 토픽이 새로 만들어지기 때문입니다.

   ```java
   public final class KafkaTopics {
   	public static final String EMAIL_SEND = "queuing.email.send";

   	private KafkaTopics() {
   	}
   }
   ```

   **토픽은 따로 만들지 않습니다.** 토픽 자동 생성을 허용하므로(브로커 설정, `application.yaml`의 `allow.auto.create.topics`), 처음 메시지를 보내거나 리스너가 구독할 때 브로커가 토픽을 만듭니다.
   `NewTopic` 빈을 선언할 필요가 없습니다. 자동으로 만들어진 토픽은 브로커 기본값(로컬 Redpanda는 파티션 1개)을 따릅니다.

3. **보내기:** `toJson`으로 변환한 문자열을 `KafkaTemplate<String, String>`으로 보냅니다.

   ```java
   @Component
   @RequiredArgsConstructor
   public class EmailSendProducer {
   	private final KafkaTemplate<String, String> kafkaTemplate;
   	private final JsonConverter jsonConverter;

   	public void send(EmailSendMessage message) {
   		kafkaTemplate.send(KafkaTopics.EMAIL_SEND, jsonConverter.toJson(message));
   	}
   }
   ```

4. **받기:** 리스너는 `String`으로 받고 `fromJson`으로 변환합니다. `spring.kafka.consumer.group-id`가 설정되어 있지 않으므로
   `@KafkaListener`에 `groupId`(또는 그룹 ID로도 쓰이는 `id`)를 적습니다.

   ```java
   @Component
   @RequiredArgsConstructor
   public class EmailSendConsumer {
   	private final JsonConverter jsonConverter;

   	@KafkaListener(topics = KafkaTopics.EMAIL_SEND, groupId = "email")
   	public void listen(String payload) {
   		EmailSendMessage message = jsonConverter.fromJson(payload, EmailSendMessage.class);
   		// ...
   	}
   }
   ```

### 주의 사항

- **Jackson 3 패키지를 씁니다.** Spring Boot 4는 Jackson 3를 쓰므로 `tools.jackson.*`을 import합니다.
  `com.fasterxml.jackson.databind.ObjectMapper`를 import하면 Spring이 등록한 매퍼와 다른 클래스입니다.
  단, `@JsonProperty` 같은 애노테이션은 Jackson 3에서도 `com.fasterxml.jackson.annotation` 패키지 그대로입니다.
- **파싱 실패 메시지는 재시도 후 건너뜁니다.** 리스너에서 예외가 나면 Spring Kafka 기본 에러 핸들러(`DefaultErrorHandler`)가
  간격 없이 9번 더 시도(총 10번)한 뒤 로그를 남기고 다음 메시지로 넘어갑니다. JSON이 깨진 메시지는 다시 시도해도 실패하므로,
  필요하면 `DefaultErrorHandler` 빈을 등록해 `addNotRetryableExceptions(IllegalArgumentException.class)`로 재시도하지 않게 하거나
  DLT(Dead Letter Topic)로 보내도록 설정합니다. `CommonErrorHandler` 빈은 Spring Boot가 리스너 컨테이너에 자동으로 적용합니다.
- **토픽 자동 생성은 브로커 설정이 허용해야 동작합니다.** 로컬 Redpanda는 `dev-container` 모드라 켜져 있습니다
  (`auto_create_topics_enabled=true`). 운영 브로커는 이 저장소 밖에서 관리하므로, 운영 브로커도 자동 생성(`auto.create.topics.enable`)을
  허용하는지 확인합니다. 꺼져 있으면 토픽이 없다는 오류로 전송이 실패합니다.
  파티션을 2개 이상으로 늘려야 하는 토픽이 생기면 그 토픽만 `NewTopic` 빈(`TopicBuilder`)으로 선언합니다.
- **메시지에 개인정보를 넣을 때 주의합니다.** Kafka 메시지는 브로커에 평문 JSON으로 남고 Kafka Console(http://localhost:8091)에서 그대로 보입니다.
  `MemberDto`처럼 복호화된 휴대폰 번호가 들어 있는 객체를 통째로 보내지 말고, 필요한 필드만 담은 메시지 클래스를 따로 만듭니다.
- **메시지 클래스를 바꿀 때는 기존 메시지와의 호환을 생각합니다.** 토픽에는 바뀌기 전 형식의 메시지가 남아 있을 수 있습니다.
  - 필드를 추가하면 그 필드가 없는 기존 메시지는 `null`로 채워지고, 소비자가 모르는 필드는 무시됩니다.
    단, 추가하는 필드는 `int`/`long`/`boolean` 같은 기본 타입이 아니라 `Integer`/`Long`/`Boolean` 같은 참조 타입으로 만듭니다.
    기본 타입 필드에 값이 없으면 파싱에 실패합니다(Jackson 3 기본값 `FAIL_ON_NULL_FOR_PRIMITIVES`).
  - 필드 이름을 바꾸면 기존 메시지의 그 값을 읽지 못해 `null`이 됩니다. 필드를 지우면 기존 메시지의 그 값은 무시됩니다.

## API 문서 (Swagger)

### 접속

| 주소 | 내용 |
|---|---|
| http://localhost:8080/swagger-ui.html | 회원 서비스 Swagger UI (dev 프로파일에서만 열림, prod에서는 비활성화) |
| http://localhost:8081/swagger-ui.html | 캐시 서비스 Swagger UI |
| http://localhost:8082/swagger-ui.html | 정산 서비스 Swagger UI |
| http://localhost:8083/swagger-ui.html | 파일 서비스 Swagger UI |
| http://localhost:8084/swagger-ui.html | 주문 서비스 Swagger UI |
| http://localhost:8085/swagger-ui.html | 상품 서비스 Swagger UI |
| http://localhost:8086/swagger-ui.html | 대기열 서비스 Swagger UI |
| http://localhost:8080/v3/api-docs | OpenAPI 문서 원본(JSON) |

### 작성 규칙

컨트롤러와 요청 DTO의 Swagger 애노테이션은 [`.claude/rules/swagger-convention.md`](../.claude/rules/swagger-convention.md)를 따릅니다.
참고 구현은 `member-service`의 `in/ApiV1AuthController.java`와 `AuthApiExamples.java`입니다.

- 컨트롤러에 `@Tag`, 엔드포인트마다 `@Operation`과 `@ApiResponses`를 붙입니다.
- 응답 코드는 그 API가 **실제로 반환하는 코드만** 적습니다. 생성 API의 201은 springdoc이 추론하지 못하므로 직접 명시합니다.
- 응답 예시는 `{Tag}ApiExamples` 상수 클래스에 모으고, `message`는 코드의 메시지 문자열을 그대로 씁니다.
  예외 메시지나 검증 메시지를 바꾸면 예시도 함께 바꿉니다.
- 요청 DTO의 모든 필드에 `@Schema(description, example)`를 붙입니다.

참고 구현에서 따르는 것은 애노테이션 구성 방식뿐입니다. 응답 코드와 에러는 각 컨트롤러의 실제 코드를 기준으로 작성합니다.

### Claude Code 스킬: `/swagger-annotate`

위 규칙대로 Swagger 애노테이션을 작성해 주는 프로젝트 스킬입니다 (`../.claude/skills/swagger-annotate`).

```
/swagger-annotate ApiV1MemberController
```

슬래시 명령 대신 "ApiV1MemberController에 Swagger 달아줘", "API 문서화해줘"처럼 요청해도 됩니다.
대상을 적지 않으면 IDE에서 열려 있는 컨트롤러를 대상으로 합니다.

스킬은 다음 순서로 진행합니다.

1. **실제 응답 조사:** 성공 반환 코드, 호출 흐름에서 던지는 예외와 메시지, `GlobalExceptionHandler`의 매핑,
   요청 검증, `WebConfig`의 인증 필요 여부를 확인합니다.
2. **작성:** `@Tag`, `@Operation`, `@ApiResponses`, 응답 예시 상수 클래스, 요청 DTO의 `@Schema`를 작성합니다.
   비즈니스 로직, 매핑 경로, 메서드 시그니처는 바꾸지 않습니다.
3. **검사:** checkstyle 경고가 0건인지 확인하고, 앱을 띄워 `/v3/api-docs`의 예시가 실제 응답과 같은지 확인합니다.
   8080을 IDE가 쓰고 있으면 18080 포트로 따로 띄우고, 확인 후 종료합니다.
4. **보고:** 문서화한 응답 코드와 예시, 바뀐 파일, 조사 중 발견한 문제(핸들러가 없어 500으로 나가는 예외 등)를 알려 줍니다.

스킬은 커밋하지 않습니다. 결과를 확인한 뒤 직접 커밋하거나 커밋을 요청합니다.
