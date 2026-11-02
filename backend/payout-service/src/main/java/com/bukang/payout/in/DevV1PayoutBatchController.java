package com.bukang.payout.in;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.payout.app.PayoutCreateUseCase;
import com.bukang.payout.app.PayoutPayUseCase;
import com.bukang.payout.dto.PayoutCreateResultDto;
import com.bukang.payout.dto.PayoutPayResultDto;

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
 * 개발용: 새벽 스케줄러(03:00 정산 대상일 배치, 04:00 지급 배치)를 기다리지 않고 바로 돌려 확인한다 (MVP 확인용)
 * dev 프로파일에서만 빈이 만들어져, prod에는 이 경로가 없다
 * today를 넘기면 그날 실행한 것처럼 돈다 (예: 지급 배치를 다음 달 20일로 돌려 지급 완료까지 확인)
 */
@Tag(name = "PayoutDev", description = "정산 배치 수동 실행 API (dev 프로파일 전용)")
@Profile("dev")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payout/dev/batches")
public class DevV1PayoutBatchController {
	// 스케줄러와 같이 서버 시간대와 상관없이 한국 날짜로 판단한다
	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	private final PayoutCreateUseCase payoutCreateUseCase;
	private final PayoutPayUseCase payoutPayUseCase;

	@PostMapping("/create")
	@Operation(summary = "정산 대상일 배치 실행",
		description = "today 기준으로 어제부터 7일치 대회의 판매 내역을 주문 서비스에서 받아, 아직 없는 판매자 × 대회 정산 내역을 만든다. "
			+ "today를 비우면 오늘(한국 시간)로 돈다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "실행 완료 (묶음별 생성 · 건너뜀 · 실패 수)",
			content = @Content(examples = @ExampleObject(value = DevPayoutBatchApiExamples.CREATE_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "today 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = DevPayoutBatchApiExamples.INVALID_VALUE_TYPE)))
	})
	public ResponseEntity<RsData<PayoutCreateResultDto>> runCreate(
		@Parameter(description = "실행 날짜 (yyyy-MM-dd). 비우면 오늘", example = "2026-10-13")
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today
	) {
		LocalDateTime now = LocalDateTime.now(ZONE);
		PayoutCreateResultDto result = payoutCreateUseCase.run(today == null ? now.toLocalDate() : today, now);
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "정산 대상일 배치를 실행했습니다.", result));
	}

	@PostMapping("/pay")
	@Operation(summary = "지급 배치 실행",
		description = "지급 예정일이 today 이전인 정산 예정(CALCULATED) 정산 내역의 정산금을 캐시 서비스에 옮기고 지급 완료(PAID)로 "
			+ "바꾼다. 지난 실행에서 결과를 모른 지급 중(PAYING) 정산 내역도 다시 요청한다. 보류(HELD)는 고르지 않는다. "
			+ "today를 비우면 오늘(한국 시간)로 돈다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "실행 완료 (지급 완료 · 실패 · 결과 모름 수)",
			content = @Content(examples = @ExampleObject(value = DevPayoutBatchApiExamples.PAY_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "today 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = DevPayoutBatchApiExamples.INVALID_VALUE_TYPE)))
	})
	public ResponseEntity<RsData<PayoutPayResultDto>> runPay(
		@Parameter(description = "실행 날짜 (yyyy-MM-dd). 비우면 오늘", example = "2026-11-20")
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today
	) {
		LocalDateTime now = LocalDateTime.now(ZONE);
		PayoutPayResultDto result = payoutPayUseCase.run(today == null ? now.toLocalDate() : today, now);
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "지급 배치를 실행했습니다.", result));
	}
}
