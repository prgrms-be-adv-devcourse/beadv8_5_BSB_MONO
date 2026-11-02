package com.bukang.payout.in;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.payout.app.PayoutSellerFacade;
import com.bukang.payout.domain.PayoutItemPaging;
import com.bukang.payout.dto.PageDto;
import com.bukang.payout.dto.PayoutDto;
import com.bukang.payout.dto.PayoutItemDto;
import com.bukang.payout.dto.PayoutSummaryDto;
import com.bukang.payout.dto.SellerPayoutHoldDto;

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
 * 판매자의 정산 조회: 요약, 정산 내역, 건별 정산, 지급보류 내역
 * 회원 ID(X-Member-Id)를 회원 서비스에 물어 판매자 ID(판매자 신청서 ID)로 바꾸고, 그 판매자의 기록만 돌려준다.
 * 두 값은 다른 번호라 그대로 쓰면 남의 정산이 보인다. 승인된 판매자가 아니면 403, 탈퇴한 회원은 404다 (PayoutSellerFacade)
 */
@Tag(name = "PayoutSeller", description = "정산 요약, 정산 내역, 건별 정산, 지급보류 내역 조회 API (판매자)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payout/seller")
public class ApiV1PayoutSellerController {
	// 서버 시간대와 상관없이 한국 날짜로 다음 정산일을 판단한다 (지급 배치와 같은 기준)
	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	private final PayoutSellerFacade payoutSellerFacade;

