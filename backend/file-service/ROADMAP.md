# file-service 작업 로드맵

이 문서는 Claude가 2026-10-09에 단계마다 확인받지 않고 진행한 file-service 작업을, **실제로 한 순서대로** 적은 것입니다.
순서와 결정이 맞는지 함께 검토합니다. 바꿀 것이 생기면 아래 "논의 기록"에 먼저 적고 고칩니다.

- **[결정됨]**: 사용자와 함께 정한 것
- **[Claude 단독]**: Claude가 계획 문서에만 적고 설명 없이 정한 것 (논의 필요)

## 완성된 흐름

```
판매자 브라우저                     file-service                          S3
① POST /api/v1/file/files ────▶ 형식·크기 검사, FILE_FILE에 PENDING 저장
   ◀──── fileId, presigned PUT URL(10분)
② PUT uploadUrl (파일 본문) ──────────────────────────────────────▶ uploads/{uuid}.png
③ POST /api/v1/file/files/{id}/complete ─▶ uploads/ → files/ 복사, 복사본 검사, uploads/ 삭제 → UPLOADED
④ 대회 등록(fileId 포함) ─▶ product-service ─FileClient─▶ PUT /internal/v1/file/refs/Race/{raceId}/files → ACTIVE
⑤ GET /api/v1/file/files?refType=Race&refId=1 ─▶ 파일 목록 + presigned GET URL(30분)
```

④의 product-service와 FileClient는 아직 없습니다. file-service는 받는 쪽 API만 만들어 두었습니다.

## 브랜치와 커밋

| 순서 | Linear | 브랜치 | 커밋 |
|---|---|---|---|
| 1 | PRO-51 파일 서비스 모듈 뼈대 | `feat/PRO-51-file-service` | 6988dea, b256b9b |
| 2 | PRO-53 파일 저장소를 S3에 연결 | `feat/PRO-53-s3-storage` | 69b16d7, 7652dda, 0292fd8 |
| 3 | PRO-54 실제 S3 버킷 만들고 연결 | 없음 (콘솔 작업) | - |
| 4 | PRO-55 업로드 URL 발급·완료 API | `feat/PRO-55-file-upload-api` | 83973f7 |
| 5 | PRO-56 연결·조회 API + 검토(3단계) 반영 | `feat/PRO-56-file-link-api` | e9400e6, 55c61ce, ed3c701, 39716c8, 맨 위 docs 커밋 |

- 브랜치는 앞 브랜치 위에 차례로 쌓았습니다(dev ← 51 ← 53 ← 55 ← 56).
- 2026-10-10에 origin/main 기준에서 **origin/dev(c728e19) 위로** 옮겼습니다. 그래서 해시가 바뀌었습니다.
  옮기기 전 상태는 로컬 태그 `backup-before-dev-rebase`(2405fe4)에 있고, 아래 "실제 작업 순서"의 해시는 옮기기 전 것입니다.
- 부모 이슈는 PRO-52 "대회 이미지 업로드 만들기"입니다.
- Backlog 이슈는 세 개입니다.
  - PRO-57: 연결되지 않은 이미지 정리 배치
  - PRO-58: 이미지 가로·세로 검사
  - PRO-59: 파일 API에 게이트웨이 회원 ID 적용

## 실제 작업 순서

### 0. 논의와 조사 (사용자와 함께)

- [결정됨] 파일은 Kafka로 보내지 않습니다. 먼저 올려서 fileId를 받고, 대회를 등록할 때 product-service가 FileClient(동기 HTTP)로 연결합니다.
- [결정됨] 파일 기능은 별도 file-service에 둡니다.
- [결정됨] 업로드는 presigned PUT으로 하고, 실제 S3 버킷까지 연결합니다.
- [결정됨] 파일 테이블은 코드 관례를 따릅니다(`BaseIdAndTime`, 테이블 이름 `<도메인>_<엔티티>`).

사용자와 정한 것은 여기까지입니다. 1~8단계의 세부 결정은 Claude가 계획 문서에 적은 뒤 바로 구현했습니다.

### 1. PRO-51 모듈 뼈대 (2e8c7bb)

- 한 일
  - `settings.gradle.kts`에 모듈 등록
  - `file-service/build.gradle.kts`, `FileApplication`, yaml 설정 파일(application, dev, prod, test)
  - `Dockerfile`, `compose.yml`에 앱 컨테이너 추가
  - `.env.example`, README, CLAUDE.md 갱신
- [Claude 단독]
  - 포트를 8082로 정함 (2026-10-10 dev 위로 옮기며 8083으로 바꿈. payout-service가 8082를 먼저 썼다)
  - cash-service와 같은 의존성 구성에서 Kafka만 뺌
