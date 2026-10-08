---
paths:
  - "backend/src/main/java/**/in/**/*.java"
  - "backend/src/main/java/**/dto/**/*.java"
---

# Swagger(OpenAPI) 애노테이션 컨벤션

`@RestController`를 새로 만들거나 수정할 때 아래 규칙에 따라 Swagger 애노테이션을 함께 작성합니다.
참고 구현은 `member-service`의 `in/ApiV1AuthController.java`와 `AuthApiExamples.java`입니다.

**참고 구현에서 가져오는 것은 애노테이션 구성 방식과 작성 패턴뿐입니다.** 응답 코드, 에러 종류, 메시지, 예시 값은
대상 컨트롤러의 실제 코드에서 새로 조사해 작성합니다. Auth의 에러(이메일 중복 409, 로그인 실패 401 등)를 다른 컨트롤러에
그대로 옮기거나, 다른 컨트롤러의 에러를 Auth와 같은 형태로 바꾸지 않습니다. 이 문서의 코드 블록도 모두 형식을 보여 주는 예시입니다.

목표는 **Swagger UI만 보고 프론트엔드가 실제 응답(상태 코드, 메시지, data)을 그대로 알 수 있게** 하는 것입니다.
그래서 문서의 응답 코드와 예시는 대상 API의 실제 코드가 반환하는 값과 정확히 같아야 합니다.

## 1. 컨트롤러 클래스: `@Tag`

```java
@Tag(name = "Auth", description = "회원가입, 로그인 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class ApiV1AuthController {
```

- `@Tag`는 클래스 애노테이션 중 가장 위에 둡니다.
- `name`은 영어 PascalCase 도메인 이름(`Auth`, `Member`, `Race`)으로 씁니다. 없으면 `api-v-1-auth-controller`처럼 표시됩니다.
- `description`은 한국어로, 이 컨트롤러가 다루는 API를 쉼표로 나열합니다.

## 2. 엔드포인트: `@Operation`

```java
@PostMapping("/join")
@Operation(summary = "회원가입", description = "이메일, 닉네임, 휴대폰 번호 중복을 검사한 뒤 회원을 생성한다.")
```

- `@Operation`은 매핑 애노테이션(`@PostMapping` 등) 바로 아래에 둡니다.
- `summary`: 짧은 한국어 명사구 (`회원가입`, `내 정보 조회`)
- `description`: 실제 동작을 한 문장으로, `~한다.`로 끝냅니다. 검사 조건이나 부수 효과(세션 저장 등)가 있으면 적습니다.

## 3. 응답: `@ApiResponses`

**대상 API가 실제로 반환할 수 있는 응답 코드를 전부** 적습니다. 코드 흐름(Controller → Facade → UseCase 등)에서
던지는 예외와 `GlobalExceptionHandler`의 매핑을 확인해 결정합니다. 추측으로 넣거나 빼지 않고, 그 API에서 나가지 않는
코드는 적지 않습니다.

응답 코드를 정하는 기준은 다음과 같습니다. 어떤 예외가 몇 번 코드로 나가는지는 그때의 `GlobalExceptionHandler`가 기준입니다.

| 확인할 것 | 응답 코드 |
|---|---|
| 성공 반환문 | `ResponseEntity.ok()`면 200, `ResponseEntity.created()`면 **201** (springdoc이 추론하지 못하므로 **반드시 명시**) |
| 호출 흐름에서 던지는 도메인 예외 | `BusinessException`을 상속한 예외는 생성자에서 넘긴 `HttpStatus`, 그 밖의 예외는 `GlobalExceptionHandler`가 매핑한 코드 |
| `@Valid` 검증, `@RequestBody` 역직렬화 | 핸들러가 있으면 그 코드 (현재 `MethodArgumentNotValidException`, `HttpMessageNotReadableException` → 400) |
| `WebConfig`에서 인증이 필요한 경로 | Security 인증 진입점의 코드 (현재 401) |

핸들러가 없는 예외는 문서에 넣지 말고, 처리되지 않는다는 사실을 따로 알립니다.

### 성공 응답

```java
@ApiResponse(responseCode = "201", description = "가입 성공",
	content = @Content(examples = @ExampleObject(value = AuthApiExamples.JOIN_SUCCESS)))
```

- 스키마는 메서드 반환 타입(`RsData<MemberDto>`)에서 자동으로 생성되므로 `schema`를 지정하지 않습니다.

### 에러 응답

```java
@ApiResponse(responseCode = "409", description = "이메일, 닉네임 또는 휴대폰 번호 중복",
	content = @Content(schema = @Schema(implementation = RsData.class), examples = {
		@ExampleObject(name = "이메일 중복", value = AuthApiExamples.DUPLICATE_EMAIL),
		@ExampleObject(name = "닉네임 중복", value = AuthApiExamples.DUPLICATE_NICKNAME)
	})),
```

- `schema = @Schema(implementation = RsData.class)`를 지정합니다. 생략하면 성공 응답 스키마(`RsDataMemberDto`)가 잘못 붙습니다.
- 같은 코드에 원인이 여러 개면 `@ExampleObject`를 여러 개 두고 `name`에 원인을 한국어로 적습니다 (Swagger UI 드롭다운에 표시됨).
- 원인이 하나면 `name` 없이 `examples = @ExampleObject(value = ...)`로 씁니다.
- `description`은 원인을 쉼표/`또는`으로 묶어 한 줄로 씁니다.

