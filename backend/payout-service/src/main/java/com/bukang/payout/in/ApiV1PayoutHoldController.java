package com.bukang.payout.in;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.payout.app.PayoutAdminFacade;
import com.bukang.payout.dto.PayoutHoldDto;
import com.bukang.payout.dto.PayoutHoldRequestDto;

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

/**
 * 관리자의 지급보류 걸기 · 풀기
 * 회원 ID는 게이트웨이가 토큰에서 꺼내 넣은 X-Member-Id 헤더를 믿는다. 게이트웨이는 역할을 보지 않으므로,
 * PayoutAdminFacade가 먼저 회원 서비스에 물어 관리자가 아니면 403으로 막는다 (PRO-265)
 */
@Tag(name = "Payout", description = "지급보류 걸기, 지급보류 풀기 API (관리자)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payout")
public class ApiV1PayoutHoldController {
	// 서버 시간대와 상관없이 한국 날짜로 해제일을 판단한다 (지급 배치와 같은 기준)
	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	private final PayoutAdminFacade payoutAdminFacade;

	// ResponseEntity.created()의 201은 springdoc이 추론하지 못하므로 응답 코드를 직접 명시한다
	// 에러 응답은 GlobalExceptionHandler, PayoutExceptionHandler가 RsData(data: null) 형식으로 반환한다
	@PostMapping("/payouts/{payoutId}/holds")
	@Operation(summary = "지급보류 걸기",
		description = "정산 예정(CALCULATED)인 정산 내역에 지급보류를 걸어 지급 배치에서 지급되지 않게 한다. "
			+ "한 정산 내역에는 보류를 하나만 걸 수 있고, 지급보류 ID(HD-)를 붙인다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "보류 성공",
			content = @Content(examples = @ExampleObject(value = PayoutApiExamples.HOLD_SUCCESS))),
		@ApiResponse(responseCode = "400",
			description = "입력값 검증 실패, 요청 본문 형식 오류, 정산 내역 ID·회원 ID 형식 오류 또는 회원 ID가 0 이하",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "보류 사유 없음", value = PayoutApiExamples.REASON_TYPE_REQUIRED),
				@ExampleObject(name = "사유 상세 500자 초과", value = PayoutApiExamples.REASON_TOO_LONG),
				@ExampleObject(name = "요청 본문 형식 오류 (없는 사유 값 포함)", value = PayoutApiExamples.INVALID_BODY),
				@ExampleObject(name = "정산 내역 ID·회원 ID 형식 오류", value = PayoutApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = PayoutApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "관리자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutApiExamples.NOT_ADMIN))),
		@ApiResponse(responseCode = "404", description = "헤더의 회원(없거나 탈퇴) 또는 정산 내역이 없음",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "정산 내역 없음", value = PayoutApiExamples.PAYOUT_NOT_FOUND),
				@ExampleObject(name = "회원 없음", value = PayoutApiExamples.MEMBER_NOT_FOUND)
			})),
		@ApiResponse(responseCode = "409", description = "이미 보류 중, 이미 지급 완료, 대회 취소로 닫힘 또는 다른 요청과 동시 수정",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "이미 보류 중", value = PayoutApiExamples.ALREADY_HELD),
				@ExampleObject(name = "이미 지급 완료", value = PayoutApiExamples.ALREADY_PAID),
				@ExampleObject(name = "대회 취소로 닫힘", value = PayoutApiExamples.HOLD_CANCELED_PAYOUT),
				@ExampleObject(name = "동시 수정", value = PayoutApiExamples.CONCURRENT_UPDATE)
			})),
		@ApiResponse(responseCode = "503", description = "회원 서비스가 응답하지 않아 관리자인지 확인하지 못함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutApiExamples.MEMBER_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<PayoutHoldDto>> hold(
		@LoginMemberId long memberId,
		@Parameter(description = "정산 내역 ID", example = "1")
		@PathVariable long payoutId,
		@Valid @RequestBody PayoutHoldRequestDto request
	) {
		PayoutHoldDto hold = payoutAdminFacade.hold(
			memberId, payoutId, request.getReasonType(), request.getReason(), LocalDateTime.now(ZONE));
		return ResponseEntity.created(URI.create("/api/v1/payout/holds/" + hold.getId()))
			.body(RsData.of(HttpStatus.CREATED, "지급보류를 걸었습니다.", hold));
	}

	@PostMapping("/holds/{holdId}/release")
	@Operation(summary = "지급보류 풀기",
		description = "지급보류를 해제하고 정산 내역을 정산 예정(CALCULATED)으로 돌린다. "
			+ "지급 예정일은 다시 정한다. 1차 · 최종 모두 해제일 다음 날(주말에도 지급)이며, "
			+ "원래 지급 예정일(1차는 환불 마감일 + 2일, 최종은 대회일 + 2일)보다 앞당기지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "해제 성공",
			content = @Content(examples = @ExampleObject(value = PayoutApiExamples.RELEASE_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "지급보류 ID·회원 ID 형식 오류 또는 회원 ID가 0 이하",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "지급보류 ID·회원 ID 형식 오류", value = PayoutApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = PayoutApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "관리자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutApiExamples.NOT_ADMIN))),
		@ApiResponse(responseCode = "404", description = "헤더의 회원(없거나 탈퇴) 또는 지급보류가 없음",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "지급보류 없음", value = PayoutApiExamples.HOLD_NOT_FOUND),
				@ExampleObject(name = "회원 없음", value = PayoutApiExamples.MEMBER_NOT_FOUND)
			})),
		@ApiResponse(responseCode = "409", description = "이미 해제됨, 대회 취소로 닫힌 정산 내역 또는 다른 요청과 동시 수정",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "이미 해제됨", value = PayoutApiExamples.ALREADY_RELEASED),
				@ExampleObject(name = "대회 취소로 닫힘", value = PayoutApiExamples.RELEASE_CANCELED_PAYOUT),
				@ExampleObject(name = "동시 수정", value = PayoutApiExamples.CONCURRENT_UPDATE)
			})),
		@ApiResponse(responseCode = "503", description = "회원 서비스가 응답하지 않아 관리자인지 확인하지 못함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutApiExamples.MEMBER_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<PayoutHoldDto>> release(
		@LoginMemberId long memberId,
		@Parameter(description = "지급보류 ID (보류 걸기 응답의 id)", example = "1")
		@PathVariable long holdId
	) {
		PayoutHoldDto hold = payoutAdminFacade.release(memberId, holdId, LocalDateTime.now(ZONE));
		return ResponseEntity.ok(RsData.of(HttpStatus.OK, "지급보류를 풀었습니다.", hold));
	}
}