- 볼 파일: `settings.gradle.kts`, `file-service/build.gradle.kts`, `FileApplication.java`
- 확인: `./gradlew :file-service:test --tests "com.bukang.file.FileApplicationTests"`

### 2. Linear 이슈 생성

- 계획을 승인받은 뒤 부모 PRO-52, 하위 PRO-53~56, Backlog PRO-57~59를 만들고 PRO-51을 부모에 연결했습니다.

### 3. PRO-53 S3 연결 (b4e0719)

- 한 일
  - AWS SDK v2 의존성 추가
  - `S3Properties`(설정 값), `S3Config`(`S3Client`, `S3Presigner`)
  - `FileStorage` 인터페이스와 `S3FileStorage`
  - `compose.yml`에 S3Mock 추가
  - `S3FileStorageTest` 작성(Testcontainers)
- [Claude 단독]
  - 로컬 S3 대체재로 S3Mock을 고름. LocalStack은 인증 토큰이 필요하고, MinIO 커뮤니티판은 유지보수가 끝났기 때문
  - 자격 증명: `.env`에 키가 있으면 그 키를 쓰고, 없으면 SDK 기본 체인을 씀(운영에서는 EC2 역할)
  - presigned URL 주소(`publicEndpoint`)를 서버가 S3에 접속하는 주소와 따로 둠. 컨테이너 안에서 쓰는 주소와 브라우저가 쓰는 주소가 다르기 때문
  - URL 만료 시간: 업로드 10분, 조회 30분
  - S3 호출을 `FileStorage` 인터페이스로 감쌈. 테스트에서 가짜 구현으로 바꿔 끼우기 위해서
- 볼 파일: `config/S3Config.java`, `out/storage/FileStorage.java`, `out/storage/S3FileStorage.java`
- 확인: `./gradlew :file-service:test --tests "*S3FileStorageTest"` (Docker 필요)

### 4. PRO-55 업로드 API 1차 구현

- 한 일
  - `domain/`: `StoredFile`, `FileStatus`, `ImageFormat`
  - `dto/`, `FileUploadUseCase`, `FileFacade`, `ApiV1FileController`, `FileExceptionHandler`
  - Swagger 문서, 테스트
- 처음 설계: 브라우저가 presigned PUT으로 `files/`에 바로 올리고, 완료 API가 그 객체를 검사함
- [Claude 단독]
  - 상태에 UPLOADED를 추가해 4개로 둠. 공유 DDL에는 3개뿐이었음
  - JPG·PNG·WebP만 받고, 10MB까지로 제한
  - 매직 바이트로 실제 형식을 검사
  - 엔티티 이름을 `StoredFile`로 정함. `java.io.File`과 헷갈리지 않게 하려고
  - 회원 ID는 임시 헤더 `X-Member-Id`로 받음. 정식 방식은 PRO-20에서 정해짐
  - 완료 API를 다시 불러도 같은 결과가 나오게 함(멱등)

### 5. S3Mock 수동 확인 중 설계 변경: `uploads/` → `files/`

- 발견: 완료 API가 검사를 마친 뒤에도 presigned PUT URL은 만료(10분) 전까지 다시 쓸 수 있습니다. 같은 URL로 다른 파일을 올리면, 검사받지 않은 파일이 검사한 파일을 덮어씁니다.
- 바꾼 것
  - 브라우저는 `uploads/`에만 올립니다.
  - 완료 API가 `files/`로 복사하고, 복사본을 검사한 뒤 `uploads/` 원본을 지웁니다.
  - `files/`에 대한 업로드 URL은 발급하지 않습니다.
  - `FileStorage`에 `copy`를 추가했습니다.
- [Claude 단독] 설계를 바꾸면서 묻지 않았습니다.

### 6. 실제 S3 연결 중 설정 혼선 수정 (d0456a2, PRO-53 브랜치)

- 발견: `.env`에서 `S3_ENDPOINT` 등 세 줄이 주석 처리돼 있었습니다. 그래서 버킷 이름은 실제 버킷인데 주소는 S3Mock인 상태로 앱이 실행됐습니다.
- 바꾼 것
  - 앱이 시작할 때 버킷, 엔드포인트, 자격 증명 방식을 로그로 남깁니다.
  - 로컬 주소에 실제 AWS 키를 쓰면 경고를 남깁니다.
  - `.env.example`의 안내를 보강했습니다.
- 순서 문제: PRO-53 범위의 수정이라 PRO-53 브랜치로 돌아가 커밋하고, PRO-55 브랜치를 그 위로 앞당겼습니다(fast-forward).

### 7. E2E 확인 후 PRO-55 커밋 (366b87c)

