package com.bukang.product.in;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.product.app.ProductFacade;
import com.bukang.product.dto.RaceScheduleDto;

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
 * 서비스끼리 부르는 내부 API. 게이트웨이에 라우팅하지 않는다(/internal/** 경로)
 * 정산 서비스의 RacePort(정산 대상 대회 조회)가 정산 생성 배치에서 부른다 (PRO-230)
 */
@Tag(name = "RaceScheduleInternal", description = "정산용 대상 대회 조회 API (서비스 간 내부 호출 전용)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/product/races")
public class InternalV1RaceScheduleController {
	private final ProductFacade productFacade;

	@GetMapping("/by-refund-deadline")
	@Operation(summary = "환불 마감일 기간으로 대회 조회",
		description = "정산 서비스가 1차 정산 생성 배치에서 부른다. 환불 마감일이 from ~ to(양 끝 포함, 최대 31일)인 대회를 "
			+ "환불 마감일, 대회 ID 순으로 돌려준다. 취소된 대회는 빼고, 판매 중지 대회는 넣는다. "
			+ "필드는 정산 서비스 RaceSchedule과 같고, raceName은 대회명, raceDate는 대회 일시다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (없으면 빈 목록)",
			content = @Content(examples = {
				@ExampleObject(name = "대회 있음", value = RaceScheduleInternalApiExamples.SCHEDULES_SUCCESS),
				@ExampleObject(name = "대회 없음", value = RaceScheduleInternalApiExamples.SCHEDULES_EMPTY)
			})),
		@ApiResponse(responseCode = "400", description = "기간 없음, 형식 오류, 시작일 > 종료일, 31일 초과",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "기간 없음", value = RaceScheduleInternalApiExamples.MISSING_FROM),
				@ExampleObject(name = "날짜 형식 오류", value = RaceScheduleInternalApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "시작일이 종료일보다 늦음", value = RaceScheduleInternalApiExamples.FROM_AFTER_TO),
				@ExampleObject(name = "31일 초과", value = RaceScheduleInternalApiExamples.PERIOD_TOO_LONG)
			}))
	})
	public ResponseEntity<RsData<List<RaceScheduleDto>>> findByRefundDeadline(
		@Parameter(description = "조회 시작 환불 마감일 (yyyy-MM-dd, 포함)", example = "2026-10-25")
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
		@Parameter(description = "조회 끝 환불 마감일 (yyyy-MM-dd, 포함)", example = "2026-10-31")
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "정산 대상 대회를 조회했습니다.",
				productFacade.findRacesByRefundDeadline(from, to)));
	}

	@GetMapping("/by-race-date")
	@Operation(summary = "대회일 기간으로 대회 조회",
		description = "정산 서비스가 최종 정산 생성 배치에서 부른다. 대회 날짜가 from ~ to(양 끝 포함, 최대 31일)인 대회를 "
			+ "대회 일시, 대회 ID 순으로 돌려준다. 취소된 대회는 빼고, 판매 중지 대회는 넣는다. "
			+ "필드는 정산 서비스 RaceSchedule과 같고, raceName은 대회명, raceDate는 대회 일시다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (없으면 빈 목록)",
			content = @Content(examples = {
				@ExampleObject(name = "대회 있음", value = RaceScheduleInternalApiExamples.SCHEDULES_SUCCESS),
				@ExampleObject(name = "대회 없음", value = RaceScheduleInternalApiExamples.SCHEDULES_EMPTY)
			})),
		@ApiResponse(responseCode = "400", description = "기간 없음, 형식 오류, 시작일 > 종료일, 31일 초과",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "기간 없음", value = RaceScheduleInternalApiExamples.MISSING_FROM),
				@ExampleObject(name = "날짜 형식 오류", value = RaceScheduleInternalApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "시작일이 종료일보다 늦음", value = RaceScheduleInternalApiExamples.FROM_AFTER_TO),
				@ExampleObject(name = "31일 초과", value = RaceScheduleInternalApiExamples.PERIOD_TOO_LONG)
			}))
	})
	public ResponseEntity<RsData<List<RaceScheduleDto>>> findByRaceDate(
		@Parameter(description = "조회 시작 대회 날짜 (yyyy-MM-dd, 포함)", example = "2026-10-25")
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
		@Parameter(description = "조회 끝 대회 날짜 (yyyy-MM-dd, 포함)", example = "2026-10-31")
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "정산 대상 대회를 조회했습니다.", productFacade.findRacesByRaceDate(from, to)));
	}
}
