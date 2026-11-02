package com.bukang.payout.in;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.payout.app.PayoutAdminFacade;
import com.bukang.payout.dto.PayoutClaimDto;

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
 * 관리자의 청구 입금 확인 (선 환불 후 회수 R4 · R5)
 * 판매자가 청구 금액을 회사 계좌로 보낸 것을 관리자가 확인하면, 판매자 × 대회 청구 하나를 한 번에 끝낸다
 * 회원 ID는 게이트웨이가 토큰에서 꺼내 넣은 X-Member-Id 헤더를 믿는다.
 * 지급보류 API와 같이 PayoutAdminFacade가 먼저 회원 서비스에 물어 관리자가 아니면 403으로 막는다 (PRO-265)
 */
@Tag(name = "PayoutClaim", description = "청구 입금 확인 API (관리자)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payout")
public class ApiV1PayoutClaimController {
	// 서버 시간대와 상관없이 한국 시간으로 남긴다 (지급보류 API와 같은 기준)
	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	private final PayoutAdminFacade payoutAdminFacade;

	// 에러 응답은 GlobalExceptionHandler, PayoutExceptionHandler가 RsData(data: null) 형식으로 반환한다
	@PostMapping("/claims/{claimId}/confirm")
	@Operation(summary = "청구 입금 확인",
		description = "판매자가 청구 금액을 보낸 것을 확인한다. 청구 금액만큼 회수 입금을 원장에 남기고, "
			+ "그 대회의 요청 중인 회수를 입금으로 끝내고, 청구를 입금 확인(CONFIRMED)으로 바꾼다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "확인 성공",
			content = @Content(examples = @ExampleObject(value = PayoutClaimApiExamples.CONFIRM_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "청구 ID·회원 ID 형식 오류 또는 회원 ID가 0 이하",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "청구 ID·회원 ID 형식 오류", value = PayoutApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = PayoutApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "관리자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutApiExamples.NOT_ADMIN))),
		@ApiResponse(responseCode = "404", description = "헤더의 회원(없거나 탈퇴) 또는 청구가 없음",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "청구 없음", value = PayoutClaimApiExamples.CLAIM_NOT_FOUND),
				@ExampleObject(name = "회원 없음", value = PayoutApiExamples.MEMBER_NOT_FOUND)
			})),
		@ApiResponse(responseCode = "409", description = "이미 입금을 확인함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutClaimApiExamples.ALREADY_CONFIRMED))),
		@ApiResponse(responseCode = "503", description = "회원 서비스가 응답하지 않아 관리자인지 확인하지 못함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutApiExamples.MEMBER_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<PayoutClaimDto>> confirm(
		@LoginMemberId long memberId,
		@Parameter(description = "청구 ID", example = "1")
		@PathVariable long claimId
	) {
		PayoutClaimDto claim = payoutAdminFacade.confirmClaim(memberId, claimId, LocalDateTime.now(ZONE));
		return ResponseEntity.ok(RsData.of(HttpStatus.OK, "청구 입금을 확인했습니다.", claim));
	}
}