- S3Mock과 실제 S3에서 ①~③ 흐름을 확인했습니다.
- 실제 S3에서 확인한 것
  - 발급 때 알린 것과 다른 Content-Type이나 크기로 PUT하면 403이 납니다. 두 값이 서명에 들어가기 때문입니다.
  - 서명 없는 GET에도 200이 나왔습니다. 버킷이 공개 읽기 상태라는 뜻이며, 아직 고치지 않았습니다. (이후 사용자가 막았다. 2026-10-10 콘솔에서 "모든 퍼블릭 액세스 차단" 활성화, 버킷 정책 없음을 확인)

### 8. PRO-56 연결·조회 API (b05499c)

- 한 일: `FileLinkUseCase`, `InternalV1FileController`, 조회 API 2개, 테스트
- [Claude 단독]
  - 내부 경로를 `/internal/v1/...`로 정함. 게이트웨이에 노출하지 않는다는 전제
  - PUT 요청의 목록을 그 대상의 파일 전체로 봄. 다시 보내도 같은 결과가 나오고(멱등), 목록에서 빠진 파일은 DELETED가 됨
  - 소유자 ID(`ownerId`)를 요청 본문으로 받음. 내부 호출을 믿는다는 전제
  - `imageType`은 정해진 값이 없는 자유 문자열. THUMBNAIL, DETAIL은 예시일 뿐
  - ACTIVE 파일은 누구나 조회할 수 있음
- 실제 S3에서 ④·⑤를 확인했습니다.
- product-service 담당자와 이 계약을 맞춘 적은 없습니다.

### 9. 리뷰 워크플로 시작 후 중단

- 사용자 요청으로 멈췄습니다. 리뷰 결과는 없습니다.

## 순서에 대한 Claude 의견 (논의 거리)

1. S3 저장소를 도메인과 API 계약보다 먼저 만들었습니다. 흐름, 상태, API 모양을 먼저 정했다면 5단계 같은 설계 변경이 구현 뒤에 나오지 않았을 것입니다.
2. 같은 URL 재사용, 버킷 공개 같은 보안 문제를 설계 단계가 아니라 E2E에서 발견했습니다.
3. PRO-54(실제 버킷)는 PRO-55 앞에 두었지만, 버킷 설정을 점검하고 정리하지 않은 채 넘어갔습니다.
4. 이슈마다 커밋을 하나로 몰아 담아서, 중간 단계를 따라가기 어렵습니다.
5. PRO-56은 쓰는 쪽(product-service)도 없고 계약 합의도 없이 만들었습니다.

참고로 Claude가 다시 한다면 제안할 순서입니다. 정답이 아니라 비교용입니다.

1. 흐름, API 계약, 상태, 객체 경로를 문서로 먼저 합의합니다. 보안 위협 검토도 이때 합니다.
2. 모듈 뼈대 (PRO-51)
3. 도메인(`StoredFile`, `FileStatus`, `ImageFormat`)과 단위 테스트
4. `FileStorage` 인터페이스와 가짜 구현으로 업로드 API를 먼저 완성합니다. S3 없이 합니다.
5. S3 구현체와 S3Mock
6. 실제 버킷. 퍼블릭 액세스 차단, IAM, CORS 체크리스트를 먼저 봅니다.
7. 연결·조회 API. product-service와 계약을 합의한 뒤에 만듭니다.

## 남은 일

