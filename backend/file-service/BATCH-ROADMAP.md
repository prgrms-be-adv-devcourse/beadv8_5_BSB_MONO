# 파일 정리 배치 로드맵 (PRO-57)

연결되지 않거나 지워진 파일을 S3와 DB에서 정리하는 배치의 기준과 작업 순서를 적은 문서입니다.
file-service 1~3단계 작업 기록은 `ROADMAP.md`에 있습니다. 기준을 바꿀 때는 아래 "논의 기록"에 먼저 적고 고칩니다.

- **[결정됨]**: 사용자와 함께 정한 것
- **[Claude 제안]**: Claude가 제안하고 사용자가 반대하지 않은 것 (2026-10-11 모두 구현했다)

구현과 테스트를 마쳤습니다 (2026-10-11, 커밋 전). 진행 상태는 6장, 6장의 순서는 shrimp-task-manager로 쪼개어 진행했습니다.

## 1. 배경

- PENDING·UPLOADED·DELETED 파일이 S3와 DB에 쌓입니다.
- 정책 1차 2장 3-4("대회 소개 이미지와 자주 묻는 질문은 판매 중에도 고칠 수 있다")에 따라 소개 이미지를 바꾸면 `link`가 이전 파일을 DELETED로 바꿉니다.
  그래서 DELETED 파일은 정상 사용 중에도 계속 생깁니다.
