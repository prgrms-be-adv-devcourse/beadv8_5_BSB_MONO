package com.bukang.member.in;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.member.app.SocialJoinUseCase;
import com.bukang.member.app.SocialLoginUseCase;
import com.bukang.member.app.SocialLoginUseCase.AuthorizeResult;
import com.bukang.member.app.SocialLoginUseCase.CallbackResult;
import com.bukang.member.config.auth.SocialLoginProperties;
import com.bukang.member.domain.SocialProvider;
import com.bukang.member.dto.AuthTokens;
import com.bukang.member.dto.MemberLoginResponseDto;
import com.bukang.member.dto.MemberLoginResult;
import com.bukang.member.dto.SocialJoinRequestDto;
import com.bukang.member.dto.SocialSignupInfoDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 카카오·구글 소셜 로그인 (BE-031·032·033·036). 로그인 전에 쓰므로 게이트웨이 공개 주소(/api/v1/auth/oauth2/**)다
 * 시작·콜백은 화면 이동(GET)이라 RsData가 아니라 302로 답한다. 결과는 프론트 화면 주소의 쿼리로 알린다
 */
@Tag(name = "SocialAuth", description = "카카오·구글 소셜 로그인, 소셜 회원가입 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/oauth2")
public class ApiV1SocialAuthController {
	private static final String PROVIDER_DESCRIPTION = "소셜 로그인 제공자 (kakao, google)";

	private final SocialLoginUseCase socialLoginUseCase;
	private final SocialJoinUseCase socialJoinUseCase;
	private final SocialLoginProperties socialLoginProperties;

	@GetMapping("/authorize/{provider}")
	@Operation(summary = "소셜 로그인 시작", description = "state·PKCE를 만들어 Redis에 10분 저장하고, 그 요청 ID를 httpOnly 쿠키"
		+ "(oauth2Request)로 심은 뒤 제공자 로그인 화면으로 보낸다. fetch가 아니라 화면 이동으로 연다. "
		+ "계정 연결 표 쿠키(oauth2Link)가 있으면 로그인한 회원의 계정 연결로 처리하고, 결과와 상관없이 그 쿠키를 지운다.")
	@ApiResponses({
		@ApiResponse(responseCode = "302", description = "제공자 로그인 화면으로 이동. 키가 설정되지 않았거나 연결 표가 잘못됐으면 "
			+ "/login?error=AUTH_SOCIAL_FAILED (연결이면 next?error=AUTH_SOCIAL_FAILED)",
			headers = @Header(name = HttpHeaders.LOCATION, description = "이동할 주소")),
		@ApiResponse(responseCode = "404", description = "지원하지 않는 제공자",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SocialAuthApiExamples.PROVIDER_NOT_FOUND)))
	})
	public ResponseEntity<Void> authorize(
		@Parameter(description = PROVIDER_DESCRIPTION, example = "kakao") @PathVariable String provider,
		@Parameter(description = "끝난 뒤 돌아갈 앱 내부 경로. 앱 내부 경로가 아니면 '/'(연결이면 '/me/accounts')로 바꾼다",
			example = "/races/12")
		@RequestParam(required = false) String next,
		@Parameter(hidden = true) @CookieValue(name = AuthCookies.OAUTH2_LINK, required = false) String linkTicket,
		HttpServletRequest request
	) {
		AuthorizeResult result = socialLoginUseCase.begin(SocialProvider.fromPath(provider), next, linkTicket, request);
		// 연결 표는 한 번만 쓰므로, 표 쿠키를 받았으면 결과와 상관없이 지운다
		List<ResponseCookie> cookies = new ArrayList<>();
		if (linkTicket != null) {
			cookies.add(AuthCookies.expired(AuthCookies.OAUTH2_LINK, AuthCookies.OAUTH2_LINK_PATH));
		}
		if (result.requestId() == null) {
			return redirect(frontUrl(result.location()), cookies.toArray(ResponseCookie[]::new));
		}
		cookies.add(AuthCookies.oauth2Request(result.requestId()));
		// 제공자 로그인 화면은 다른 도메인이라 front 주소를 붙이지 않는다
		return redirect(result.location(), cookies.toArray(ResponseCookie[]::new));
	}

	@GetMapping("/callback/{provider}")
	@Operation(summary = "소셜 로그인 콜백", description = "제공자가 브라우저를 보내는 주소다. 프론트가 직접 부르지 않는다. "
		+ "쿠키의 요청 ID로 꺼낸 state가 맞을 때만 진행하고, 결과에 따라 프론트 화면으로 보낸다. "
		+ "연결된 회원: Refresh Token 쿠키 + /auth/social/complete?next= / 처음 온 사람: 가입 대기 쿠키(oauth2Signup) + "
		+ "/signup/social?next= / 확인된 이메일이 다른 회원 것: /login?error=AUTH_SOCIAL_EMAIL_TAKEN&provider=&joinedWith= / "
		+ "실패: /login?error=AUTH_SOCIAL_FAILED / 계정 연결: next?linked= 또는 next?error=")
	@ApiResponses({
		@ApiResponse(responseCode = "302", description = "프론트 화면으로 이동 (설명 참고)",
			headers = @Header(name = HttpHeaders.LOCATION, description = "이동할 주소")),
		@ApiResponse(responseCode = "404", description = "지원하지 않는 제공자",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SocialAuthApiExamples.PROVIDER_NOT_FOUND)))
	})
	public ResponseEntity<Void> callback(
		@Parameter(description = PROVIDER_DESCRIPTION, example = "kakao") @PathVariable String provider,
		@RequestParam(required = false) String code,
		@RequestParam(required = false) String state,
		@Parameter(description = "사용자가 동의를 취소하면 제공자가 넣는다") @RequestParam(required = false) String error,
		@Parameter(hidden = true) @CookieValue(name = AuthCookies.OAUTH2_REQUEST, required = false) String requestId
	) {
		CallbackResult result = socialLoginUseCase.callback(SocialProvider.fromPath(provider), requestId, code, state,
			error);
		// 인가 요청은 한 번만 쓰므로 결과와 상관없이 쿠키를 지운다
		ResponseCookie clearRequest = AuthCookies.expired(AuthCookies.OAUTH2_REQUEST, AuthCookies.OAUTH2_PATH);
		String location = frontUrl(result.location());
		return switch (result) {
			case CallbackResult.LoggedIn loggedIn -> {
				AuthTokens tokens = loggedIn.tokens();
				yield redirect(location, clearRequest,
					AuthCookies.refreshToken(tokens.refreshToken(), tokens.refreshTokenExpiration()));
			}
			case CallbackResult.SignupRequired signup ->
				redirect(location, clearRequest, AuthCookies.oauth2Signup(signup.pendingId()));
			case CallbackResult.Redirect ignored -> redirect(location, clearRequest);
		};
	}

	@GetMapping("/signup")
	@Operation(summary = "소셜 가입 미리 채울 값 조회", description = "가입 대기 쿠키(oauth2Signup)로 제공자가 준 이메일·이름을 돌려준다. "
		+ "emailLocked가 true면 이메일을 고칠 수 없게 보여 준다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = SocialAuthApiExamples.SIGNUP_INFO_SUCCESS))),
		@ApiResponse(responseCode = "401", description = "가입 대기가 없거나 15분이 지남",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SocialAuthApiExamples.SIGNUP_EXPIRED)))
	})
	public ResponseEntity<RsData<SocialSignupInfoDto>> findSignup(
		@Parameter(hidden = true) @CookieValue(name = AuthCookies.OAUTH2_SIGNUP, required = false) String pendingId
	) {
		return ResponseEntity.ok(
			RsData.of(HttpStatus.OK, "소셜 가입 정보 조회 성공", socialJoinUseCase.findSignupInfo(pendingId)));
	}

	@PostMapping("/join")
	@Operation(summary = "소셜 회원가입", description = "가입 대기 쿠키의 소셜 계정으로 회원을 만든다. 약관·입력 검사는 이메일 가입과 같다. "
		+ "제공자가 확인된 이메일을 줬으면 그 값을 쓰고 본문 email은 무시한다. 성공하면 로그인과 같이 Access Token은 본문, "
		+ "Refresh Token은 쿠키로 내려주고 가입 대기 쿠키를 지운다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "가입 성공",
			headers = @Header(name = HttpHeaders.SET_COOKIE,
				description = "refreshToken=...; Path=/api/v1/auth; Max-Age=1209600; Secure; HttpOnly; SameSite=Lax"),
			content = @Content(examples = @ExampleObject(value = SocialAuthApiExamples.JOIN_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "입력값 검증 실패, 만 14세 미만, 필수 약관 미동의, 이메일 없음",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 검증 실패", value = AuthApiExamples.JOIN_INVALID_INPUT),
				@ExampleObject(name = "만 14세 미만", value = AuthApiExamples.JOIN_UNDER_AGE),
				@ExampleObject(name = "필수 약관 미동의", value = AuthApiExamples.JOIN_REQUIRED_TERMS_NOT_AGREED),
				@ExampleObject(name = "이메일 없음", value = SocialAuthApiExamples.JOIN_EMAIL_REQUIRED)
			})),
		@ApiResponse(responseCode = "401", description = "가입 대기가 없거나 15분이 지남",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SocialAuthApiExamples.SIGNUP_EXPIRED))),
		@ApiResponse(responseCode = "409", description = "이메일·닉네임 중복, 이미 가입한 휴대폰 번호(계정 연결 안내) 또는 이미 연결된 소셜 계정",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "이메일 중복", value = AuthApiExamples.DUPLICATE_EMAIL),
				@ExampleObject(name = "닉네임 중복", value = AuthApiExamples.DUPLICATE_NICKNAME),
				@ExampleObject(name = "이미 가입한 휴대폰 번호", value = SocialAuthApiExamples.PHONE_TAKEN),
				@ExampleObject(name = "이미 연결된 소셜 계정", value = SocialAuthApiExamples.ALREADY_LINKED)
			}))
	})
	public ResponseEntity<RsData<MemberLoginResponseDto>> join(
		@Parameter(hidden = true) @CookieValue(name = AuthCookies.OAUTH2_SIGNUP, required = false) String pendingId,
		@Valid @RequestBody SocialJoinRequestDto request
	) {
		MemberLoginResult result = socialJoinUseCase.join(pendingId, request);
		AuthTokens tokens = result.tokens();
		return ResponseEntity.status(HttpStatus.CREATED)
			.header(HttpHeaders.SET_COOKIE,
				AuthCookies.refreshToken(tokens.refreshToken(), tokens.refreshTokenExpiration()).toString())
			.header(HttpHeaders.SET_COOKIE,
				AuthCookies.expired(AuthCookies.OAUTH2_SIGNUP, AuthCookies.OAUTH2_PATH).toString())
			.body(RsData.of(HttpStatus.CREATED, "소셜 가입 성공",
				new MemberLoginResponseDto(tokens.accessToken(), result.member())));
	}

	private String frontUrl(String pathAndQuery) {
		return socialLoginProperties.frontUrl(pathAndQuery);
	}

	private static ResponseEntity<Void> redirect(String location, ResponseCookie... cookies) {
		ResponseEntity.HeadersBuilder<?> builder = ResponseEntity.status(HttpStatus.FOUND)
			.location(URI.create(location));
		for (ResponseCookie cookie : cookies) {
			builder.header(HttpHeaders.SET_COOKIE, cookie.toString());
		}
		return builder.build();
	}
}
