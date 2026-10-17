package com.bukang.order.in;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.order.app.ActiveTicketQueryUseCase;
import com.bukang.order.app.SoldEntryQueryUseCase;
import com.bukang.order.app.SoldSummaryQueryUseCase;
import com.bukang.order.dto.ActiveTicketCountDto;
import com.bukang.order.dto.SoldEntryDto;
import com.bukang.order.dto.SoldSummaryDto;

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
 * 다른 서비스가 동기 호출로 부르는 내부 API. /internal/** 은 게이트웨이에 노출하지 않는다 (file-service와 같은 방식)
 * 회원이 아니라 서비스가 부르므로 회원 ID 헤더를 받지 않는다
 */
@Tag(name = "OrderInternal", description = "정산용 판매 내역, 탈퇴 확인용 참가권 수 조회 API (서비스 간 내부 호출 전용)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/order")
public class InternalV1OrderController {
	// 대회일은 한국 날짜라, 서버 시간대(UTC 등)와 상관없이 한국 기준 "오늘"로 센다
	private static final ZoneId RACE_ZONE = ZoneId.of("Asia/Seoul");

	private final SoldEntryQueryUseCase soldEntryQueryUseCase;
	private final SoldSummaryQueryUseCase soldSummaryQueryUseCase;
	private final ActiveTicketQueryUseCase activeTicketQueryUseCase;

	@GetMapping("/sold-entries")
	@Operation(summary = "정산용 판매 내역 조회",
		description = "정산 서비스가 정산 대상일 배치에서 부른다. from ~ to(양 끝 포함, 최대 31일)에 열린 대회의 주문 상품 중 "
			+ "결제 완료이고 취소되지 않은 참가권을 주문 상품 ID 순으로 돌려준다. 정산 배치는 놓친 날을 따라잡으려고 7일치를 묻는다. "
			+ "필드는 정산 서비스의 SoldEntry와 같고, raceCourseId는 참가권의 코스, raceDate는 주문 때 복사한 대회 날짜, "
			+ "price는 1장 값(키트 포함)이다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (없으면 빈 목록)",
			content = @Content(examples = {
				@ExampleObject(name = "판매 내역 있음", value = OrderInternalApiExamples.SOLD_ENTRIES_SUCCESS),
				@ExampleObject(name = "판매 내역 없음", value = OrderInternalApiExamples.SOLD_ENTRIES_EMPTY)
			})),
		@ApiResponse(responseCode = "400", description = "기간 없음, 형식 오류, 시작일 > 종료일, 31일 초과",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "기간 없음", value = OrderInternalApiExamples.MISSING_FROM),
				@ExampleObject(name = "날짜 형식 오류", value = OrderInternalApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "시작일이 종료일보다 늦음", value = OrderInternalApiExamples.FROM_AFTER_TO),
				@ExampleObject(name = "31일 초과", value = OrderInternalApiExamples.PERIOD_TOO_LONG)
			}))
	})
	public ResponseEntity<RsData<List<SoldEntryDto>>> findSoldEntries(
		@Parameter(description = "조회 시작 대회 날짜 (yyyy-MM-dd, 포함)", example = "2026-11-09")
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
		@Parameter(description = "조회 끝 대회 날짜 (yyyy-MM-dd, 포함)", example = "2026-11-15")
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "판매 내역을 조회했습니다.", soldEntryQueryUseCase.findSoldEntries(from, to)));
	}

	@GetMapping("/sold-summary")
	@Operation(summary = "1차 정산용 판매 집계 조회",
		description = "정산 서비스가 1차 정산 생성 배치에서 부른다. 이 대회의 주문 상품 중 결제 완료이고 취소되지 않은 참가권을 "
			+ "판매자 × 코스로 묶어 결제 금액 합(priceSum)과 장수(ticketCount)를 판매자, 코스 순으로 돌려준다. "
			+ "priceSum은 판매 내역 조회의 price와 같은 금액을 더한 값이다. 판매가 없거나 없는 대회면 빈 목록이다. "
			+ "필드는 정산 서비스의 SoldSummary와 같다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (없으면 빈 목록)",
			content = @Content(examples = {
				@ExampleObject(name = "판매 있음", value = OrderInternalApiExamples.SOLD_SUMMARY_SUCCESS),
				@ExampleObject(name = "판매 없음", value = OrderInternalApiExamples.SOLD_SUMMARY_EMPTY)
			})),
		@ApiResponse(responseCode = "400", description = "대회 ID 없음 또는 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "대회 ID 없음", value = OrderInternalApiExamples.MISSING_RACE_ID),
				@ExampleObject(name = "대회 ID 형식 오류", value = OrderInternalApiExamples.INVALID_VALUE_TYPE)
			}))
	})
	public ResponseEntity<RsData<List<SoldSummaryDto>>> summarizeSold(
		@Parameter(description = "대회 ID", example = "5") @RequestParam int raceId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "판매 집계를 조회했습니다.", soldSummaryQueryUseCase.summarizeSold(raceId)));
	}

	// 회원 서비스가 탈퇴 전에 끝나지 않은 참가권이 있는지 묻는다 (BE-022)
	@GetMapping("/members/{memberId}/active-tickets/count")
	@Operation(summary = "끝나지 않은 참가권 수 조회",
		description = "회원 탈퇴 확인용. 회원이 산 참가권 중 결제 완료이고 취소·환불되지 않았으며, "
			+ "대회 날짜(주문 때 복사한 날짜)가 오늘 이후(오늘 포함)인 것의 수를 돌려준다. "
			+ "결제 대기·기한 만료 주문은 아직 참가권이 아니라 세지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (없으면 0)",
			content = @Content(examples = {
				@ExampleObject(name = "참가권 있음", value = OrderInternalApiExamples.ACTIVE_TICKETS_SUCCESS),
				@ExampleObject(name = "참가권 없음", value = OrderInternalApiExamples.ACTIVE_TICKETS_NONE)
			}))
	})
	public ResponseEntity<RsData<ActiveTicketCountDto>> countActiveTickets(
		@Parameter(description = "회원 ID", example = "3") @PathVariable int memberId
	) {
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "끝나지 않은 참가권 수를 조회했습니다.",
			// 결제 기한은 주문을 만들 때 서버 시각(LocalDateTime.now())으로 정하므로 같은 시각으로 비교한다
			activeTicketQueryUseCase.countActiveTickets(memberId, LocalDate.now(RACE_ZONE), LocalDateTime.now())));
	}
}
