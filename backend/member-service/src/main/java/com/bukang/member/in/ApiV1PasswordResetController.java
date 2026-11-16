package com.bukang.member.in;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.member.app.PasswordResetUseCase;
import com.bukang.member.dto.PasswordResetConfirmDto;
import com.bukang.member.dto.PasswordResetRequestDto;
import com.bukang.member.dto.PasswordResetTokenDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 로그인 전에 쓰므로 게이트웨이 공개 주소(/api/v1/auth/password-reset/**)에 있다
@Tag(name = "PasswordReset", description = "비밀번호 재설정 메일 요청, 재설정 링크 확인, 새 비밀번호 저장 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/password-reset")
public class ApiV1PasswordResetController {
	private final PasswordResetUseCase passwordResetUseCase;

	@PostMapping("/request")
	@Operation(summary = "비밀번호 재설정 메일 요청", description = "가입한 이메일이면 30분 동안 한 번 쓸 수 있는 재설정 링크를 메일로 보낸다. "
		+ "가입하지 않은 이메일·탈퇴한 회원도 같은 200으로 응답하고 메일은 보내지 않는다. "
		+ "비밀번호 없는 소셜 가입 회원에게는 링크 대신 소셜 로그인 안내 메일을 보낸다. "
		+ "메일은 응답 뒤 따로 보내고, 실패하면 최대 3번 다시 보낸다. 같은 이메일은 1분에 1번, 하루 5번까지, "
		+ "같은 IP는 하루 20번까지 받는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "요청 받음 (가입 여부와 상관없이 같은 응답)",
			content = @Content(examples = @ExampleObject(value = PasswordResetApiExamples.REQUEST_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 요청 본문 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 검증 실패", value = PasswordResetApiExamples.REQUEST_INVALID_INPUT),
				@ExampleObject(name = "요청 본문 형식 오류", value = PasswordResetApiExamples.INVALID_BODY)
			})),
		@ApiResponse(responseCode = "429", description = "같은 이메일로 1분 안에 다시 요청했거나 하루 5번을 넘김, 같은 IP가 하루 20번을 넘김",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PasswordResetApiExamples.TOO_MANY)))
	})
	public ResponseEntity<RsData<Void>> requestPasswordReset(@Valid @RequestBody PasswordResetRequestDto request,
		HttpServletRequest httpRequest) {
		passwordResetUseCase.request(request.getEmail(), ClientIp.of(httpRequest));
		return ResponseEntity.ok(RsData.of(HttpStatus.OK, "가입된 이메일이면 재설정 메일을 보냈습니다.", null));
	}

	@PostMapping("/verify")
	@Operation(summary = "재설정 링크 확인", description = "새 비밀번호 화면을 열 때 링크가 아직 쓸 수 있는지 확인한다. 링크를 쓴 것으로 치지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "쓸 수 있는 링크",
			content = @Content(examples = @ExampleObject(value = PasswordResetApiExamples.VERIFY_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "링크가 없거나 30분이 지났거나 이미 썼거나 더 새 링크가 나옴, 요청 본문 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "쓸 수 없는 링크", value = PasswordResetApiExamples.TOKEN_INVALID),
				@ExampleObject(name = "요청 본문 형식 오류", value = PasswordResetApiExamples.INVALID_BODY)
			}))
	})
	public ResponseEntity<RsData<Void>> verifyPasswordReset(@Valid @RequestBody PasswordResetTokenDto request) {
		passwordResetUseCase.verify(request.getToken());
		return ResponseEntity.ok(RsData.of(HttpStatus.OK, "쓸 수 있는 재설정 링크입니다.", null));
	}

	@PostMapping("/confirm")
	@Operation(summary = "새 비밀번호 저장", description = "재설정 링크로 새 비밀번호를 저장하고 링크를 지운다. "
		+ "저장한 뒤 모든 기기의 로그인(Refresh Token)을 풀고 변경 알림 메일을 보낸다. "
		+ "이미 받은 Access Token은 만료(최대 30분)까지 쓸 수 있다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "비밀번호 변경 성공",
			content = @Content(examples = @ExampleObject(value = PasswordResetApiExamples.CONFIRM_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "입력값 검증 실패, 요청 본문 형식 오류 또는 쓸 수 없는 링크",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 검증 실패", value = PasswordResetApiExamples.CONFIRM_INVALID_INPUT),
				@ExampleObject(name = "요청 본문 형식 오류", value = PasswordResetApiExamples.INVALID_BODY),
				@ExampleObject(name = "쓸 수 없는 링크", value = PasswordResetApiExamples.TOKEN_INVALID)
			}))
	})
	public ResponseEntity<RsData<Void>> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmDto request) {
		passwordResetUseCase.confirm(request.getToken(), request.getPassword());
		return ResponseEntity.ok(RsData.of(HttpStatus.OK, "비밀번호를 바꿨습니다.", null));
	}
}
