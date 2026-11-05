package com.bukang.payout.global.in;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.payout.dto.RefundEligibilityDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 서비스끼리 부르는 내부 API. 게이트웨이에 라우팅하지 않는다(/internal/** 경로)
 * 환불 가능 여부는 정산 규칙(정산 생성일)으로 정해지므로, 주문 서비스가 규칙을 복사하지 않고 여기에 물어본다
 */
@Tag(name = "PayoutInternal", description = "참가권 환불 가능 여부 조회 API (서비스 간 내부 호출 전용)")
@RestController
@RequestMapping("/internal/v1/payout")
public class InternalV1PayoutRefundController {
	// 서버 시간대와 상관없이 한국 날짜로 판단한다 (정산 생성일 배치와 같은 기준)
	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	@GetMapping("/refund-eligibility")
	@Operation(summary = "참가권 환불 가능 여부 조회",
		description = "대회일로 정산 생성일(대회 다음 날)을 구해, 오늘(한국 날짜)이 정산 생성일 전이면 환불 가능으로 돌려준다. "
			+ "정산 생성일 당일부터는 사유와 상관없이(대회 취소·일정 변경 포함) 환불할 수 없다. "
			+ "환불 기한 판단은 주문 서비스가 하고, 환불 요청을 받을 때 이 API를 동기 호출로 부른다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (환불 가능 여부는 data.refundable)",
			content = @Content(examples = {
				@ExampleObject(name = "환불 가능", value = PayoutInternalApiExamples.REFUNDABLE),
				@ExampleObject(name = "환불 불가 (정산 생성일 지남)", value = PayoutInternalApiExamples.NOT_REFUNDABLE)
			})),
		@ApiResponse(responseCode = "400", description = "대회일 없음 또는 대회일 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "대회일 없음", value = PayoutInternalApiExamples.MISSING_PARAMETER),
				@ExampleObject(name = "대회일 형식 오류", value = PayoutInternalApiExamples.INVALID_VALUE_TYPE)
			}))
	})
	public ResponseEntity<RsData<RefundEligibilityDto>> refundEligibility(
		@Parameter(description = "참가권의 대회일 (yyyy-MM-dd)", example = "2026-09-25")
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate raceDate
	) {
		RefundEligibilityDto eligibility = RefundEligibilityDto.of(raceDate, LocalDate.now(ZONE));
		return ResponseEntity.ok(RsData.of(HttpStatus.OK, "환불 가능 여부를 조회했습니다.", eligibility));
	}
}
