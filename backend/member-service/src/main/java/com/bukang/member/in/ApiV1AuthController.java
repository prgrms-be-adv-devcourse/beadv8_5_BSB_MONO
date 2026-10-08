package com.bukang.member.in;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.rsdata.RsData;
import com.bukang.member.app.MemberFacade;
import com.bukang.member.dto.MemberDto;
import com.bukang.member.dto.MemberJoinRequestDto;
import com.bukang.member.dto.MemberLoginRequestDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Auth", description = "회원가입, 로그인 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class ApiV1AuthController {
	private final MemberFacade memberFacade;
	private final SecurityContextRepository securityContextRepository;

	// ResponseEntity.created()의 201은 springdoc이 추론하지 못하므로 응답 코드를 직접 명시한다
	// 에러 응답은 GlobalExceptionHandler가 RsData(data: null) 형식으로 반환한다
	@PostMapping("/join")
	@Operation(summary = "회원가입", description = "이메일, 닉네임, 휴대폰 번호 중복을 검사한 뒤 회원을 생성한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "가입 성공",
			content = @Content(examples = @ExampleObject(value = AuthApiExamples.JOIN_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 요청 본문 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 검증 실패", value = AuthApiExamples.JOIN_INVALID_INPUT),
				@ExampleObject(name = "요청 본문 형식 오류", value = AuthApiExamples.INVALID_BODY)
			})),
		@ApiResponse(responseCode = "409", description = "이메일, 닉네임 또는 휴대폰 번호 중복",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "이메일 중복", value = AuthApiExamples.DUPLICATE_EMAIL),
				@ExampleObject(name = "닉네임 중복", value = AuthApiExamples.DUPLICATE_NICKNAME),
				@ExampleObject(name = "휴대폰 번호 중복", value = AuthApiExamples.DUPLICATE_PHONE)
			}))
	})
	public ResponseEntity<RsData<MemberDto>> join(
		@Valid @RequestBody MemberJoinRequestDto memberJoinDto
	) {
		MemberDto memberDto = memberFacade.join(memberJoinDto);
		return ResponseEntity.created(URI.create("/api/v1/member/members/" + memberDto.getId()))
			.body(RsData.of(HttpStatus.CREATED, "Member Joined Successfully", memberDto));
	}

	@PostMapping("/login")
	@Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인하고 세션에 인증 정보를 저장한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "로그인 성공",
			content = @Content(examples = @ExampleObject(value = AuthApiExamples.LOGIN_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 요청 본문 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 검증 실패", value = AuthApiExamples.LOGIN_INVALID_INPUT),
				@ExampleObject(name = "요청 본문 형식 오류", value = AuthApiExamples.INVALID_BODY)
			})),
		@ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = AuthApiExamples.LOGIN_FAILED)))
	})
	public ResponseEntity<RsData<MemberDto>> login(
		@Valid @RequestBody MemberLoginRequestDto memberLoginRequestDto,
		HttpServletRequest request,
		HttpServletResponse response
	) {
		MemberDto memberDto = memberFacade.login(memberLoginRequestDto);

		SecurityContext context = SecurityContextHolder.getContext();
		securityContextRepository.saveContext(context, request, response);

		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "로그인 성공", memberDto));
	}
}
