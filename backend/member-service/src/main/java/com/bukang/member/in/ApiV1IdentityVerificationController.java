package com.bukang.member.in;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.member.app.IdentityVerificationUseCase;
import com.bukang.member.dto.IdentityVerificationStartDto;
import com.bukang.member.dto.VerifiedIdentityDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 가입 본인인증 (BUK-33, 포트원 V2 + KG이니시스 통합인증). 로그인 전에 쓰므로 게이트웨이 공개 주소다
 * 순서: 시작(인증 ID 받기) → 화면이 포트원 창을 연다 → 확인(서버가 포트원에서 결과를 조회) → 가입 요청에 인증 토큰을 넣는다
 */
@Tag(name = "IdentityVerification", description = "가입 본인인증 API (포트원)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/identity-verifications")
public class ApiV1IdentityVerificationController {
	private final IdentityVerificationUseCase identityVerificationUseCase;

	@PostMapping
	@Operation(summary = "본인인증 시작", description = "포트원 창을 열 인증 ID를 만들어 15분 동안 저장한다. "
		+ "화면은 이 ID로 PortOne.requestIdentityVerification()을 부른다. 서버가 만든 ID의 결과만 가입에 쓸 수 있다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "인증 ID 발급",
			useReturnTypeSchema = true,
			content = @Content(examples = @ExampleObject(value = IdentityVerificationApiExamples.START_SUCCESS)))
	})
	public ResponseEntity<RsData<IdentityVerificationStartDto>> startIdentityVerification() {
		IdentityVerificationStartDto data = new IdentityVerificationStartDto(identityVerificationUseCase.start());
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(RsData.of(HttpStatus.CREATED, "본인인증을 시작했습니다.", data));
	}

	@PostMapping("/{identityVerificationId}/complete")
	@Operation(summary = "본인인증 확인", description = "포트원 창이 끝나면 부른다. 서버가 포트원 조회 API로 결과를 직접 확인하고, "
		+ "확인된 이름 · 생년월일 · 휴대폰 번호와 CI 해시를 15분 동안 저장한다. 화면이 보낸 인증 결과는 쓰지 않는다. "
		+ "이미 확인한 ID를 다시 부르면 저장한 결과를 그대로 돌려준다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "본인인증 확인",
			useReturnTypeSchema = true,
			content = @Content(examples = @ExampleObject(value = IdentityVerificationApiExamples.COMPLETE_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "서버가 만들지 않았거나 15분이 지난 인증 ID, 인증 실패, CI를 주지 않는 인증 방법",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "인증 ID가 없거나 지남", value = IdentityVerificationApiExamples.VERIFICATION_REQUIRED),
				@ExampleObject(name = "인증 실패", value = IdentityVerificationApiExamples.VERIFICATION_FAILED),
				@ExampleObject(name = "CI를 주지 않는 인증 방법", value = IdentityVerificationApiExamples.CI_MISSING)
			})),
		@ApiResponse(responseCode = "503", description = "포트원 조회 실패 (연결 실패, 응답 지연, 인증 키 오류)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = IdentityVerificationApiExamples.UNAVAILABLE)))
	})
	public ResponseEntity<RsData<VerifiedIdentityDto>> completeIdentityVerification(
		@Parameter(description = "본인인증 시작에서 받은 인증 ID", example = "crewrun-iv-6f1c2b9e-3a5d-4c7e-9b1a-2d8f0e4c6a71")
		@PathVariable String identityVerificationId
	) {
		return ResponseEntity.ok(RsData.of(HttpStatus.OK, "본인인증을 확인했습니다.",
			identityVerificationUseCase.complete(identityVerificationId)));
	}
}
