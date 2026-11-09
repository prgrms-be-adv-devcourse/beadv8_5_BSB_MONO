package com.bukang.payout.in;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.payout.app.PayoutAdminFacade;
import com.bukang.payout.domain.PayoutCreateFailureType;
import com.bukang.payout.domain.PayoutItemPaging;
import com.bukang.payout.dto.PayoutCreateFailuresDto;

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
 * 관리자 정산 조회 (BUK-108 이상 감지). 관리자가 아니면 PayoutAdminFacade가 403으로 막는다 (PRO-265)
 */
@Tag(name = "Payout", description = "이상 감지 기록 조회 API (관리자)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payout/admin")
public class ApiV1PayoutAdminController {
	private final PayoutAdminFacade payoutAdminFacade;

	@GetMapping("/create-failures")
	@Operation(summary = "이상 감지 기록 조회",
		description = "정산 생성 배치가 남긴 실패 기록(대사 불일치 · 주문 조회 실패 등)을 실행일 기간으로 최근 것부터 돌려준다. "
			+ "카드 숫자(대사 불일치 · 자동으로 채운 참가권 · 주문 조회 실패 건수)는 종류 선택과 상관없이 기간 전체로 센다. "
			+ "끝 날이 시작 날 + 1개월 전이어야 한다(최대 1개월). 기록이 없으면 빈 목록과 카드 0을 돌려준다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (기록이 없어도 200)",
			content = @Content(examples = {
				@ExampleObject(name = "기록 있음", value = PayoutAdminApiExamples.CREATE_FAILURES_SUCCESS),
				@ExampleObject(name = "기록 없음", value = PayoutAdminApiExamples.CREATE_FAILURES_EMPTY)
			})),
		@ApiResponse(responseCode = "400",
			description = "기간 한도 초과, 시작이 끝보다 늦음, 페이지 번호·크기 범위 오류, 기간 없음, 값 형식 오류(없는 종류 포함) "
				+ "또는 회원 ID가 0 이하",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "1개월 초과", value = PayoutAdminApiExamples.FAILURE_PERIOD_TOO_LONG),
				@ExampleObject(name = "시작이 끝보다 늦음", value = PayoutAdminApiExamples.PERIOD_REVERSED),
				@ExampleObject(name = "페이지 번호 음수", value = PayoutAdminApiExamples.INVALID_PAGE),
				@ExampleObject(name = "페이지 크기 범위 밖", value = PayoutAdminApiExamples.INVALID_PAGE_SIZE),
				@ExampleObject(name = "기간 없음", value = PayoutAdminApiExamples.MISSING_PARAMETER),
				@ExampleObject(name = "기간·종류·회원 ID 형식 오류", value = PayoutApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = PayoutApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "관리자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutApiExamples.NOT_ADMIN))),
		@ApiResponse(responseCode = "404", description = "헤더의 회원이 없거나 탈퇴함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutApiExamples.MEMBER_NOT_FOUND))),
		@ApiResponse(responseCode = "503", description = "회원 서비스가 응답하지 않아 관리자인지 확인하지 못함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutApiExamples.MEMBER_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<PayoutCreateFailuresDto>> createFailures(
		@LoginMemberId long memberId,
		@Parameter(description = "조회 시작 실행일 (yyyy-MM-dd)", example = "2026-11-01")
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
		@Parameter(description = "조회 끝 실행일 (yyyy-MM-dd, 포함)", example = "2026-11-06")
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
		@Parameter(description = "실패 종류 (없으면 전체)", example = "대사_불일치")
		@RequestParam(required = false) PayoutCreateFailureType type,
		@Parameter(description = "페이지 번호 (0부터)", example = "0")
		@RequestParam(defaultValue = "0") int page,
		@Parameter(description = "페이지 크기 (1 ~ 100)", example = "50")
		@RequestParam(defaultValue = "" + PayoutItemPaging.DEFAULT_SIZE) int size
	) {
		PayoutCreateFailuresDto failures = payoutAdminFacade.createFailures(memberId, from, to, type, page, size);
		return ResponseEntity.ok(RsData.of(HttpStatus.OK, "이상 감지 기록을 조회했습니다.", failures));
	}
}