- ~~버킷 공개 읽기 차단~~ (완료: 모든 퍼블릭 액세스 차단 활성화, 버킷 정책 없음. 2026-10-10 확인)
- ~~IAM 정책에 `uploads/*` 추가~~ (3-5에서 실제 버킷에 업로드·복사·삭제가 모두 성공해 권한은 충분하다. 남은 확인: `s3:ListBucket`이 있어야 없는 객체에 404가 온다)
- PRO-54 결과 기록
- product-service FileClient (계약 합의 필요)
- PRO-57 정리 배치: 구현과 테스트를 마쳤다 (2026-10-11, 커밋·PR 전). 기준과 기록은 [BATCH-ROADMAP.md](BATCH-ROADMAP.md)
- Backlog PRO-58~59
- ~~푸시와 PR~~ (완료: 1~3단계는 PR #11~#14로 dev에 머지됨)

## 논의 기록

(논의하며 정한 것을 날짜와 함께 적습니다)

### 2026-10-09 검토 1단계: 모듈 뼈대 (완료)

1단계 범위는 사용자가 정했다: S3 버킷 연결과 테스트에 필요한 `.env`, `build.gradle.kts`, application 설정.
위 "실제 작업 순서"의 1단계(Claude가 정리한 것)와는 범위가 다르다.

**1-1. `.env`와 AWS 설정**
- `.env`의 S3 값 7개와 각각 필요한 이유를 확인했다 (버킷·리전 / 액세스 키 / 엔드포인트·presigned 주소·path-style).
- `S3_ENDPOINT`·`S3_PUBLIC_ENDPOINT`는 빈 값으로, `S3_PATH_STYLE=false`로 주석을 풀었다 (사용자 수정).
- 줄이 없으면 S3Mock 기본값을, 빈 값이면 실제 AWS를 쓰는 구조는 **유지**한다. 시작 로그와 경고가 안전장치다.
- 버킷: **A안(비공개 버킷 + presigned GET)**. 버킷 정책(`Principal: *`, `GetObject`)을 지우고 모든 퍼블릭 액세스 차단을 켠다 (콘솔, 사용자). 코드는 바꾸지 않는다.
- IAM: 팀 계정(`devcos-team05`) 키를 그대로 쓴다. `AmazonS3FullAccess`로 필요한 권한은 충분하다. 그룹 권한으로 EC2·VPC·IAM 전체 권한도 가진다는 점을 확인했다.
- CORS는 프론트가 붙을 때 확인한다. 포트 8082는 서비스 포트를 모은 뒤 충돌이 나면 정리한다. (2026-10-10 payout-service와 겹쳐 8083으로 정리)

**1-2. `build.gradle.kts`**
- AWS 의존성 4개(SDK BOM, `s3`, `testcontainers-junit-jupiter`, `s3mock-testcontainers`)의 역할을 확인했다.
- QueryDSL은 지금 쓰는 곳이 없지만 다른 서비스와 같은 뼈대를 위해 **유지**한다.
- Claude가 뺐던 Kafka(`spring-boot-starter-kafka`, `spring-boot-starter-kafka-test`)를 **다시 넣었다**.

**1-3. application 설정**
- Kafka 설정을 cash-service와 같게 넣었다.
  - `application.yaml`: 직렬화
  - dev: `localhost:9092`
  - prod: `${KAFKA_BOOTSTRAP_SERVERS}`
  - test: `listener.auto-startup: false`
  - `compose.yml`: `depends_on: bukang-kafka`
- 테스트는 개인 `.env` 대신 `application-test.yaml`에 고정한 S3Mock 값을 쓴다. 실제 S3에는 요청하지 않는다.
- `./gradlew :file-service:test` 34개가 통과했다 (`S3FileStorageTest` 포함).
- Mock 빈(`FakeFileStorage`)을 쓰는 테스트 코드는 기능을 함께 고칠 때 같이 본다.

**커밋**: Kafka는 PRO-51(`d400acd`)에, 테스트 S3 값은 PRO-53(`b1d4d5c`)에 나눠 담고, 그 위의 브랜치를 rebase했다.

### 2026-10-09 검토 2단계: Linear 이슈 생성 (넘어감)

코드 작업이 아니라서 검토하지 않고 넘어갔다.

### 2026-10-09~10 검토 3단계: S3 연결 코드 (진행 중)

3단계 순서는 사용자가 정했다.

1. 폴더 구조가 DDD에 맞는지, `boundedcontext`로 묶을지
2. `StoredFile` 엔티티: 필드와 애노테이션 확인
3. S3 업로드·조회 주요 코드를 이해할 수 있게 정리 (대화창)
4. 3에서 이해한 내용으로 작성된 코드를 보고 필요하면 스타일대로 수정
5. 직접 파일을 올려 볼 수 있는 업로드 HTML 페이지
6. 테스트 코드

**3-1. 폴더 구조 (완료)**
- `boundedcontext` 폴더로 묶지 않는다. MSA에서는 서비스 하나가 바운디드 컨텍스트 하나이고, 2c19d66(MSA 전환)에서 없앤 구조와 맞춘다.
- `config/`, `dto/`는 학원 구조의 `global`·`shared` 자리를 서비스 안으로 옮긴 모양이라 그대로 둔다.
- `toDto()`는 학원 방식대로 엔티티에 둔다 (도메인 → dto 의존은 알고 둔다).

**3-2. `StoredFile` 엔티티 (완료)**
- 용도: `String imageType` → `FileType fileType` (컬럼 `file_type`). 처음 주신 DDL처럼 "엔티티별 Enum"으로 되돌렸다.
  - `FileType`, `FileKind`는 product-service와 같은 값을 쓰도록 `common/shared/file/domain`에 둔다.
  - `FileType`: `THUMBNAIL`(대표 이미지, 1개), `DETAIL`(대회 소개 이미지, 5개), `COURSE`(코스 안내 이미지, 7개: 종목 유형 합계), `INTRO_VIDEO`(대회 소개 영상, 1개), `ROSTER`(크루원 명단, 1개). `DEFAULT`는 정책에 없어 뺐다.
  - `FileKind`: `IMAGE` 10MB, `VIDEO` 100MB, `EXCEL` 5MB (파일 하나당). 100MB를 넘는 동영상은 나중에 멀티파트 업로드로 넓힌다.
  - 화면·메시지용 이름은 `label` 필드 (`FileStatus`도 `korean` → `label`).
  - `FileStatus`, `ImageFormat`은 file-service 안에 둔다. 다른 서비스가 상태로 동작을 나누기 시작하면 `FileStatus`를 common으로 옮기자고 다시 논의한다.
- 용도는 업로드 URL 발급 때 받는다(A안). 연결 때 보내는 용도는 발급 때와 같아야 하고, 용도별 최대 개수를 넘으면 400.
- 동영상·엑셀은 형식 검사(`ImageFormat`)가 아직 이미지만 알아서, 지금은 발급 요청을 400으로 거절한다. (3-4에서 `FileFormat`으로 바꿔 허용했다)
- 등록자·수정자: `createUser`, `updateUser` (int). 상태를 바꾸는 메서드가 `updateUser`를 채운다. (3-4에서 응답 `FileDto`에 `createUser`를 넣었다. 수정자(`updateUser` → `modifyUser`)는 판매자·회원 테이블이 따로 있어 "누가 바꿨나"의 정책이 정해지지 않았으므로 엔티티와 응답에서 모두 뺐다. 상태 변경 메서드(`linkTo`, `confirmUpload`, `delete`)의 회원 ID 매개변수도 함께 뺐다)
- 회원 복제본 `FileMember`(`FILE_MEMBER`)를 만들었다. Kafka 리스너는 사용자가 작성한다. FileMember가 없으면 업로드를 막는 검사는 리스너가 동작한 뒤에 넣는다. (3-4에서 지웠다: 업로드 흐름은 `X-Member-Id` 숫자만 쓰고, 회원 존재는 게이트웨이의 JWT 검사로 보장되며, 판매자는 회원 테이블에 없어 업로드를 막는 검사에 쓸 수도 없다)
- Enum 컬럼은 `@Enumerated(STRING)` + `columnDefinition = "varchar(20)"`. MySQL enum 타입도, 허용 값 CHECK 제약도 만들지 않고 허용 값은 애플리케이션이 지킨다 (변환기 방식은 CHECK가 남아서 쓰지 않았다).
- 조회 순서는 Enum에 적은 순서(대표 → 소개 → 코스 → 영상 → 명단)로 Java에서 정렬한다.

**3-3. S3 업로드·조회 코드 정리 (완료)**
- 업로드 방식(서버 경유 / presigned PUT / presigned POST, 큰 파일은 멀티파트)과 조회 방식(공개 URL / presigned GET)을 비교했다.
- SDK 핵심 코드: `presignPutObject`, `headObject`, Range `getObject`, `copyObject`, `deleteObject`, `presignGetObject`.

**3-4. 코드 검토 (진행 중)**
- `S3Properties`·`S3Config` (완료)
  - `S3Client`는 서버가 S3에 직접 하는 일(확인·복사·삭제), `S3Presigner`는 브라우저가 쓸 URL(올리기 PUT, 보기 GET)을 만든다.
  - 시작 로그: "AWS 기본 주소" 대신 실제 AWS 주소 모양을 찍고, presigned URL 주소는 앱 주소와 다를 때만 찍는다.
  - 자격 증명을 빈 하나(`s3Credentials`)로 꺼내 `S3Client`와 `S3Presigner`가 함께 쓴다.
  - 주석의 "비우면"을 ".env에 `S3_ENDPOINT=`처럼 빈 값을 적으면"으로 명확히 했다 (줄이 없으면 S3Mock 기본값).
  - S3 호출 시간 제한(`apiCallTimeout`)은 넣지 않는다.
- `S3FileStorage` (완료): 함수 역할만 정리했다. `presignPut`·`presignGet`은 `S3Presigner`(S3 호출 없음), `head`·`copy`·`delete`·`readFirstBytes`는 `S3Client`.
- 컨트롤러 (2026-10-10)
  - 회원 ID는 `X-Member-Id` 헤더로 받는다. JWT 검사는 게이트웨이가 하고(PRO-20), file-service는 JWT 비밀 키도 검사 코드도 갖지 않는다.
  - 사용자가 `MemberIdHeader` 상수 클래스를 지우고 `"X-Member-Id"` 문자열을 직접 쓰도록 바꿨다. 테스트도 맞췄다.
- 동영상·엑셀 업로드 허용 (2026-10-10, `issue`는 사용자가 직접 고침)
  - `ImageFormat` → `FileFormat`. 형식마다 `FileKind`와 메시지용 `label`을 붙였다. DB 컬럼이 아니라서(`contentType` 문자열만 저장) DB 영향은 없다.
  - 받는 형식: 이미지 JPG·PNG·WebP, 동영상 MP4(`video/mp4`)·MOV(`video/quicktime`), 엑셀 XLSX·XLS.
  - 매직 바이트
    - MP4·MOV: 4~7바이트 `ftyp` + 8~11바이트 브랜드. 신고한 형식과 정확히 같아야 한다 (MOV는 `qt  `, MP4는 `isom`·`iso2`·`mp41`·`mp42`·`avc1`). M4A 오디오, HEIC 사진 등 `ftyp`를 쓰는 다른 형식은 막힌다.
    - XLSX: ZIP(`PK 03 04`), XLS: 옛 오피스 형식(`D0 CF 11 E0 …`)까지만 확인한다. docx·doc 등도 같은 값이라, 진짜 엑셀인지는 명단을 읽을 때 확인한다.
  - `issue`: 용도의 종류(`FileKind`)와 같은 형식만 받는다(`fromContentType(...).filter(kind)`). 메시지는 "이미지 파일은 JPG, PNG, WebP만 올릴 수 있습니다." 형태로 종류마다 만들어진다.
  - 모든 종류에 형식이 생겨 "아직 올릴 수 없는 종류" 검사(`supports`)와 Swagger 예시(`UNSUPPORTED_KIND`)는 지웠다.
  - 업로드는 presigned PUT 한 번으로 한다(5GB까지 가능). S3는 요청이 시작될 때 만료를 보므로 10분 안에 시작하면 100MB도 끝까지 올라간다.
  - `ImageFormatTest` → `FileFormatTest` (7개): 동영상(MP4·MOV 구분, M4A 거절), 엑셀(XLSX·XLS), 짧은 앞부분, `labelsOf`, "모든 종류에 형식이 하나 이상 있다"(뺀 `supports` 대신). 테스트용 앞부분 바이트는 `TestImages`에 추가했다.
- 이름 변경 (2026-10-10): `issue`가 Linear "이슈"와 헷갈려서 "발급"을 `create`로 바꿨다. `issueUploadUrl` → `createUploadUrl`(컨트롤러·Facade), `FileUploadUseCase.issue` → `create`(`complete`와 짝), `ISSUE_*` 예시 상수 → `CREATE_*`, 테스트 도우미 `issue`·`issueAndUpload` → `create`·`createAndUpload`. 한글 설명("업로드 URL 발급")은 그대로 둔다.
- 업로드 흐름 (2026-10-10): `createUploadUrl`(PENDING, `uploads/`) → 브라우저가 S3에 PUT → `completeUpload`(복사·검사 후 UPLOADED, `files/`) → product-service가 `link`(ACTIVE)
  - 브라우저는 PUT이 끝난 뒤 `complete`를 한 번 부른다. S3는 올라오는 중인 객체를 보여 주지 않아서, 업로드 중에 불러도 "업로드된 파일이 없습니다"(400)로 끝난다. 폴링은 필요 없다.
  - UPLOADED는 "검사 통과, 대상에 아직 안 붙음"(올린 사람만 조회), ACTIVE는 "대상에 붙음"(누구나 조회, `findByRef`에 나옴).
  - presigned URL은 만료가 있어서 DB에는 키만 저장하고, 보기 URL은 조회할 때마다 Facade의 `toDto`가 만든다.
  - `head`는 버킷에 `s3:ListBucket` 권한이 없으면 없는 객체에 404 대신 403을 받는다. IAM 정책에서 확인이 필요하다.
- 컨트롤러·Facade·UseCase 수정 (2026-10-10)
  - `findById` 403 메시지: "연결된 파일이 아닙니다(현재 상태: …). 본인이 올린 파일만 볼 수 있습니다." (조건은 그대로)
  - 응답 `FileDto`에 `createUser`를 넣었다. 수정자는 뺐다(3-2 참고).
  - `link` 오류 메시지에 문제가 된 요청 항목을 붙였다: "… (fileId: 12, fileType: DETAIL, sortNo: 0)". 용도가 다르면 업로드 때 용도도 보여 준다.
  - `link`를 내부 API로 나눈 이유: 대상(대회)의 주인을 file-service가 모르고, 목록 전체를 갈아 치우는 API라 브라우저가 부르면 남의 대회 파일을 지울 수 있다.
  - completeUpload URL을 응답에 넣는 것(HATEOAS)은 팀 API와 모양이 달라져 넣지 않는다.
- 대상 종류(refType) (2026-10-10)
  - 원래 검사(`^[A-Za-z][A-Za-z0-9]{0,49}$`)는 `"race"`(소문자)를 막지 못했다.
  - 보내는 쪽: common에 `FileRef`를 두었다. 생성자를 막고 `FileRef.of(저장된 엔티티)`로만 만들어, 대상 종류가 항상 `getModelTypeCode()`(클래스 이름)에서 온다. Enum 목록 방식은 쓰지 않기로 했다.
  - 받는 쪽: `link`에 클래스 이름 규칙 `^[A-Z][A-Za-z0-9]{0,49}$`로 검사한다(대문자 시작, 컬럼 길이 50자). `"race"`, `"race-event"`는 400.
  - `link`는 브라우저가 부르지 않는 내부 API라 개발자 실수만 잡으면 된다.
- 주석 정리 (2026-10-10): `FileUploadUseCase`(줄 끝 주석을 위 줄로, 사실과 다른 곳 수정), `FileFacade`, `FileLinkUseCase`("정리 배치가 지운다" → 배치는 아직 없다)
- Swagger 예시 클래스(`FileApiExamples`, `FileInternalApiExamples`)는 남긴다. 자동 생성은 필드 타입까지만 보여 주고, 실제 상태 코드와 원인별 메시지는 예시로만 알 수 있다(팀 Swagger 컨벤션).
- `FileMember` 삭제 (2026-10-10): 쓰는 곳이 없었다. 이유는 3-2 기록 참고.

**3-5. 업로드 HTML 페이지 (완료, 2026-10-10)**
- `file-service/src/main/resources/dev-static/upload-test.html` → `http://localhost:8083/upload-test.html` (페이지와 API가 같은 주소라 API에는 CORS가 필요 없다)
  - `application-dev.yaml`의 `spring.web.resources.static-locations`에 `dev-static`을 더해 dev 프로파일에서만 연다. prod에서는 열리지 않는다.
- ①발급 → ②S3 PUT(진행률) → ③완료 확인 → ④연결(내부 API) → ⑤목록·단건 조회. ②·③을 직접 누르는 모드, 연결 때 보낼 용도·순서 변경, 요청·응답 기록 포함.
- 버킷 CORS: `AllowedOrigins: http://localhost:8083`, `AllowedMethods: PUT, GET`, `AllowedHeaders: content-type`. 버킷 정책과는 별도 설정이다. (테스트할 때는 8082였고, 포트 변경 뒤 8083으로 바꿔야 한다)
- 로컬 MySQL의 옛 `file_file`(10-09 E2E 테스트 행 5개)을 지우고 새 구조로 다시 만들었다. `file_member`는 원래 없었다.
- 사용자가 실제 S3 버킷으로 업로드·연결·조회를 확인했다. 대표 이미지 3장 업로드는 성공하고, 개수 제한은 연결 때 400으로 걸린다(업로드 때는 대상이 없어 셀 수 없다).
- 10-09 E2E 때 Claude가 실제 버킷에 올린 객체(`uploads/b3092afa-…png` 등)가 남아 있다. 사용자가 콘솔에서 정리한다.
- 처음에는 `static`에 두었다가, 커밋 전에 dev 전용(`dev-static`)으로 옮겼다(사용자 결정).

**3-6. 테스트 코드 (2026-10-10)**
- 구조 설명: API 테스트는 컨트롤러~UseCase는 진짜 코드, DB는 H2, S3는 `FakeFileStorage`(`@TestConfiguration` + `@Primary`로 바꿔 끼움). `FileStorage` 인터페이스가 있어서 가능하다. 상태가 이어지는 흐름이라 Mockito Mock 대신 Fake를 쓴다. 서명·CORS·IAM은 테스트로 확인하지 못하고 실제 버킷으로 확인한다(3-5).
- 추가한 테스트
  - `ApiV1FileUploadTest`(15 → 21): 용도에 맞지 않는 형식, 동영상 발급(MOV 100MB 성공·초과 400), 엑셀 발급(XLSX·XLS), 동영상 완료(MP4·MOV), MOV로 신고하고 MP4를 올리면 거절, 엑셀 완료(XLSX·XLS). 요청 도우미가 fileType을 받도록 바꿨다.
  - `FileLinkAndQueryTest`(11 → 13): 용도별 최대 개수 초과, 업로드 때와 다른 용도
  - `FileRefTest`(3, file-service에 둠): 클래스 이름·ID로 만들기, 연결 API 규칙 통과, 저장 전 엔티티 거절

**확인**: `./gradlew :file-service:test` 49개 모두 통과(Docker를 켜서 S3Mock 4개 포함), Checkstyle 경고 0건.

### 2026-10-10 커밋과 dev 기준 rebase

- 3단계 변경은 PRO-56 브랜치에 이어서 커밋 4개로 담았다: common 계약(FileType·FileKind·FileRef) / file-service 코드·테스트 / dev 전용 테스트 페이지 / 문서.
  한 파일에 여러 목적이 섞여 있어 더 잘게 나누지 않았다. 각 커밋은 빌드·테스트가 통과하게 묶었다.
- 팀 통합 브랜치는 dev다(PR #5·#7~#9가 dev로 머지됨). 그래서 스택 전체를 origin/main 기준에서 **origin/dev 위로 rebase**했다.
- dev에 정산이 payout-service 모듈로 분리되어 들어오면서 `settings.gradle.kts`, `compose.yml`, `Dockerfile`, `backend/CLAUDE.md`·`README.md`가 충돌했다. 양쪽을 모두 남겼다.
- **포트**: payout-service가 8082를 먼저 써서 file-service를 **8083**으로 바꿨다. 뼈대 커밋(PRO-51)에서부터 8083이다.
- PR은 이슈별로 쌓아서 연다: dev ← PRO-51 ← PRO-53 ← PRO-55 ← PRO-56. 푸시·PR 초안은 확인받은 뒤 진행한다.
- 옮기기 전 상태는 로컬 태그 `backup-before-dev-rebase`에 남겼다. `backup/PRO-56` 브랜치와 stash는 푸시가 끝난 뒤 지운다.

**남은 것**
- 버킷 CORS의 `AllowedOrigins`를 `http://localhost:8083`으로 바꾼다 (사용자 콘솔 작업).
- product-service(사용자가 담당)에서 `link`를 부를 때: 대상은 common의 `FileRef.of(저장된 엔티티)`로 만들고, owner는 브라우저 본문이 아니라 받은 `X-Member-Id`를 넣는다.
- product-service ↔ file-service 일관성 (saga 패턴 거리)
  - 순서: 대회 저장(ID 확보) → `link` 호출 → 커밋. `link`가 실패하면 대회 저장을 롤백한다(지금 설계로 가능).
  - `link`는 성공했는데 product-service 커밋이 실패하면, 파일이 없는 대회 ID에 ACTIVE로 남는다. 되돌릴 보상 작업이 지금은 없다.
  - 고쳐 저장하던 대회라면 더 나쁘다: 목록에서 뺀 기존 파일이 이미 DELETED가 되어, 원래 목록을 다시 보내도 404로 복구되지 않는다 (README 검증에서 찾음).
  - `link(대상, [])`(빈 목록)는 파일을 DELETED로 만들어서 보상으로 쓸 수 없다. 보상하려면 "연결 해제 → UPLOADED로 복구" API가 따로 필요하다.
- 3단계 변경은 모두 커밋하지 않은 상태로 `feat/PRO-56-file-link-api` 작업 트리에 있다. 담을 브랜치를 정해야 한다.
- 로컬 MySQL의 `file_file` 테이블에 옛 컬럼(`image_type`, `regist_user`)이 남아 있어, 앱 실행 전에 테이블을 지워야 한다.
- 새 검사(용도에 맞지 않는 형식, 최대 개수, 용도 불일치)와 동영상·엑셀 발급·완료는 3-6에서 테스트를 쓴다.
- MP4 브랜드 범위를 넓힐지 (M4V, 소니 XAVC 등).
- 정책 논의 (판매자·회원 테이블이 따로 있음)
  - 수정자 기록을 다시 넣을지, 넣는다면 판매자 ID와 회원 ID를 어떻게 구분할지
  - 관리자 여럿이 같은 대상을 고칠 때: 지금 `link`는 이미 연결된 파일도 `ownerId`와 `createUser`가 같아야 해서, 다른 관리자가 올린 파일이 있으면 403이 난다 (검사 순서를 "이미 이 대상에 연결됨"이 먼저 오게 바꾸면 해결)
  - 크루원 명단(ROSTER)처럼 민감한 파일의 공개 범위 (지금은 연결되면 누구나 조회 가능)
- ~~정리 작업이 아직 없다. 대상: 오래된 UPLOADED(연결 안 된) 파일, 남은 `uploads/` 객체, DELETED 파일의 `files/` 객체(`link`에서 목록에 빠져 DELETED가 돼도 S3 객체는 남는다).~~
  (PRO-57 정리 배치로 해결, [BATCH-ROADMAP.md](BATCH-ROADMAP.md). 행 없이 남은 객체는 아직 지우지 않는다)
- 프론트: `file.type`이 빈 값일 때(브라우저가 모르는 확장자) 확장자로 보완한다.
- `backup/PRO-56` 브랜치와 stash 정리.
