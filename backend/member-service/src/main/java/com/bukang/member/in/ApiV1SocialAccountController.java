package com.bukang.member.in;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.member.app.SocialAccountUseCase;
import com.bukang.member.domain.SocialProvider;
import com.bukang.member.dto.MemberDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 로그인한 회원의 소셜 계정 연결·해제 (BE-032). 게이트웨이가 Access Token을 검사한 요청만 온다
 */
@Tag(name = "SocialAccount", description = "로그인한 회원의 소셜 계정 연결·해제 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/member/members/me/social-accounts")
public class ApiV1SocialAccountController {
	private final SocialAccountUseCase socialAccountUseCase;

	@PostMapping("/{provider}/link-ticket")
	@Operation(summary = "계정 연결 표 발급", description = "로그인한 회원이 소셜 계정을 연결할 때 먼저 부른다. 2분 동안 한 번만 쓸 수 있는 "
		+ "연결 표를 httpOnly 쿠키(oauth2Link, Path=/api/v1/auth/oauth2/authorize)로 심는다. 프론트는 이어서 "
		+ "/api/v1/auth/oauth2/authorize/{provider}?next=/me/accounts로 화면을 이동한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "발급 성공",
			headers = @Header(name = HttpHeaders.SET_COOKIE,
				description = "oauth2Link=...; Path=/api/v1/auth/oauth2/authorize; Max-Age=120; "
					+ "Secure; HttpOnly; SameSite=Lax"),
			content = @Content(examples = @ExampleObject(value = SocialAuthApiExamples.LINK_TICKET_SUCCESS))),
		@ApiResponse(responseCode = "404", description = "회원이 없거나 지원하지 않는 제공자",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "회원 없음", value = SocialAuthApiExamples.MEMBER_NOT_FOUND),
				@ExampleObject(name = "없는 제공자", value = SocialAuthApiExamples.PROVIDER_NOT_FOUND)
			})),
		@ApiResponse(responseCode = "409", description = "같은 제공자가 이미 연결됨",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SocialAuthApiExamples.PROVIDER_ALREADY_LINKED)))
	})
	public ResponseEntity<RsData<Void>> issueLinkTicket(
		@LoginMemberId long memberId,
		@Parameter(description = "소셜 로그인 제공자 (kakao, google)", example = "kakao") @PathVariable String provider
	) {
		String ticket = socialAccountUseCase.issueLinkTicket(memberId, SocialProvider.fromPath(provider));
		return ResponseEntity.status(HttpStatus.CREATED)
			.header(HttpHeaders.SET_COOKIE, AuthCookies.oauth2Link(ticket).toString())
			.body(RsData.of(HttpStatus.CREATED, "연결 표 발급 성공", null));
	}

	@DeleteMapping("/{provider}")
	@Operation(summary = "소셜 계정 연결 해제", description = "비밀번호나 다른 소셜 연결이 남을 때만 해제한다. "
		+ "카카오면 커밋 뒤 카카오 연결 끊기도 부른다(실패해도 해제는 유지).")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "해제 성공",
			content = @Content(examples = @ExampleObject(value = SocialAuthApiExamples.UNLINK_SUCCESS))),
		@ApiResponse(responseCode = "404", description = "회원이 없거나, 연결되지 않은 제공자이거나, 지원하지 않는 제공자",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "회원 없음", value = SocialAuthApiExamples.MEMBER_NOT_FOUND),
				@ExampleObject(name = "연결 없음", value = SocialAuthApiExamples.ACCOUNT_NOT_FOUND),
				@ExampleObject(name = "없는 제공자", value = SocialAuthApiExamples.PROVIDER_NOT_FOUND)
			})),
		@ApiResponse(responseCode = "409", description = "마지막 로그인 방법",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SocialAuthApiExamples.LAST_LOGIN_METHOD)))
	})
	public ResponseEntity<RsData<MemberDto>> unlink(
		@LoginMemberId long memberId,
		@Parameter(description = "소셜 로그인 제공자 (kakao, google)", example = "kakao") @PathVariable String provider
	) {
		MemberDto member = socialAccountUseCase.unlink(memberId, SocialProvider.fromPath(provider));
		return ResponseEntity.ok(RsData.of(HttpStatus.OK, "연결을 해제했습니다.", member));
	}
}
