---
name: swagger-annotate
description: RestController에 프로젝트 Swagger 컨벤션(.claude/rules/swagger-convention.md)대로 @Tag, @Operation, @ApiResponses, 응답 예시 상수 클래스, 요청 DTO @Schema를 작성한다. "Swagger 애노테이션 달아줘", "API 문서화해줘", "Swagger 적용해줘", "/swagger-annotate ApiV1XxxController"처럼 컨트롤러의 Swagger 문서 작성이나 보완을 요청할 때 사용한다.
argument-hint: "[컨트롤러 클래스명 또는 경로]"
---

# Swagger 애노테이션 작성

대상: $ARGUMENTS (비어 있으면 사용자가 지목한 컨트롤러, 그것도 없으면 IDE에서 열린 컨트롤러. 대상이 불분명하면 물어본다)

먼저 `.claude/rules/swagger-convention.md`를 읽고, 참고 구현인
`backend/member-service/src/main/java/com/bukang/member/in/ApiV1AuthController.java`와 `AuthApiExamples.java`를 본다.
이 문서의 단계는 컨벤션을 적용하는 순서이고, 규칙 자체는 컨벤션 파일이 기준이다.

참고 구현에서 따르는 것은 **애노테이션 구성 방식과 작성 패턴**(어떤 애노테이션을 어디에 어떤 형식으로 쓰는지)뿐이다.
응답 코드, 에러 종류, 메시지, 예시 값은 대상 컨트롤러의 코드에서 새로 조사한다.

- Auth의 응답(이메일 중복 409, 로그인 실패 401 등)을 대상 컨트롤러에 옮겨 적지 않는다.
- 대상 컨트롤러의 에러를 Auth와 같은 종류나 형태로 바꾸지 않는다.
- 대상 코드의 예외나 응답 방식이 Auth와 다르더라도 코드를 고쳐 맞추지 않는다. 문서는 대상 코드를 있는 그대로 반영한다.

## 1. 실제 응답 조사 (작성 전에 반드시)

추측으로 응답 코드나 메시지를 쓰지 않는다. 엔드포인트마다 다음을 확인해 표로 정리한다.

1. **성공 코드**: 반환문이 `ok()`(200)인지 `created()`(201)인지, 성공 메시지 문자열
2. **던지는 예외**: Controller → Facade → UseCase/Service 호출 흐름을 따라가며 `throw`와 예외 메시지 문자열을 모은다
3. **예외 → 상태 코드**: 도메인 예외는 `BusinessException`을 상속하므로 예외 클래스 생성자의 `HttpStatus`가 상태 코드다.
   그 밖의 예외는 `backend/common`의 `global/exception/GlobalExceptionHandler.java`(Security 예외는 `member-service`의 `security/AuthExceptionHandler.java`)에서 상태 코드와 응답 메시지를 확인한다
   (핸들러가 고정 메시지를 쓰는지, `exception.getMessage()`를 쓰는지 구분)
4. **입력 검증**: `@Valid @RequestBody`가 있으면 요청 DTO의 검증 애노테이션 `message`, 그리고 400(형식 오류)
5. **인증**: 해당 서비스의 `security/WebConfig.java`(member-service)에서 이 경로가 `permitAll`인지 인증 필요인지 (인증 필요면 401 포함)
6. **응답 DTO 필드**: 성공 예시의 `data`를 채우기 위해 필드 목록 확인

핸들러가 없는 예외가 나오면 문서에 억지로 넣지 말고, 그 예외가 500 또는 다른 코드로 나간다는 사실을 사용자에게 알린다.

## 2. 작성

컨벤션 파일의 1~6절을 따른다.

1. 컨트롤러에 `@Tag` (클래스 애노테이션 맨 위)
2. 엔드포인트마다 `@Operation` + `@ApiResponses` (1단계 표의 모든 코드)
3. 같은 `in` 패키지에 대상 컨트롤러용 `{Tag}ApiExamples` 상수 클래스를 만들거나, 이미 있으면 상수를 추가한다.
   메시지는 1단계에서 모은 문자열을 그대로 복사한다. 상수 재사용은 같은 클래스 안에서 코드와 메시지가 모두 같을 때만 한다.
4. 요청 DTO에 클래스/필드 `@Schema(description, example)`. 예시 값은 검증 규칙을 통과하는 현실적인 값으로 쓰고,
   같은 의미의 필드가 다른 DTO에 이미 있으면 그 값과 맞춘다.
5. 기존 비즈니스 로직, 매핑 경로, 메서드 시그니처는 바꾸지 않는다. Swagger 애노테이션과 예시 클래스만 추가/수정한다.

## 3. 검사

1. `./gradlew compileJava checkstyleMain`을 실행한다. checkstyle은 실패해도 빌드가 성공하므로
   `build/reports/checkstyle/main.xml`에서 수정한 파일의 경고를 직접 확인하고 0건이 될 때까지 고친다.
   (텍스트 블록 안 JSON 들여쓰기도 탭이어야 한다)
2. 앱을 실행해 `/v3/api-docs`를 받아, 수정한 엔드포인트의 응답 코드별 example의 `status`/`message`가
   1단계 표와 같은지 확인한다. 8080이 사용 중이면 `./gradlew bootRun --args='--server.port=18080'`로 띄우고,
   확인 후 띄운 프로세스를 종료한다. IDE에서 실행 중인 앱은 건드리지 않는다.

## 4. 보고

- 엔드포인트별로 문서화한 응답 코드와 예시 목록 (표)
- 새로 만들거나 수정한 파일
- 조사 중 발견한 문제 (핸들러 없는 예외, 문서와 실제가 어긋날 수 있는 부분 등)
- 커밋은 사용자가 요청할 때만 한다.