- 근거
  - Linear PRO-57 "연결되지 않은 이미지 정리 배치 만들기"
  - FigJam Page 2 「09 시스템 아키텍처」 ⑩ "이미지 공개 범위 · 업로드 검증 · 악성 파일 검사 · 고아 객체 정리"
    ([보드](https://www.figma.com/board/qyssISq5c48Pl6YM06dUxF?node-id=361-9284))
- 정책에는 정리 기준이 없습니다.
  - FigJam Page 1 「06 정책 최종안 · 1차」 2장 "이미지 규격"에는 형식·크기·장수만 있습니다.
  - 보드의 보관 기간 규정(5년·3년·1년)은 개인정보·결제·명단 내려받기 기록만 다룹니다.
  - 그래서 아래 기준을 사용자와 정했습니다.

## 2. 상태별로 남는 것

`FileUploadUseCase.complete`와 `FileLinkUseCase.link` 코드를 기준으로 정리했습니다.

| 상태 | 생기는 경우 | S3에 남은 객체 |
|---|---|---|
| PENDING | 업로드 URL만 받고 완료(`complete`)를 부르지 않음 | `uploads/…` (브라우저가 올렸을 때만) |
| UPLOADED | 확인은 끝났지만 대상에 연결하지 않음 | `files/…` |
| DELETED (교체) | `link`에서 목록에서 빠짐 (DB 상태만 바뀐다) | `files/…` |
| DELETED (검사 실패) | `complete`에서 형식·크기가 신고와 다름 | 없음 (`complete`가 이미 지운다) |

`s3_key` 컬럼에는 PENDING과 검사 실패로 DELETED가 된 파일이면 `uploads/…`, 검사를 통과한 파일(UPLOADED, ACTIVE, 교체로 DELETED)이면 `files/…`가 들어 있습니다.
배치는 상태와 상관없이 `s3_key`의 객체를 지우면 됩니다. 이미 없는 키를 지워도 S3는 성공으로 응답합니다.

## 3. 정한 규칙

### [결정됨] (2026-10-10)

- 대상: PENDING, UPLOADED, DELETED. 연결된 파일(ACTIVE)은 지우지 않습니다.
- 기준: 마지막으로 바뀐 뒤 1일이 지난 파일
- 처리: S3 객체를 지우고 DB 행도 지웁니다. 상태(예: PURGED)를 더하지 않습니다.
- 주기: 매일 05:00 (Asia/Seoul)
- 정책에 정리 기준이 없으므로, 이 규칙을 file-service의 정책으로 삼습니다.
- Spring Batch로 만듭니다. 실행 기록(메타데이터)은 DB에 남기고(`spring-boot-starter-batch-jdbc`), 테이블 이름은 기본값 `BATCH_`를 씁니다.
- Step은 학원 방식(Tasklet 반복)을 따릅니다. 4장 참고.

### [Claude 제안] (2026-10-11 모두 구현)

- 기준 시각은 `modify_date`(마지막으로 바뀐 시각)로 잡습니다.
  `create_date`로 잡으면, 오래전에 올렸다가 방금 교체되어 DELETED가 된 파일이 바로 지워집니다.
  인덱스도 `idx_file_status_create(status, create_date)`를 `idx_file_status_modify(status, modify_date)`로 바꿨습니다.
- 기준 시각 = Job 파라미터 `runDateTime` − 1일. 보관 기간(1일)은 상수로 둡니다.
  실행 시각을 파라미터로 받으면 테스트에서 시각만 바꿔 "하루 지남"을 만들 수 있습니다.
  - 전제: JVM 시간대가 `Asia/Seoul`이어야 `runDateTime`(서울 시각)과 `modify_date`(JVM 시각)가 같은 시계가 됩니다.
    컨테이너는 `backend/Dockerfile`의 `ENV TZ=Asia/Seoul`로 맞춥니다 (논의 기록 "시간대")
- 한 번에 100건씩 처리합니다 (학원 프로젝트는 10건).
- 매번 첫 페이지를 조회합니다 (학원 방식). 지운 행은 다시 조회되지 않으므로 건너뛰는 행이 없습니다.
- DB 행을 먼저 지우고, 커밋한 뒤 S3 객체를 지웁니다.
  - 행은 "아직 같은 상태이고 기준 시각보다 오래됐을 때"만 지웁니다 (조건부 삭제).
    후보를 조회한 뒤 그 파일이 연결(ACTIVE)되면 지운 행이 0개가 되므로 S3 객체도 지우지 않습니다.
  - 순서를 반대로 하면(S3 먼저), 조회한 뒤 연결된 파일의 본문이 사라질 수 있습니다.
  - 대신 S3 삭제가 실패하면 행 없이 객체만 남습니다. 로그로 남깁니다.
- 커밋한 뒤 S3를 지우는 방법 (사용자 결정: Facade + `TransactionTemplate`)
  - `FileFacade.cleanupMore`는 `@Transactional(propagation = NOT_SUPPORTED)`로 트랜잭션 없이 실행합니다.
    클래스에 붙은 `readOnly` 트랜잭션이 메서드 전체를 감싸면, 행 삭제가 거기에 합쳐져 메서드 끝(S3 삭제 뒤)에야 커밋되기 때문입니다.
  - 행 삭제는 `transactionTemplate.execute(...)` 안에서만 실행합니다. `execute()`가 돌아올 때 이미 커밋되어 있고, 그 다음 줄에서 S3를 지웁니다.
  - 트랜잭션을 Facade가 정한다는 file-service 관례를 지킵니다. 같은 클래스 안의 `@Transactional` 메서드를 부르면 프록시를 거치지 않아 트랜잭션이 열리지 않으므로, 메서드를 나누는 방법은 쓰지 않습니다.
  - Tasklet에 트랜잭션 매니저를 넘기지 않습니다. Spring Batch 6은 이때 `ResourcelessTransactionManager`(실제 트랜잭션 없음)를 씁니다.
    JPA 트랜잭션 매니저를 넘겨도 `cleanupMore`가 `NOT_SUPPORTED`로 바깥 트랜잭션을 잠시 멈추므로 순서는 지켜집니다.
    다만 쓸모없는 바깥 트랜잭션이 생기므로 넘기지 않습니다.
- 반복은 지운 건수가 0이면 끝납니다 (사용자 결정). 한 묶음 100건이 모두 `link`·`complete`와 겹쳐 하나도 못 지우면
  그날은 일찍 끝나고 남은 파일은 다음 날 지워집니다. 그 대신 "조회 건수 + 지운 키"를 담는 record가 필요 없습니다.
- S3 객체는 `DeleteObjects`로 한 묶음(100건)을 요청 한 번에 지웁니다 (사용자 결정, 요청 하나에 최대 1000개).
  일부만 실패하면 응답의 `errors`에 키별로 담겨 오므로, 실패한 키만 로그로 남기고 나머지는 지워집니다.
- 파일 하나가 실패해도 나머지는 계속 처리하고, 처리 건수와 실패 건수를 로그로 남깁니다.
- `DeleteObjects` 요청 자체가 실패하면(네트워크, 권한 등) 행만 지워진 키를 로그로 남기고 예외를 다시 던져 배치를 멈춥니다.
  다음 묶음도 실패할 가능성이 높아, 계속 돌면 행만 지워지고 객체가 쌓이기 때문입니다. Job은 FAILED로 기록됩니다.
- ShedLock(여러 서버 중 한 대만 실행)은 쓰지 않습니다. file-service는 지금 한 대입니다.
- `uploads/` S3 수명 주기 규칙은 보류합니다 (버킷에 설정되어 있지 않음, 2026-10-11 사용자 확인). PENDING을 정리할 때 그 행의 `uploads/` 객체도 함께 지웁니다.
  배치가 지우지 못하고 행 없이 남는 객체는 아래 세 경우입니다.
  - `complete` 도중 서버가 죽은 경우: `files/`로 복사한 뒤 커밋 전에 죽으면 행은 PENDING(`uploads/` 키)으로 롤백되어 `files/` 사본이 남습니다
  - `complete`가 끝난 뒤 같은 업로드 URL로 다시 올린 경우: URL은 만료(10분) 전까지 다시 쓸 수 있어 `uploads/` 객체가 생깁니다.
    수명 주기 규칙이 있으면 이 경우를 지울 수 있습니다
  - 배치의 S3 삭제가 실패한 경우 (로그에 키가 남습니다)

## 4. 참고 프로젝트 (학원 방식)

경로: `D:\Study\K-training-mission\bep4-1-mission` (Spring Boot 4.0.1, Spring Batch 6)

흐름:

```
PayoutScheduler (@Scheduled)
  └ jobOperator.start(job, runDateTime=지금)
      └ Job: Step1 → Step2 (start → next)
           └ Step = Tasklet 하나
                ① facade.xxxMore(10) 호출  ← @Transactional, 10건 처리하고 커밋
                ② 처리 건수가 0이면 FINISHED (Step 끝)
                ③ 아니면 CONTINUABLE → Spring Batch가 ①을 다시 부름
```

| 참고 프로젝트 | 우리 프로젝트 |
|---|---|
| `in/PayoutCollectItemsAndCompletePayoutsBatchJobConfig`: Job → Step(Tasklet) → `facade.xxxMore(N)`, CONTINUABLE/FINISHED | 따릅니다. Step은 하나(`fileCleanupStep`)이고, 한 번에 100건 |
| `app/…MoreUseCase`: `PageRequest.of(0, limit)`로 매번 첫 페이지 조회 | 따릅니다. 지운 행은 다시 조회되지 않습니다 |
| `in/PayoutScheduler`: `jobOperator.start(job, runDateTime)`, cron + `zone = "Asia/Seoul"`, `@Profile("prod")` | 따릅니다(`@Profile("prod")` 포함). `runDateTime`은 문자열이 아니라 `addLocalDateTime`으로, `Asia/Seoul` 시각을 넣습니다 (JVM 시간대도 `Asia/Seoul`이어야 한다, 3장 전제) |
| `global/batch/BatchConfig`: `@EnableBatchProcessing` + `@EnableJdbcJobRepository`, prod가 아니면 `schema-h2.sql`을 직접 실행 | 따르지 않습니다. `spring-boot-starter-batch-jdbc`와 `spring.batch.jdbc.initialize-schema`(프로파일별)로 대신합니다. 우리 dev는 MySQL이라 H2 스크립트가 맞지 않습니다 |
| `application.yaml`: `spring.batch.job.enabled: false` | 따릅니다. 없으면 앱을 켤 때마다 Job이 실행됩니다 |
| `in/PayoutDataInit`: dev에서 앱을 켤 때 Job을 한 번 실행 (`runDate=오늘`) | 따르지 않습니다. 켤 때마다 파일이 지워지므로 6장 ⑤의 Job 테스트(`FileCleanupJobTest`)로 확인합니다 |

## 5. 테스트 방식

- 기존 file-service 테스트처럼 H2와 `FakeFileStorage`를 씁니다.
  `S3FileStorage.deleteAll`(`DeleteObjects`)만 Docker의 S3Mock 컨테이너로 시험합니다 (Docker가 없으면 건너뜀).
- DB 시각을 직접 고치지 않고, 기준 시각(`cutoff`)이나 Job 파라미터 `runDateTime`을 하루 넘게 옮겨 "1일 지남"을 만듭니다.
- 테스트 목록은 6장 ⑤에 있습니다.

## 6. 진행 순서

사용자가 정한 순서(①~④)에 단계마다 만들 것과 확인을 붙였습니다.
④ 뒤에 ⑤ 테스트 코드 작성을 따로 둡니다. 그래서 ②~④에서는 테스트를 쓰지 않고 컴파일과 기존 테스트 통과만 확인합니다.
단계마다 사용자에게 확인받고 진행합니다.

**진행 상태 (2026-10-11)**: ①~⑥ 완료(커밋 전). ⑦ 마무리만 남았습니다. file-service 테스트 61개 통과, checkstyle 0건.

### ① 의존성·yaml

- `file-service/build.gradle.kts`
  - `spring-boot-starter-batch-jdbc` (실행 기록을 DB에 남기는 Spring Batch)
  - 테스트용 `spring-boot-starter-batch-jdbc-test` (Maven Central에 4.1.1 있음 확인)
- `application.yaml`: `spring.batch.job.enabled: false`
- `spring.batch.jdbc.initialize-schema` (메타 테이블 만들기)
  - dev: `always`
  - test: H2라 기본값으로 자동 생성
  - prod: `always` (사용자 결정). `BATCH_` 테이블은 JPA 엔티티가 아니라서 `ddl-auto` 값과 상관없이 Spring Batch 스크립트로 만든다.
    처음 켤 때 만들고, 다음부터는 이미 있다는 오류를 무시한다(`continue-on-error` 기본값 `true`). DB 계정에 CREATE 권한이 필요하다
- 확인: 기존 테스트가 통과하는지. dev 실행 때 bukang-db에 `BATCH_` 테이블(9개: 기록 6개, 시퀀스 3개)이 생기는지는 ③에서 확인했다

### ② 삭제 부품 (DB와 S3의 일관성)

- `domain/StoredFile`: 인덱스를 `idx_file_status_modify(status, modify_date)`로 바꿉니다.
  - dev(`ddl-auto: update`)는 새 인덱스를 더하지만 옛 인덱스는 지우지 않습니다.
    2026-10-11 확인해 보니 로컬 dev DB에는 옛 `idx_file_status_create`가 원래 없어서 지울 것이 없었습니다.
    다른 팀원의 DB에 남아 있으면 `ALTER TABLE file_file DROP INDEX idx_file_status_create;`로 지웁니다.
  - prod(`validate`)는 인덱스를 검사하지 않습니다. 운영 DB에 테이블을 만들 때 새 인덱스로 만듭니다.
- `out/StoredFileRepository`
  - 후보 조회: `status IN (PENDING, UPLOADED, DELETED) AND modify_date < 기준 시각 ORDER BY id`, 첫 페이지만 limit건
  - 조건부 삭제: 같은 상태이고 기준 시각보다 오래됐을 때만 지우고, 지운 행 수를 돌려줍니다.
- `app/FileCleanupUseCase`와 `FileFacade`의 `cleanupMore(limit, cutoff)`
  - `deleteOldRows`: 후보를 읽어 한 건씩 조건부로 지우고, 실제로 지운 행의 S3 키를 돌려줍니다.
    `FileFacade`가 `transactionTemplate.execute(...)` 안에서 부르고, `execute()`가 돌아온 시점에 커밋되어 있습니다 (3장).
  - `deleteObjects`: 트랜잭션 밖에서 S3를 지웁니다. `FileStorage`에 `deleteAll(keys)`(S3 `DeleteObjects`)를 더해 한 번에 지웁니다.
    지우지 못한 키를 돌려받고, 1000개가 넘으면 나눠 요청합니다.
  - 지운 건수를 돌려줍니다.
- 확인: 컴파일, 기존 테스트 통과

### ③ Job·Step 등록

- `in/FileCleanupBatchJobConfig`: `fileCleanupJob` → `fileCleanupStep`
  - Tasklet이 Job 파라미터 `runDateTime`에서 기준 시각(− 1일)을 구해 `cleanupMore(100, 기준 시각)`을 반복해서 부릅니다.
  - `runDateTime`은 `LocalDateTime` 파라미터로 받습니다(`getLocalDateTime`). 학원 프로젝트는 문자열로 넣기만 하고 읽지 않습니다
  - 지운 건수가 0이면 FINISHED, 아니면 `contribution.incrementWriteCount(지운 건수)` 후 CONTINUABLE
  - Tasklet에 트랜잭션 매니저를 넘기지 않습니다(`tasklet(Tasklet)`, Batch 6.0에서 생김). 이때 `ResourcelessTransactionManager`를 씁니다 (3장).
- 확인 (2026-10-11 완료)
  - 컴파일, 기존 테스트 49개 통과
  - dev(MySQL)로 두 번 켰습니다. 이 확인에서만 실행 인자로 compose 자동 실행을 끄고 S3를 로컬 S3Mock으로 바꿨습니다 (설정 파일은 그대로)
    - `BATCH_` 테이블 9개가 생기고, 다시 켜도 오류 없이 뜹니다. 시퀀스 행도 1개씩으로 중복되지 않습니다
    - 켤 때 Job이 실행되지 않습니다 (`BATCH_JOB_EXECUTION` 0건, 로그에 Job 실행 없음)
    - 새 인덱스 `idx_file_status_modify`가 생겼습니다

### ④ 스케줄러 (매일 05:00)

- `FileApplication`: `@EnableScheduling`
- `in/FileCleanupScheduler`
  - `@Scheduled(cron = "0 0 5 * * *", zone = "Asia/Seoul")`
  - `runDateTime`에 지금 시각을 `addLocalDateTime`으로 넣어 `jobOperator.start(fileCleanupJob, 파라미터)`
  - `@Profile("prod")` (사용자 결정). 개발자 PC에서 05:00에 `.env`가 가리키는 S3(실제 버킷일 수 있다)를 지우지 않게 한다
  - Job은 스케줄러 스레드에서 끝까지 돈 뒤 돌아온다(기본 `SyncTaskExecutor`). FAILED로 끝나면 `log.error`
  - 시작 자체가 거절되면(`JobExecutionException`: 이미 완료, 실행 중 등) `log.error`
- dev 확인 (사용자 결정)
  - 기본은 ⑤의 Job 테스트(`FileCleanupJobTest`)로 확인한다. 스케줄러 빈은 prod에서만 생기므로 테스트에서는 직접 만들어 부른다
  - 필요하면 사용자가 요청할 때만 스케줄러를 잠시 `dev` 프로파일 + 가까운 cron으로 바꿔 확인하고, 사용자가 확인했다고 하면 되돌린다
- 확인: 컴파일, 기존 테스트 49개 통과 (스케줄러 빈은 test 프로파일에서 만들어지지 않으므로 연결은 ⑤의 `FileCleanupJobTest`에서 확인)

### ⑤ 테스트 코드 작성

H2 + `FakeFileStorage` (`S3FileStorage.deleteAll`만 S3Mock)

- 삭제 로직 (`app/FileCleanupTest`, 2026-10-11 작성. `FakeFileStorage`에 `failOnDelete`, `failDeleteRequest`를 더했다)
  1. 1일이 지난 PENDING·UPLOADED·DELETED 파일은 행과 S3 객체가 지워진다
  2. ACTIVE 파일은 오래돼도 남는다
  3. 1일이 안 된 파일은 남는다
  4. S3 삭제가 일부 실패해도 행은 지워지고 나머지 객체도 지워진다
  5. 후보를 조회한 뒤 상태가 바뀐 파일은 지우지 않는다 (조건부 삭제). `cleanupMore` 중간에 끼어드는 대신 `deleteIfOld`를 직접 불러 확인한다
  - (추가) S3 요청 자체가 실패하면 예외가 나지만 행 삭제는 이미 커밋되어 있다. "커밋한 뒤 S3 삭제" 순서의 증거다
  - (추가) limit건씩 지우고 다시 부르면 남은 파일을 지운다. 늘 첫 페이지를 읽어도 건너뛰는 파일이 없다
- `S3FileStorage.deleteAll` (`S3FileStorageTest`, Docker로 S3Mock. Docker가 없으면 건너뜀)
  - 여러 객체를 한 번에 지운다, 없는 키는 실패로 치지 않는다. S3Mock 5.2.3이 `DeleteObjects`를 받는 것을 확인했다
  - 지울 키가 없으면 빈 결과를 돌려준다
  - 1000개가 넘을 때 나눠 보내는 것은 객체를 1001개 올려야 해서 시험하지 않았다
- Job (`in/FileCleanupJobTest`, `@SpringBatchTest` + `spring-boot-starter-batch-jdbc-test`, 2026-10-11 작성)
  6. `runDateTime`을 넣어 실행하면 COMPLETED이고 파일이 정리된다. `WRITE_COUNT`는 지운 건수다
  7. 같은 파라미터로 다시 실행하면 `JobInstanceAlreadyCompleteException`으로 거절된다 (JOB_KEY가 같다)
  - (추가, 사용자) 스케줄러 연결: 빈은 `@Profile("prod")`라 test 컨텍스트에 없으므로 `new FileCleanupScheduler(jobOperator, fileCleanupJob)`으로 만들어 부른다.
    COMPLETED가 남고, `BATCH_JOB_EXECUTION_PARAMS`에 `PARAMETER_TYPE = java.time.LocalDateTime`, 값은 ISO 문자열로 저장된다
- 확인 (`backend/`에서): `./gradlew :file-service:test`가 모두 통과

### ⑥ 문서·검사

- `app/FileLinkUseCase`의 주석 "S3 객체를 지울 정리 배치는 아직 없다"를 배치 설명으로 고칩니다.
- `README.md` "7. 아직 안 된 것", `backend/CLAUDE.md`의 file-service 구조, 이 문서를 갱신합니다.
- `./gradlew :file-service:checkstyleMain :file-service:checkstyleTest`의 경고를 0건으로 만듭니다.
- S3Mock으로 직접 돌려 볼지는 선택입니다.
- 한 일 (2026-10-11)
  - `FileLinkUseCase` 주석: "1일 뒤 정리 배치(fileCleanupJob)가 행과 S3 객체를 지운다"
  - file-service `README.md`: 1장 상태표 DELETED와 "정리 배치" 설명, 3장 정리 문장, 5장 "1일 안에 연결해야 한다",
    6장 정리 배치 테스트, 7장(정리 작업 항목을 "행 없이 남은 객체"와 "여러 대 실행"으로 바꿈), 문서 목록에 이 문서 링크
  - `backend/CLAUDE.md`: file-service 구조(in·app)와 정리 배치 한 줄. `backend/README.md` "파일 저장소" 표의 `files/` 정리 칸
  - `ROADMAP.md`: 남은 일의 PRO-57, "정리 작업이 아직 없다"에 해결 표시
  - S3는 `S3FileStorageTest`(S3Mock 컨테이너)로 `DeleteObjects`를 확인했고, 사용자 요청으로 실제 버킷에서도 수동 확인했습니다 (논의 기록)
  - 문서 대조: 검증 에이전트 3개가 문서와 코드를 비교해 17건을 찾았다. 16건은 문서를 고쳤고, 1건("1일" 문장)은 시간대를 맞춰 해결했다.
    `backend/README.md`의 `uploads/` 칸은 수명 주기 규칙이 설정되어 있지 않아(사용자) 정리 배치 기준으로 고쳤다
  - 시간대 문제를 찾아 `backend/Dockerfile`에 `ENV TZ=Asia/Seoul`을 넣었다 (논의 기록 "시간대")
  - `docker compose --profile app up`이면 prod로 떠 배치가 돈다는 점은 README에만 적었다 (팀은 IntelliJ나 `docker compose up`만 쓴다, 사용자)

### ⑦ 마무리 (끝에 한꺼번에)

- 단계별 커밋을 정리하고 push합니다: `git push -u origin feat/PRO-57-file-cleanup-batch`
  - 이 브랜치는 origin/dev를 추적하도록 만들어졌으므로 `-u`로 같은 이름의 원격 브랜치를 지정합니다.
- PR을 엽니다. 본문에 `Closes PRO-57`을 넣으면 머지할 때 Done이 됩니다.
- Linear PRO-57의 내용과 상태를 갱신합니다 (초안을 먼저 보여 줌).
- 남은 브랜치(PRO-51·53·55·56)를 원격과 로컬에서 정리합니다.

## 7. 알려진 한계 / 정할 것

- 알려진 한계
  - 1일이 넘은 PENDING 파일에 05:00 정각에 `complete`가 겹치면, `complete`가 실패하고 `files/` 객체가 남을 수 있다. 드물어서 그냥 둔다
  - 1일이 넘은 UPLOADED 파일에 배치와 `link`가 겹치면, 먼저 커밋한 쪽이 이긴다. 어느 쪽이든 "ACTIVE인데 S3 객체가 없는" 상태는 생기지 않는다
    - `link`가 먼저: 조건부 삭제가 0건이 되어 파일이 남는다
    - 배치가 먼저: `link`가 "존재하지 않는 파일"(404)로 실패한다. 배치의 삭제가 커밋되기 전에 `link`가 이미 행을 읽었다면,
      `link`의 UPDATE가 0건이 되어 예외가 나고 핸들러가 없어 500으로 나간다. 아주 드물어서 그냥 둔다
  - DELETED를 1일 두는 것은 product-service 커밋이 실패했을 때 되돌릴 여유가 된다. 다만 되돌리는 보상 API는 아직 없다 (saga, `README.md` 5장 참고)
- 나중에
  - 대회를 삭제하거나 판매를 중지할 때 연결된 파일을 어떻게 할지 product-service와 정한다
  - file-service를 여러 대로 늘리면 ShedLock 같은 중복 실행 방지가 필요하다
  - `uploads/` 수명 주기 규칙을 학원 AWS 계정에서 설정할 수 있는지 확인한다
  - 이 규칙을 FigJam 정책(⑩ 또는 정책 2장)에 남길지 팀과 정한다

## 논의 기록

### 2026-10-10 기준 정하기

- FigJam 보드를 확인했다. Page 1 정책 1차·2차 2장, 09 ERD `race_image`, Page 2 시스템 아키텍처 ⑤⑨⑩을 확인했고, 파일 정리 기준은 없었다
- 정책이 없으면 함께 정하기로 했다 (사용자: "그리 중요한 건 아니니까")
- 사용자가 고른 것: 기준 1일, DB 행 삭제, 매일 05:00
- Claude 제안(반대 없음): `modify_date` 기준, DB 먼저 삭제 후 S3 삭제, ShedLock 없음, `uploads/` 수명 주기 규칙 보류
- 코드는 아직 작성하지 않는다 (사용자 요청). 먼저 이 문서를 만들었다

### 2026-10-10 진행 방식과 Spring Batch

- 이슈·브랜치·Linear 정리는 기능을 다 만든 뒤에 한다 (사용자). 다만 이미 머지된 PRO-56 브랜치에서 작업하지 않도록,
  최신 dev에서 로컬 브랜치 `feat/PRO-57-file-cleanup-batch`만 먼저 만들었다 (push하지 않음)
- 사용자가 정한 구현 순서: ① 의존성·yaml → ② DB·S3 삭제(일관성) → ③ Job·Step 등록 → ④ 스케줄러.
  ④ 뒤에 테스트 코드 작성 단계를 따로 두었다 (사용자 요청)
- `@Scheduled` + UseCase 대신 Spring Batch를 쓴다. 실행 기록은 DB(`BATCH_`)에 남긴다
- Step은 학원 방식(Tasklet 반복)을 골랐다. 학원 방식은 성능이 걱정된다는 의견이 있었지만, 새벽에 돌고 양이 적어 부담이 작다.
  매번 첫 페이지를 읽는 방식은 오히려 삭제 작업과 잘 맞는다. 진짜 병목은 객체마다 나가는 S3 삭제 요청이다
- 확정한 진행 순서는 shrimp-task-manager로 쪼개어 진행한다. 그때도 참고 프로젝트를 본다

### 2026-10-10 ① 의존성·yaml

- prod 메타 테이블은 `initialize-schema: always`로 만든다 (사용자). 배포 때 손으로 할 일이 없고, 다시 켜도 오류를 무시하고 넘어간다.
  고르지 않은 쪽: `never` + `schema-mysql.sql` 수동 실행 (validate 원칙과 맞지만 잊으면 05:00 배치가 실패한다)
- 검증은 컴파일과 기존 테스트만 했다 (사용자). dev 실행으로 테이블을 확인하는 것은 ③으로 미뤘다

### 2026-10-11 ② 삭제 부품

- 조건부 삭제 메서드 이름은 `deleteIfOld`로 정했다 (사용자. 처음 이름은 `deleteIfStale`)
- 주석의 "조회"가 API 조회(`findById` 등)로 읽혀서, "배치가 후보로 읽은 뒤 지우기 전까지"로 고쳤다.
  상태를 바꾸는 것은 `createUploadUrl`, `completeUpload`, `link`뿐이다
- 한 건씩 지우면 S3 요청이 최대 100번 나간다는 질문에, `DeleteObjects`를 "나중에"에서 지금 하는 것으로 당겼다 (사용자: 지금 배우고 싶다)
- DB 행은 계속 한 건씩 조건부로 지운다. `IN (...)`으로 한 번에 지우면 지운 개수만 알 수 있어, 어느 파일의 S3 객체를 지워도 되는지 모른다
- 트랜잭션: Facade에 `@Transactional`을 두면 커밋이 S3 삭제 뒤가 된다. 그래서 `cleanupMore`는 `NOT_SUPPORTED`로 두고
  `TransactionTemplate`으로 행 삭제만 감쌌다 (사용자: B안. UseCase에 `@Transactional`을 두는 A안보다 Facade 관례를 지킨다)
- 반복 종료는 지운 건수 0으로 바꾸고 record를 없앴다 (Claude 제안, 사용자가 고른 B안 코드에 포함)
- `DeleteObjects` 요청 자체가 실패하면 배치를 멈추게 했다 (Claude 제안, 사용자 확인). 일부 키만 실패하면 로그만 남기고 계속한다

### 2026-10-11 ③ Job·Step

- API는 참고 프로젝트가 아니라 우리 classpath의 Spring Batch 6.0.5 jar로 확인했다 (사용자 원칙)
- 보완: `runDateTime`은 `LocalDateTime` 파라미터로 받는다
- 파라미터 검증(`DefaultJobParametersValidator`, 필수 키가 모두 있어야 통과)을 넣었다가 뺐다 (사용자).
  스케줄러가 항상 `runDateTime`을 넣으므로, 빠뜨리는 실수를 막는 방어용 이상의 의미가 없다
- dev 확인은 Claude가 실행했다 (사용자). Kafka를 쓰지 않으므로 compose 자동 실행을 끄고 떠 있는 MySQL에만 붙었다
- 참고 프로젝트의 `runDateTime`(문자열)은 아무도 읽지 않는다. 매번 다른 값을 넣어 JOB_KEY(식별 파라미터의 MD5)를 바꿔
  새 실행으로 인정받는 용도다. 우리는 그 값으로 기준 시각을 계산하므로 `LocalDateTime`으로 넣고 꺼낸다

### 2026-10-11 실제 버킷으로 수동 확인

- 사용자 요청으로 잠시 바꿔 확인하고 되돌렸다: 스케줄러 `@Profile({"prod", "dev"})`, cron `0 */3 * * * *`, `RETENTION` 5분
- 실제 S3 버킷(운영 전이라 사용자가 허락), IntelliJ dev 실행, `upload-test.html`로 UPLOADED 2개·ACTIVE 1개를 만들었다 (03:13)
- 결과 (`BATCH_JOB_EXECUTION`): 03:15 0건, 03:18 0건(기준 03:13:00보다 몇 초 늦게 바뀜), 03:21 2건 지움(COMMIT_COUNT 2). ACTIVE는 남았다
- 3분 간격으로 검사하므로 5분 보관이면 5~8분 뒤에 지워진다. 운영(1일, 05:00)이면 1~2일 뒤다

### 2026-10-11 시간대 (문서 대조에서 발견)

- `modify_date`는 `@LastModifiedDate`가 JVM 기본 시간대로 채우고, `runDateTime`은 `Asia/Seoul`로 넣는다.
  컨테이너는 TZ를 넣지 않으면 UTC다 (같은 PC에서 `bukang-db`는 KST, TZ 없는 `bukang-s3mock`은 UTC로 확인).
  그대로 배포하면 기준 시각이 "UTC 지금 − 15시간"이 되어 1일이 아니라 15시간 뒤에 지워진다
- 리전(서울)은 서버 위치일 뿐 OS·JVM 시간대를 정하지 않는다. EC2 OS도 기본은 UTC다
- `backend/Dockerfile` 실행 단계에 `ENV TZ=Asia/Seoul`을 넣어 모든 서비스 JVM을 한국 시간으로 맞췄다 (사용자: B안).
  다른 서비스의 `createDate`·`modifyDate`도 MySQL(`TZ: Asia/Seoul`)과 같은 시계가 된다. 팀 전체에 영향이 있어 커밋을 따로 나눈다
- 고르지 않은 쪽: 스케줄러가 `LocalDateTime.now()`(JVM 시간대)를 넣는 A안. 둘은 충돌하지 않는다

### 2026-10-11 수명 주기 규칙과 배치

- 버킷에 `uploads/` 수명 주기 규칙은 설정되어 있지 않다 (사용자)
- 사용자 질문: "규칙이 있었으면 배치는 괜히 만든 건가?" → 아니다. 둘은 맡는 일이 다르다
  - 수명 주기 규칙은 S3 객체만 안다. DB 행(PENDING·UPLOADED·DELETED)은 지우지 못한다
  - `files/`에는 연결된 파일(ACTIVE)과 연결 안 된 파일이 섞여 있어, 경로로 거는 규칙을 걸면 쓰는 이미지까지 지운다
  - 규칙의 기준은 객체를 만든 날이다. "DELETED가 된 뒤 1일"처럼 DB 상태가 바뀐 시각을 볼 수 없다
  - 규칙이 맞는 곳은 전부 임시 객체인 `uploads/`뿐이다 (완료 뒤 같은 URL로 다시 올린 객체 등)

### 2026-10-11 ④ 스케줄러

- `@Profile("prod")`를 붙였다 (사용자)
- dev 확인은 ⑤의 Job 테스트(`FileCleanupJobTest`)로 한다 (사용자). 필요하면 사용자가 요청할 때 스케줄러를 잠시 `dev` + 가까운 cron으로 바꿨다가,
  확인했다고 하면 되돌린다