	@GetMapping("/summary")
	@Operation(summary = "정산 요약 조회",
		description = "다음 정산일, 정산 예정액(다음 정산일에 나갈 지급액 합계, 보류 제외), 지급보류 중인 정산 내역 수를 돌려준다. "
			+ "1차가 아직 지급되지 않아 기다리는 최종 정산은 다음 정산일 · 정산 예정액에서 뺀다. "
			+ "조회 기간과 상관없이 판매자의 지금 상태로 계산한다. "
			+ "지급 예정 정산 내역이 없으면 다음 정산일은 null, 정산 예정액은 0이다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = {
				@ExampleObject(name = "지급 예정 정산 내역 있음", value = PayoutSellerApiExamples.SUMMARY_SUCCESS),
				@ExampleObject(name = "지급 예정 정산 내역 없음", value = PayoutSellerApiExamples.SUMMARY_EMPTY)
			})),
		@ApiResponse(responseCode = "400", description = "회원 ID 형식 오류 또는 회원 ID가 0 이하",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "회원 ID 형식 오류", value = PayoutSellerApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = PayoutSellerApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutSellerApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "판매자 신청이 없거나 대기 · 반려 상태",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutSellerApiExamples.SELLER_NOT_APPROVED))),
		@ApiResponse(responseCode = "404", description = "존재하지 않거나 탈퇴한 회원",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutSellerApiExamples.MEMBER_NOT_FOUND))),
		@ApiResponse(responseCode = "503", description = "회원 서비스에서 판매자 정보를 확인하지 못함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutSellerApiExamples.SELLER_LOOKUP_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<PayoutSummaryDto>> summary(
		@LoginMemberId long memberId
	) {
		PayoutSummaryDto summary = payoutSellerFacade.summary(memberId, LocalDate.now(ZONE));
		return ResponseEntity.ok(RsData.of(HttpStatus.OK, "정산 요약을 조회했습니다.", summary));
	}

	@GetMapping("/payouts")
	@Operation(summary = "정산 내역 조회",
		description = "정산 월(정산 대상일이 속한 달)이 기간 안인 정산 내역을 최근 달부터 돌려준다. "
			+ "시작 달을 포함해 최대 12개월까지 한 번에 조회한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = PayoutSellerApiExamples.PAYOUTS_SUCCESS))),
		@ApiResponse(responseCode = "400",
			description = "기간 한도 초과, 시작이 끝보다 늦음, 기간 없음, 값 형식 오류 또는 회원 ID가 0 이하",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "12개월 초과", value = PayoutSellerApiExamples.PAYOUT_PERIOD_TOO_LONG),
				@ExampleObject(name = "시작이 끝보다 늦음", value = PayoutSellerApiExamples.PERIOD_REVERSED),
				@ExampleObject(name = "기간 없음", value = PayoutSellerApiExamples.MISSING_PARAMETER),
				@ExampleObject(name = "기간·회원 ID 형식 오류", value = PayoutSellerApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = PayoutSellerApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutSellerApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "판매자 신청이 없거나 대기 · 반려 상태",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutSellerApiExamples.SELLER_NOT_APPROVED))),
		@ApiResponse(responseCode = "404", description = "존재하지 않거나 탈퇴한 회원",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutSellerApiExamples.MEMBER_NOT_FOUND))),
		@ApiResponse(responseCode = "503", description = "회원 서비스에서 판매자 정보를 확인하지 못함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutSellerApiExamples.SELLER_LOOKUP_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<List<PayoutDto>>> payouts(
		@LoginMemberId long memberId,
		@Parameter(description = "조회 시작 정산 월 (yyyy-MM)", example = "2025-11")
		@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth fromMonth,
		@Parameter(description = "조회 끝 정산 월 (yyyy-MM, 포함)", example = "2026-10")
		@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth toMonth
	) {
		List<PayoutDto> payouts = payoutSellerFacade.payouts(memberId, fromMonth, toMonth);
		return ResponseEntity.ok(RsData.of(HttpStatus.OK, "정산 내역을 조회했습니다.", payouts));
	}

	@GetMapping("/payout-items")
	@Operation(summary = "건별 정산 조회",
		description = "정산 대상일이 기간 안인 정산 항목(참가권 1장)을 최근 날부터 페이지로 나눠 돌려준다. "
			+ "끝 날이 시작 날 + 1개월 전이어야 한다(최대 1개월). 페이지는 0부터, 크기는 기본 50 · 최대 100이다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = PayoutSellerApiExamples.ITEMS_SUCCESS))),
		@ApiResponse(responseCode = "400",
			description = "기간 한도 초과, 시작이 끝보다 늦음, 페이지 번호·크기 범위 오류, 기간 없음, 값 형식 오류 "
				+ "또는 회원 ID가 0 이하",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "1개월 초과", value = PayoutSellerApiExamples.DETAIL_PERIOD_TOO_LONG),
				@ExampleObject(name = "시작이 끝보다 늦음", value = PayoutSellerApiExamples.PERIOD_REVERSED),
				@ExampleObject(name = "페이지 번호 음수", value = PayoutSellerApiExamples.INVALID_PAGE),
				@ExampleObject(name = "페이지 크기 범위 밖", value = PayoutSellerApiExamples.INVALID_PAGE_SIZE),
				@ExampleObject(name = "기간 없음", value = PayoutSellerApiExamples.MISSING_PARAMETER),
				@ExampleObject(name = "기간·회원 ID 형식 오류", value = PayoutSellerApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = PayoutSellerApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutSellerApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "판매자 신청이 없거나 대기 · 반려 상태",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutSellerApiExamples.SELLER_NOT_APPROVED))),
		@ApiResponse(responseCode = "404", description = "존재하지 않거나 탈퇴한 회원",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutSellerApiExamples.MEMBER_NOT_FOUND))),
		@ApiResponse(responseCode = "503", description = "회원 서비스에서 판매자 정보를 확인하지 못함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutSellerApiExamples.SELLER_LOOKUP_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<PageDto<PayoutItemDto>>> items(
		@LoginMemberId long memberId,
		@Parameter(description = "조회 시작 정산 대상일 (yyyy-MM-dd)", example = "2026-09-01")
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
		@Parameter(description = "조회 끝 정산 대상일 (yyyy-MM-dd, 포함)", example = "2026-09-30")
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
		@Parameter(description = "페이지 번호 (0부터)", example = "0")
		@RequestParam(defaultValue = "0") int page,
		@Parameter(description = "페이지 크기 (1 ~ 100)", example = "50")
		@RequestParam(defaultValue = "" + PayoutItemPaging.DEFAULT_SIZE) int size
	) {
		PageDto<PayoutItemDto> items = payoutSellerFacade.items(memberId, from, to, page, size);
		return ResponseEntity.ok(RsData.of(HttpStatus.OK, "건별 정산을 조회했습니다.", items));
	}

	@GetMapping("/holds")
	@Operation(summary = "지급보류 내역 조회",
		description = "보류한 날이 기간 안인 지급보류를 최근 것부터 돌려준다. 해제된 보류도 포함한다. "
			+ "끝 날이 시작 날 + 1개월 전이어야 한다(최대 1개월).")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = PayoutSellerApiExamples.HOLDS_SUCCESS))),
		@ApiResponse(responseCode = "400",
			description = "기간 한도 초과, 시작이 끝보다 늦음, 기간 없음, 값 형식 오류 또는 회원 ID가 0 이하",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "1개월 초과", value = PayoutSellerApiExamples.DETAIL_PERIOD_TOO_LONG),
				@ExampleObject(name = "시작이 끝보다 늦음", value = PayoutSellerApiExamples.PERIOD_REVERSED),
				@ExampleObject(name = "기간 없음", value = PayoutSellerApiExamples.MISSING_PARAMETER),
				@ExampleObject(name = "기간·회원 ID 형식 오류", value = PayoutSellerApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = PayoutSellerApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutSellerApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "판매자 신청이 없거나 대기 · 반려 상태",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutSellerApiExamples.SELLER_NOT_APPROVED))),
		@ApiResponse(responseCode = "404", description = "존재하지 않거나 탈퇴한 회원",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutSellerApiExamples.MEMBER_NOT_FOUND))),
		@ApiResponse(responseCode = "503", description = "회원 서비스에서 판매자 정보를 확인하지 못함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutSellerApiExamples.SELLER_LOOKUP_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<List<SellerPayoutHoldDto>>> holds(
		@LoginMemberId long memberId,
		@Parameter(description = "조회 시작 보류일 (yyyy-MM-dd)", example = "2026-09-15")
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
		@Parameter(description = "조회 끝 보류일 (yyyy-MM-dd, 포함)", example = "2026-10-14")
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
	) {
		List<SellerPayoutHoldDto> holds = payoutSellerFacade.holds(memberId, from, to);
		return ResponseEntity.ok(RsData.of(HttpStatus.OK, "지급보류 내역을 조회했습니다.", holds));
	}
}
