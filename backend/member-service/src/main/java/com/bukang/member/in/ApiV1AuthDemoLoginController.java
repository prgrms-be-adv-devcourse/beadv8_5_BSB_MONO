package com.bukang.member.in;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.member.app.DemoLoginUseCase;
import com.bukang.member.dto.AuthTokens;
import com.bukang.member.dto.MemberLoginResponseDto;
import com.bukang.member.dto.MemberLoginResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 테스트 계정 로그인. member.auth.demo-login.enabled가 true일 때만 빈을 만든다
 * 꺼져 있으면 이 주소가 아예 없어 GET·POST 모두 404가 되고, 프론트는 버튼을 숨긴다
 * 켜져 있는 동안에는 누구나 설정에 적힌 시연 전용 계정으로 로그인한다. 토큰이 읽기 전용이라 게이트웨이가 쓰기를 막는다
 */
@Tag(name = "Auth", description = "회원가입, 로그인, 토큰 재발급, 로그아웃 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/demo-login")
@ConditionalOnProperty(name = "member.auth.demo-login.enabled", havingValue = "true")
public class ApiV1AuthDemoLoginController {
	private final DemoLoginUseCase demoLoginUseCase;

	@GetMapping
	@Operation(summary = "데모 로그인 켜짐 확인", description = "켜져 있으면 200, 꺼져 있으면 404다. 로그인 화면이 버튼을 보여 줄지 정한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "데모 로그인을 쓸 수 있음",
			content = @Content(examples = @ExampleObject(value = AuthApiExamples.DEMO_LOGIN_AVAILABLE))),
		@ApiResponse(responseCode = "404", description = "데모 로그인이 꺼져 있음", content = @Content)
	})
	public ResponseEntity<RsData<Void>> availability() {
		return ResponseEntity.ok(RsData.of(HttpStatus.OK, "데모 로그인을 쓸 수 있습니다.", null));
	}

	// 요청 본문·쿼리·헤더를 받지 않는다. 로그인할 계정은 서버 설정 하나로만 정해진다
	// JSON 형식 요청만 받는다. 다른 사이트가 폼 전송으로 방문자를 데모 계정에 로그인시키지 못하게 한다
	// (다른 사이트에서 JSON 형식으로 보내려면 브라우저가 사전 확인 요청을 먼저 보내고, CORS가 막는다)
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "데모 로그인", description = "서버 설정에 적힌 시연 전용 계정 하나로 비밀번호 없이 로그인한다. 토큰은 읽기 전용이라 조회만 할 수 있다. "
		+ "응답과 쿠키는 이메일 로그인과 같다. 같은 IP는 1분에 10번까지 부를 수 있다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "로그인 성공",
			headers = @Header(name = HttpHeaders.SET_COOKIE,
				description = "refreshToken=...; Path=/api/v1/auth; Max-Age=1209600; Secure; HttpOnly; SameSite=Lax"),
			content = @Content(examples = @ExampleObject(value = AuthApiExamples.LOGIN_SUCCESS))),
		@ApiResponse(responseCode = "404", description = "데모 로그인이 꺼져 있거나 시연용 계정이 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = AuthApiExamples.DEMO_LOGIN_MEMBER_NOT_FOUND))),
		@ApiResponse(responseCode = "415", description = "Content-Type이 application/json이 아님", content = @Content),
		@ApiResponse(responseCode = "429", description = "같은 IP가 1분에 10번을 넘게 부름",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = AuthApiExamples.DEMO_LOGIN_TOO_MANY)))
	})
	public ResponseEntity<RsData<MemberLoginResponseDto>> login(HttpServletRequest httpRequest) {
		MemberLoginResult result = demoLoginUseCase.login(ClientIp.of(httpRequest));

		AuthTokens tokens = result.tokens();
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE,
				AuthCookies.refreshToken(tokens.refreshToken(), tokens.refreshTokenExpiration()).toString())
			.body(RsData.of(HttpStatus.OK, "로그인 성공",
				new MemberLoginResponseDto(tokens.accessToken(), result.member())));
	}
}
