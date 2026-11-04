package com.bukang.member.in;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.member.app.MemberPhoneChangeUseCase;
import com.bukang.member.dto.MemberDto;
import com.bukang.member.dto.MemberPhoneChangeRequestDto;
import com.bukang.member.dto.VerifiedIdentityDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 휴대폰 번호는 본인인증을 다시 해야만 바뀐다 (BUK-71, BE-037). 두 API 모두 로그인해야 부를 수 있다
// 인증 시작은 가입과 같은 POST /api/v1/auth/identity-verifications를 쓴다
@Tag(name = "MemberPhone", description = "휴대폰 번호 바꾸기용 본인인증 확인, 휴대폰 번호 바꾸기 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/member/members/me")
public class ApiV1MemberPhoneController {
	private final MemberPhoneChangeUseCase memberPhoneChangeUseCase;

	@PostMapping("/phone-verifications/{identityVerificationId}/complete")
	@Operation(summary = "휴대폰 번호 바꾸기용 본인인증 확인",
		description = "포트원 창이 끝나면 부른다. 서버가 포트원 조회 API로 결과를 직접 확인하고, 인증한 사람의 이름 · 생년월일"
			+ "(회원과 인증 결과 둘 다 CI가 있으면 CI도)이 로그인한 회원과 같을 때만 결과를 15분 동안 저장한다. "
			+ "가입용 확인과 달리 이미 가입한 사람이어도 막지 않는다. 이미 확인한 ID를 다시 부르면 저장한 결과를 그대로 돌려준다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "본인인증 확인",
			useReturnTypeSchema = true,
			content = @Content(examples = @ExampleObject(value = MemberPhoneApiExamples.COMPLETE_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "서버가 만들지 않았거나 15분이 지난 인증 ID 또는 인증 실패",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "인증 ID가 없거나 지남", value = IdentityVerificationApiExamples.VERIFICATION_REQUIRED),
				@ExampleObject(name = "인증 실패", value = IdentityVerificationApiExamples.VERIFICATION_FAILED)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = MemberApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "인증한 사람이 로그인한 회원 본인이 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = MemberPhoneApiExamples.IDENTITY_MISMATCH))),
		@ApiResponse(responseCode = "404", description = "헤더의 회원이 없거나 탈퇴함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = MemberApiExamples.MEMBER_NOT_FOUND))),
		@ApiResponse(responseCode = "503", description = "포트원 조회 실패 (연결 실패, 응답 지연, 인증 키 오류)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = IdentityVerificationApiExamples.UNAVAILABLE)))
	})
	public ResponseEntity<RsData<VerifiedIdentityDto>> completePhoneVerification(
		@LoginMemberId long memberId,
		@Parameter(description = "본인인증 시작에서 받은 인증 ID", example = "crewrun-iv-6f1c2b9e-3a5d-4c7e-9b1a-2d8f0e4c6a71")
		@PathVariable String identityVerificationId
	) {
		return ResponseEntity.ok(RsData.of(HttpStatus.OK, "본인인증을 확인했습니다.",
			memberPhoneChangeUseCase.complete(memberId, identityVerificationId)));
	}

	@PutMapping("/phone")
	@Operation(summary = "휴대폰 번호 바꾸기",
		description = "회원 전용 본인인증 확인에서 받은 토큰의 휴대폰 번호로 바꾼다. 인증한 사람이 로그인한 회원 본인인지 다시 확인하고, "
			+ "다른 회원이 쓰는 번호인지 검사한다. 본인인증 해시도 새 번호 기준으로 바꾼다. 지금과 같은 번호면 저장하지 않는다. "
			+ "쓴 토큰은 지워져 다시 쓸 수 없다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "변경 성공 (같은 번호여도 200)",
			content = @Content(examples = @ExampleObject(value = MemberPhoneApiExamples.CHANGE_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "토큰 누락, 요청 본문 형식 오류, 서버에 없거나 15분이 지난 토큰",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "토큰 누락", value = MemberPhoneApiExamples.TOKEN_BLANK),
				@ExampleObject(name = "요청 본문 형식 오류", value = MemberApiExamples.INVALID_BODY),
				@ExampleObject(name = "토큰이 없거나 지남", value = IdentityVerificationApiExamples.VERIFICATION_REQUIRED)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = MemberApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "인증한 사람이 로그인한 회원 본인이 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = MemberPhoneApiExamples.IDENTITY_MISMATCH))),
		@ApiResponse(responseCode = "404", description = "헤더의 회원이 없거나 탈퇴함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = MemberApiExamples.MEMBER_NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "다른 회원이 쓰는 휴대폰 번호",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = MemberPhoneApiExamples.DUPLICATE_PHONE)))
	})
	public ResponseEntity<RsData<MemberDto>> changePhone(
		@LoginMemberId long memberId,
		@Valid @RequestBody MemberPhoneChangeRequestDto memberPhoneChangeRequestDto
	) {
		return ResponseEntity.ok(RsData.of(HttpStatus.OK, "휴대폰 번호를 바꿨습니다.",
			memberPhoneChangeUseCase.change(memberId, memberPhoneChangeRequestDto.verificationToken())));
	}
}
