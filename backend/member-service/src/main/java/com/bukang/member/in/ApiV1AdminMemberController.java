package com.bukang.member.in;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.member.app.IdentityReviewQueryUseCase;
import com.bukang.member.dto.IdentityReviewPageDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

// 관리자 판별은 X-Member-Id 회원의 역할 컬럼으로 한다 (BE-020 결정 1). 관리자가 아니면 403
@Tag(name = "AdminMember", description = "관리자의 회원 확인 API (관리자 전용)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/member/admin/members")
public class ApiV1AdminMemberController {
	private final IdentityReviewQueryUseCase identityReviewQueryUseCase;

	@GetMapping("/identity-review")
	@Operation(summary = "확인이 필요한 계정 조회",
		description = "이름과 생년월일이 같고 휴대폰이 다른 회원을 묶음으로 돌려준다. 카카오 인증은 CI가 없어 같은 사람이 휴대폰만 바꿔 "
			+ "여러 계정을 만들었을 수 있으므로 관리자가 확인한다. 가장 최근 가입자가 있는 묶음부터 묶음 단위로 페이지를 나눈다. "
			+ "이름·휴대폰·이메일은 가린 값만, 생년월일은 주지 않는다. 탈퇴 회원과 본인인증 전에 가입한 회원은 빠진다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = AdminMemberApiExamples.IDENTITY_REVIEW_SUCCESS))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "관리자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.NOT_ADMIN))),
		@ApiResponse(responseCode = "404", description = "헤더의 회원이 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.MEMBER_NOT_FOUND)))
	})
	public ResponseEntity<RsData<IdentityReviewPageDto>> findIdentityReview(
		@LoginMemberId long memberId,
		@Parameter(description = "페이지 번호 (0부터, 음수면 0으로 본다)", example = "0")
		@RequestParam(defaultValue = "0") int page,
		@Parameter(description = "페이지 크기, 묶음 수 (1~100, 범위 밖이면 가까운 값으로 맞춘다)", example = "20")
		@RequestParam(defaultValue = "20") int size
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "확인이 필요한 계정을 조회했습니다.",
				identityReviewQueryUseCase.find(memberId, page, size)));
	}
}
