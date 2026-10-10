# file-service

대회 이미지, 대회 소개 영상, 크루원 명단(엑셀) 파일을 S3에 저장하고, 볼 수 있는 URL을 내주는 서비스입니다 (포트 8083).
파일 본문은 우리 서버를 거치지 않습니다. 브라우저가 서버에서 받은 presigned URL로 S3에 직접 올리고, 직접 받습니다.

- 이 문서: 프론트와 product-service가 API를 붙일 때 보는 사용법
- 실행 환경(S3Mock과 실제 S3, `.env`): [`backend/README.md`의 "파일 저장소"](../README.md#파일-저장소-s3)
- API별 전체 오류 목록과 예시: Swagger (`http://localhost:8083/swagger-ui.html`)
- 작업·논의 기록: [`ROADMAP.md`](ROADMAP.md)
- 파일 정리 배치의 기준과 작업 기록: [`BATCH-ROADMAP.md`](BATCH-ROADMAP.md)

## 1. 한눈에 보는 흐름

```
           브라우저                         file-service                       S3
① 발급      POST /api/v1/file/files   ──▶  형식·크기 검사, 파일 저장(PENDING)
           ◀── uploadUrl, headers, fileId
② 업로드    PUT uploadUrl (파일 본문)  ─────────────────────────────────────▶  uploads/{uuid}.png
③ 완료 확인 POST …/files/{id}/complete ──▶ uploads/ → files/ 복사, 실제 크기·앞부분 검사
           ◀── 파일 정보 + 보기 URL         (UPLOADED)                          files/{uuid}.png

           product-service
④ 연결      PUT /internal/v1/file/refs/Race/5/files ──▶ 대회 5번에 붙임 (ACTIVE)

           누구나
⑤ 조회      GET /api/v1/file/files?refType=Race&refId=5 ──▶ 대회 5번 파일 + 보기 URL
```

| 상태 | 뜻 | 바뀌는 때 |
|---|---|---|
| `PENDING` | 업로드 대기. DB에만 있고 S3에는 아직 없을 수 있다 | ① 발급 |
| `UPLOADED` | 검사를 통과했지만 아직 어느 대상(대회 등)에도 붙지 않았다 | ③ 완료 확인 성공 |
| `ACTIVE` | 대상에 붙었다 | ④ 연결 |
| `DELETED` | 지워짐 | ③ 검사 실패(S3 객체도 지운다), ④ 연결 목록에서 빠짐(DB 상태만 바뀌고 S3 객체는 정리 배치가 지운다) |

파일을 올리는 시점에는 붙일 대회가 아직 없을 수 있어서(대회 등록 화면에서 이미지를 먼저 올림), "올라감"(UPLOADED)과 "대상에 붙음"(ACTIVE)을 나눕니다.

**정리 배치**: 대상에 붙지 않은 파일(`PENDING`, `UPLOADED`, `DELETED`)은 마지막으로 바뀐 뒤 1일이 지나면 매일 05:00 배치가 DB 행과 S3 객체를 함께 지웁니다.
`ACTIVE`는 지우지 않습니다. prod에서만 돌고, 기준과 동작은 [`BATCH-ROADMAP.md`](BATCH-ROADMAP.md)에 있습니다.

## 2. 단계별 요청과 응답

이 문서에 적은 성공·오류 응답은 모두 `{ "status": 상태 코드, "message": 메시지, "data": 내용 }` 형식이고, 오류일 때 `data`는 `null`입니다.
예상하지 못한 서버 오류(S3 호출 실패 같은 500)는 Spring 기본 오류 형식으로 나갈 수 있습니다.

**회원 ID**: `X-Member-Id` 헤더로 받습니다. 게이트웨이(PRO-20)가 JWT를 검사한 뒤 이 헤더를 넣어 줄 예정이라 file-service는 JWT를 보지 않습니다.
게이트웨이가 생기기 전인 지금은 헤더에 회원 번호를 직접 넣어 테스트합니다.

### ① 업로드 URL 발급

`POST /api/v1/file/files` · 헤더 `X-Member-Id` 필수

| 필드 | 설명 | 예시 |
|---|---|---|
| `fileType` | 파일 용도. 화면의 어느 칸에서 골랐는지로 프론트가 정한다 ([3. 파일 종류](#3-파일-종류)) | `"THUMBNAIL"` |
| `fileName` | 원본 파일명 (255자 이하) | `file.name` |
| `contentType` | 파일 형식 | `file.type` (빈 값이면 [확장자로 보완](#파일-형식을-신고할-때)) |
| `fileSize` | 파일 크기 (바이트, 0보다 큼) | `file.size` |

```json
{ "fileType": "THUMBNAIL", "fileName": "race.png", "contentType": "image/png", "fileSize": 204800 }
```

응답 `201` (`Location: /api/v1/file/files/{fileId}` 헤더도 붙음)

```json
{
  "status": 201,
  "message": "업로드 URL을 발급했습니다.",
  "data": {
    "fileId": 1,
    "uploadUrl": "https://{버킷}.s3.ap-northeast-2.amazonaws.com/uploads/3f2a….png?X-Amz-Signature=…",
    "method": "PUT",
    "headers": { "content-type": "image/png", "content-length": "204800" },
    "expiresAt": "2026-10-10T12:35:35Z"
  }
}
```

| 오류 | 메시지 예 |
|---|---|
| 400 용도에 맞지 않는 형식 | 이미지 파일은 JPG, PNG, WebP만 올릴 수 있습니다. |
| 400 크기 초과 | 동영상 파일은 100MB 이하만 올릴 수 있습니다. |
| 400 입력값 누락 | 파일 용도를 작성해주세요. / 파일명을 작성해주세요. |
| 400 없는 용도 (`"thumbnail"` 같은 소문자 포함) | 요청 본문 형식이 올바르지 않습니다. |
| 401 회원 ID 헤더 없음 | 로그인이 필요합니다. |

### ② S3에 직접 올리기 (브라우저 → S3)

①의 응답에 있는 `uploadUrl`로 `method`(PUT) 요청을 보냅니다. **`headers`를 그대로 붙여야 합니다.**
presigned URL의 서명에 Content-Type과 크기가 묶여 있어서, 다르게 보내면 S3가 403(`SignatureDoesNotMatch`)으로 거절합니다.

```js
const { data } = await (await fetch('/api/v1/file/files', { /* ① */ })).json();

await fetch(data.uploadUrl, {
  method: data.method,       // "PUT"
  headers: data.headers,     // content-length는 브라우저가 막는 헤더라 무시되고, 파일 크기로 자동으로 채워진다
  body: file,
});
```

- `headers`는 응답 body에 든 **다음 요청(S3 PUT)용 데이터**입니다. 자동으로 붙지 않으니 프론트가 옮겨 담습니다.
- URL은 10분 동안 쓸 수 있습니다. S3는 요청이 **시작될 때** 만료를 보므로, 10분 안에 시작한 큰 파일은 끝까지 올라갑니다.
- PUT은 S3가 파일을 끝까지 받은 뒤에 끝납니다. 끝난 다음 ③을 **한 번** 부르면 되고, 폴링할 필요는 없습니다.
  진행률이 필요하면 `XMLHttpRequest`의 `upload.onprogress`를 씁니다.
- 브라우저에서 S3로 직접 요청하므로 버킷에 CORS 설정이 필요합니다 ([6. 로컬에서 확인하기](#6-로컬에서-확인하기)).

### ③ 업로드 완료 확인

`POST /api/v1/file/files/{fileId}/complete` · 헤더 `X-Member-Id` 필수 (발급 때와 같은 회원)

서버가 S3의 `uploads/` 객체를 `files/`로 복사하고, 복사본의 **실제 크기·Content-Type·앞부분(매직 바이트)**이 ①에서 신고한 값과 같은지 검사합니다.
같으면 `UPLOADED`가 되고, 바로 미리보기에 쓸 수 있는 보기 URL(`url`, 30분)을 돌려줍니다.

응답 `200` — `data`는 [파일 정보](#4-파일-정보-filedto)

| 오류 | 메시지 | 다음 할 일 |
|---|---|---|
| 400 아직 안 올림 | 업로드된 파일이 없습니다. 업로드 URL로 파일을 먼저 올려주세요. | ②를 끝낸 뒤 다시 부른다 (파일은 PENDING 그대로) |
| 400 신고와 다름 | 올린 파일이 신고한 형식이나 크기와 다릅니다. 업로드 URL을 다시 받아 올려주세요. | 파일은 DELETED. ①부터 다시 |
| 403 남의 파일 | 본인이 올린 파일만 처리할 수 있습니다. | |
| 404 없거나 삭제됨 | 존재하지 않는 파일입니다. | |

- 이미 확인한 파일을 다시 불러도 같은 결과를 돌려줍니다(멱등). 다시 시도하거나 버튼을 두 번 눌러도 안전합니다.
- 확인을 마친 파일은 업로드 URL이 없는 `files/`로 옮겨지므로, 남은 시간 동안 같은 URL로 다시 올려도 확인한 파일은 바뀌지 않습니다.

### ④ 대상에 연결 (product-service → file-service)

`PUT /internal/v1/file/refs/{refType}/{refId}/files` · **내부 API** (브라우저는 부르지 않는다, 게이트웨이에 노출하지 않음)

| 값 | 설명 |
|---|---|
| `refType` (경로) | 대상 종류 = 대상 엔티티의 클래스 이름 (예: `Race`). 대문자로 시작하는 영문자·숫자 50자 이하 |
| `refId` (경로) | 대상 ID (1 이상) |
| `ownerId` (본문) | 이 저장을 한 회원 ID. product-service가 **받은 `X-Member-Id`**를 넣는다 (브라우저가 보낸 값을 쓰지 않는다) |
| `files[]` (본문) | 연결할 파일 목록. 각 항목은 `fileId`, `fileType`(업로드 때와 같아야 함), `sortNo`(같은 용도 안의 노출 순서, 0부터) |

```json
{
  "ownerId": 1,
  "files": [
    { "fileId": 10, "fileType": "THUMBNAIL", "sortNo": 0 },
    { "fileId": 11, "fileType": "DETAIL", "sortNo": 0 },
    { "fileId": 12, "fileType": "DETAIL", "sortNo": 1 }
  ]
}
```

응답 `200` — 연결된 파일 목록 (표시 순서대로)

**규칙**
- **보낸 목록이 그 대상의 파일 전체가 됩니다(갈아 치우기).** 전에 붙어 있었는데 목록에 없는 파일은 `DELETED`가 됩니다. 빈 목록 `[]`을 보내면 모두 뗍니다.
  그래서 대상을 고쳐 저장할 때는 화면에 남아 있는 파일 목록을 그대로 보내면 됩니다. 같은 요청을 다시 보내도 결과가 같습니다.
- **하나라도 문제가 있으면 아무것도 바뀌지 않습니다.** 먼저 전부 검사하고, 모두 통과해야 바꿉니다.
- 업로드 확인을 마친 파일(`UPLOADED`)이나 이미 이 대상에 붙은 파일(`ACTIVE`)만 연결할 수 있습니다.

| 오류 | 메시지 예 |
|---|---|
| 400 용도별 최대 개수 초과 | 대표 이미지 최대 개수(1개)를 넘었습니다. |
| 400 업로드 때와 다른 용도 | 업로드할 때 정한 파일 용도(THUMBNAIL)와 다릅니다. (fileId: 12, fileType: DETAIL, sortNo: 0) |
| 400 같은 파일 두 번 | 같은 파일을 두 번 연결할 수 없습니다. (fileId: …) |
| 400 대상 종류·ID 형식 | 대상 종류는 대문자로 시작하는 영문자·숫자 50자 이하로 작성해주세요. / 대상 ID는 0보다 커야 합니다. |
| 403 소유자가 올리지 않은 파일 | 본인이 올린 파일만 처리할 수 있습니다. (fileId: …) |
| 404 없거나 삭제된 파일 | 존재하지 않는 파일입니다. (fileId: …) |
| 409 다른 대상에 연결된 파일 | 다른 대상에 연결된 파일입니다. (fileId: …) |
| 409 업로드 확인 전 파일 | 업로드가 확인되지 않은 파일입니다. (fileId: …) |

파일별 오류에는 어느 항목이 문제인지 `(fileId, fileType, sortNo)`가 붙습니다.

### ⑤ 조회

**대상별 목록** `GET /api/v1/file/files?refType=Race&refId=5` · 회원 헤더 없음

- 그 대상에 붙은(`ACTIVE`) 파일만 돌려줍니다.
- 순서: 용도(대표 이미지 → 소개 이미지 → 코스 → 소개 영상 → 명단) → `sortNo` → 파일 ID
- `refType`·`refId`가 없으면 400 "필수 요청 값이 없습니다."

**파일 하나** `GET /api/v1/file/files/{fileId}` · 헤더 `X-Member-Id` 선택

| 파일 상태 | 볼 수 있는 사람 |
|---|---|
| `ACTIVE` | 누구나 (헤더 없어도 됨) |
| `PENDING`, `UPLOADED` | 올린 회원만. 그 밖에는 403 "연결된 파일이 아닙니다(현재 상태: 업로드 완료). 본인이 올린 파일만 볼 수 있습니다." (괄호 안은 상태에 따라 "업로드 대기"/"업로드 완료") |
| `DELETED`, 없음 | 404 "존재하지 않는 파일입니다." |

**보기 URL(`url`)**: 버킷이 비공개라 조회할 때마다 30분짜리 presigned GET URL을 새로 만들어 줍니다.
그대로 `<img src>`, `<video src>`, 내려받기 링크에 쓰면 됩니다. 저장해 두고 나중에 쓰면 만료되니, 필요할 때 다시 조회합니다.
확인 전(`PENDING`) 파일은 `url`이 `null`입니다.

## 3. 파일 종류

용도(`fileType`)마다 받을 수 있는 종류·형식·크기와, 대상 하나에 붙일 수 있는 개수가 정해져 있습니다.
용도·개수는 common의 `FileType`, 종류·크기는 common의 `FileKind`, 형식은 file-service의 `FileFormat`에 있습니다.

| fileType | 용도 | 종류 | 형식 | 최대 크기 | 대상당 개수 |
|---|---|---|---|---|---|
| `THUMBNAIL` | 대표 이미지 | 이미지 | JPG, PNG, WebP | 10MB | 1 |
| `DETAIL` | 대회 소개 이미지 | 이미지 | JPG, PNG, WebP | 10MB | 5 |
| `COURSE` | 코스 안내 이미지 (종목마다 1장) | 이미지 | JPG, PNG, WebP | 10MB | 7 |
| `INTRO_VIDEO` | 대회 소개 영상 | 동영상 | MP4, MOV | 100MB | 1 |
| `ROSTER` | 크루원 명단 | 엑셀 | XLSX, XLS | 5MB | 1 |

- **크기**는 ① 발급 때 검사합니다. **개수**는 ④ 연결 때 검사합니다. 업로드 시점에는 붙일 대상이 없어서 셀 수 없기 때문입니다.
  그래서 대표 이미지를 여러 번 올리는 것(다시 고르기)은 되고, 두 장 이상을 붙이려 하면 400입니다. 화면에서는 대표 이미지 칸을 한 장만 고르게 만듭니다.
- 연결하지 않고 1일이 지난 파일은 정리 배치가 지웁니다 ([1. 한눈에 보는 흐름](#1-한눈에-보는-흐름)의 "정리 배치").

### 파일 형식을 신고할 때

| 형식 | contentType | 확장자 |
|---|---|---|
| JPG | `image/jpeg` | jpg, jpeg |
| PNG | `image/png` | png |
| WebP | `image/webp` | webp |
| MP4 | `video/mp4` | mp4 |
| MOV | `video/quicktime` | mov |
| XLSX | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` | xlsx |
| XLS | `application/vnd.ms-excel` | xls |

- 브라우저는 `file.type`을 **파일 내용이 아니라 확장자**로 정합니다. 브라우저가 모르는 확장자면 빈 문자열이라, 위 표대로 확장자로 채워 보냅니다.
- 신고한 값은 믿지 않고 ③에서 실제 파일 앞부분으로 다시 검사합니다. 그래서 확장자만 바꾼 파일은 거절됩니다.
  - 이미지: 형식별 시그니처
  - MP4·MOV: `ftyp` 뒤 브랜드로 구분합니다. **신고한 형식과 정확히 같아야 하므로** 이름만 `.mp4`로 바꾼 MOV는 거절됩니다.
    MP4는 흔한 브랜드(`isom`, `iso2`, `mp41`, `mp42`, `avc1`)만 받고, M4A(오디오)·HEIC(사진)처럼 구조가 같은 다른 파일은 막습니다.
  - 엑셀: XLSX는 ZIP, XLS는 옛 오피스 형식인지까지만 확인합니다. docx·doc도 앞부분이 같아서, 진짜 엑셀인지는 명단을 읽을 때 확인해야 합니다.
- 코덱(H.264, HEVC 등)은 검사하지 않습니다. 브라우저에 따라 재생되지 않는 영상이 있을 수 있습니다.

## 4. 파일 정보 (`FileDto`)

③ 완료 확인, ④ 연결, ⑤ 조회가 돌려주는 파일 정보입니다.

| 필드 | 설명 |
|---|---|
| `id` | 파일 ID |
| `createDate`, `modifyDate` | 만든 시각, 바뀐 시각 |
| `createUser` | 올린 회원 ID |
| `originFileName` | 원본 파일명 (경로는 빼고 이름만) |
| `contentType`, `fileSize` | 신고한 형식과 크기 (③에서 실제 파일과 같은지 확인함) |
| `status` | `PENDING`, `UPLOADED`, `ACTIVE`, `DELETED` |
| `refType`, `refId` | 붙은 대상. 연결 전에는 `null` |
| `fileType`, `sortNo` | 용도와, 같은 용도 안의 노출 순서 |
| `url` | 보기용 presigned GET URL (30분). 확인 전 파일은 `null` |

## 5. product-service에서 연결할 때

```java
race = raceRepository.save(race);                 // 1. 대회를 저장해 ID를 얻는다
FileRef ref = FileRef.of(race);                   // 2. 대상은 엔티티로만 만든다 (type = "Race", id = race.getId())
fileClient.link(ref.getType(), ref.getId(), memberId, files);   // 3. owner는 받은 X-Member-Id
// 4. 커밋 (link가 실패하면 예외로 대회 저장도 롤백)
```

- **`FileRef.of(엔티티)`**(common `shared/file/domain`): 대상 종류를 문자열로 직접 적지 않게 막습니다. `"Race"`와 `"race"`처럼 같은 대상이
  다른 값으로 저장되는 일을 막기 위해서입니다. 생성자가 막혀 있고, 저장 전 엔티티(ID 0)로는 만들 수 없습니다.
- 대상을 고쳐 저장할 때도 **남아 있는 파일 전체**를 보냅니다(갈아 치우기).
- 업로드를 마친 파일은 **1일 안에** 연결해야 합니다. 1일이 지난 뒤 처음 도는 05:00 정리 배치가 지우므로, 그 뒤에 연결하면 404가 납니다.
- 알려진 한계: `link`는 성공했는데 product-service의 커밋이 실패하면 file-service 쪽 변경은 되돌아가지 않습니다.
  - 새로 만든 대상: 파일이 없는 대상 ID에 `ACTIVE`로 남습니다.
  - 고쳐 저장하던 대상: 새 목록이 이미 적용되어, 목록에서 뺀 파일은 `DELETED`가 된 채 되돌릴 수 없습니다(DELETED 파일은 다시 연결하면 404).
  - 되돌리는 보상 API는 아직 없습니다 (saga 보상, [ROADMAP](ROADMAP.md) 남은 것).

## 6. 로컬에서 확인하기

1. Docker Desktop을 켜고 `FileApplication`을 실행합니다 (dev 프로파일, compose의 MySQL 등이 함께 뜬다).
   실제 S3를 쓸지 S3Mock을 쓸지는 `.env`로 정합니다 ([backend/README](../README.md#파일-저장소-s3)). 시작 로그의 `S3 저장소: bucket=…, 주소=…`로 확인합니다.
2. **테스트 페이지** `http://localhost:8083/upload-test.html` (`src/main/resources/dev-static/`, dev 프로파일에서만 열림)
   - ①~⑤를 화면에서 해 볼 수 있습니다. ②·③을 직접 누르는 모드, 연결 때 보낼 용도·순서 바꾸기, 요청·응답 기록이 있습니다.
   - 반드시 `localhost`로 엽니다. 버킷 CORS에 허용한 주소와 같아야 합니다 (`127.0.0.1`은 다른 주소로 취급).
3. **실제 버킷에 올릴 때 CORS** (S3 콘솔 → 버킷 → 권한 → CORS. 버킷 정책과는 다른 설정)

   ```json
   [
     {
       "AllowedOrigins": ["http://localhost:8083"],
       "AllowedMethods": ["PUT", "GET"],
       "AllowedHeaders": ["content-type"],
       "MaxAgeSeconds": 3000
     }
   ]
   ```

4. **Swagger** `http://localhost:8083/swagger-ui.html` — API별 응답 코드와 원인별 오류 예시가 있습니다. `X-Member-Id` 입력칸에 회원 번호를 넣습니다.
5. **자동 테스트** `./gradlew :file-service:test`
   - API 테스트는 H2와 메모리 저장소(`FakeFileStorage`)로 돌아 S3가 필요 없습니다.
   - `S3FileStorageTest`는 Docker가 있으면 S3Mock 컨테이너로 돌고, 없으면 건너뜁니다.
   - 정리 배치는 `FileCleanupTest`(삭제 로직)와 `FileCleanupJobTest`(Job 실행, 스케줄러 연결)로 확인합니다.
     스케줄러는 prod 프로파일에서만 등록됩니다. IntelliJ·`bootRun`(dev)이나 인프라만 띄우는 `docker compose up`에서는 돌지 않습니다.
     `docker compose --profile app up`으로 앱 컨테이너를 띄우면 prod 프로파일이라 05:00에 돌고, `.env`가 실제 버킷을 가리키면 그 버킷에서 지웁니다.
   - presigned URL 서명, CORS, IAM 권한은 S3Mock과 가짜 저장소가 검사하지 않아서 실제 버킷으로만 확인할 수 있습니다.

## 7. 아직 안 된 것

- **행 없이 남은 S3 객체**: 아래 경우에는 DB 행 없이 객체만 남고, 정리 배치가 지우지 못합니다.
  - ③ 완료 확인 도중 서버가 죽음 (`files/` 사본이 남는다)
  - ③이 끝난 뒤 같은 업로드 URL(만료 10분 전)로 다시 올림 (`uploads/` 객체가 남는다)
  - 정리 배치의 S3 삭제 실패 (실패한 키는 로그에 남는다)
- **정리 배치 여러 대 실행**: file-service를 여러 대로 늘리면 같은 시각에 배치가 겹치지 않게 막아야 합니다 (ShedLock 등).
- **게이트웨이(PRO-20)**: 지금은 `X-Member-Id`를 아무 번호로나 보낼 수 있고, 내부 API(`/internal/**`)도 8083 포트로 직접 부를 수 있습니다.
- **크루원 명단 공개 범위**: 지금은 연결되면 누구나 조회할 수 있습니다. 명단에는 개인정보가 있어 범위를 정해야 합니다.
- **saga 보상**: ④ 이후 product-service 커밋이 실패했을 때 되돌리는 API
- **판매자·회원 ID 구분**: `createUser`, `ownerId`가 회원 ID인지 판매자 ID인지 구분하지 않습니다.
- **100MB 넘는 동영상**: 멀티파트 업로드로 넓힐 수 있습니다.