## 4. 응답 예시 상수 클래스: `{Domain}ApiExamples`

예시 JSON은 컨트롤러에 직접 쓰지 않고, 같은 `in` 패키지의 상수 클래스에 모읍니다.

```java
/**
 * ApiV1AuthController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class AuthApiExamples {
	static final String DUPLICATE_EMAIL = """
		{ "status": 409, "message": "이미 사용 중인 이메일입니다.", "data": null }
		""";

	private AuthApiExamples() {
	}
}
```

- 클래스 이름은 `{Tag 이름}ApiExamples`, `final` + package-private + private 생성자로 만듭니다.
- 상수 이름은 `{동작}_{결과}` 대문자 스네이크 케이스 (`JOIN_SUCCESS`, `LOGIN_FAILED`, `DUPLICATE_EMAIL`).
  같은 클래스 안에서 여러 API가 똑같은 응답(코드와 메시지가 모두 같음)을 내면 하나만 두고 재사용합니다 (`INVALID_BODY`).
- 값은 텍스트 블록(`"""`)으로 작성하고, JSON 들여쓰기도 **탭**을 씁니다 (checkstyle 탭 규칙).
- 에러 응답은 한 줄 JSON, 성공 응답은 여러 줄 JSON으로 씁니다.

### 예시 값 작성 규칙

- `status`는 실제 HTTP 상태 코드, `message`는 **대상 API의 코드에 있는 메시지 문자열을 그대로 복사**합니다.
  - 핸들러가 `exception.getMessage()`를 응답하면: 예외를 던지는 곳에서 넘기는 메시지
  - 핸들러가 고정 메시지를 응답하면: 핸들러의 메시지
  - 검증 실패: 대상 요청 DTO의 검증 애노테이션 `message` 중 대표적인 것
- 에러 응답의 `data`는 `null`입니다.
- 성공 응답의 `data`는 응답 DTO의 모든 필드를 채우고, 요청 DTO의 `@Schema(example)`과 같은 값을 씁니다.
- 날짜는 `2026-10-05T21:25:35` 형식으로 씁니다.
- 메시지를 바꾸면 예시 상수도 함께 바꿉니다. 문자열이라 자동으로 맞춰지지 않습니다.

## 5. 요청 DTO: `@Schema`

```java
@Schema(description = "회원가입 요청")
@AllArgsConstructor
@Getter
public class MemberJoinRequestDto {
	@Schema(description = "휴대폰 번호 (하이픈 생략 가능)", example = "010-1234-5678")
	@NotBlank(message = "전화번호를 작성해주세요.")
	@Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$", message = "휴대폰 번호 형식에 맞게 작성해주세요.")
	private final String phone;
}
```

- 클래스에 `@Schema(description = "~ 요청")`을 붙입니다.
- 모든 필드에 `@Schema(description, example)`을 붙이고, 검증 애노테이션보다 **위에** 둡니다.
- `description`에는 제약 조건을 괄호로 덧붙입니다 (`비밀번호 (10자 이상)`). required, minLength, pattern은
  검증 애노테이션에서 자동으로 문서화되므로 `@Schema`에 중복해 적지 않습니다.
- `example`은 그 필드의 검증 규칙을 통과하는 현실적인 값으로 씁니다. 같은 의미의 필드가 이미 다른 DTO에 있으면
  같은 값을 써서 문서 전체에서 예시가 이어지게 합니다. 예를 들어 회원 관련 필드는 현재 이렇게 씁니다.

| 필드 | example |
|---|---|
| username | `runner1` |
| password | `password1234` |
| nickname | `러너1` |
| email | `runner1@bukang.com` |
| phone | `010-1234-5678` |

## 6. 기타

- `HttpServletRequest`, `HttpServletResponse`, `@AuthenticationPrincipal` 같은 파라미터는 springdoc이 자동으로
  숨기므로 따로 처리하지 않습니다.
- 경로 변수와 쿼리 파라미터에는 `@Parameter(description = "...", example = "...")`를 붙입니다.
- import 순서는 Naver 규칙을 따릅니다. `io.swagger.*`는 `jakarta.*`, `lombok.*`과 같은 "그 밖" 그룹입니다.
- 인증 방식이 JWT로 바뀌면 `@SecurityScheme` 설정과 인증이 필요한 API의 `@SecurityRequirement`를 추가합니다.
  지금은 세션 방식이라 사용하지 않습니다.

## 7. 확인

1. `./gradlew compileJava checkstyleMain`으로 컴파일과 checkstyle 경고를 확인합니다.
2. 앱을 실행해 `/v3/api-docs`에서 응답 코드, 예시, 태그가 의도대로 나오는지 확인합니다.
   8080을 IDE가 쓰고 있으면 `./gradlew bootRun --args='--server.port=18080'`으로 띄웁니다.
   Swagger UI는 dev 프로파일의 `/swagger-ui.html`에서 볼 수 있습니다.
