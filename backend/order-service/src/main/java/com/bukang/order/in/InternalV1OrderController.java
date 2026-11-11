package com.bukang.order.in;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.order.app.ActiveTicketQueryUseCase;
import com.bukang.order.app.OrderLookupQueryUseCase;
import com.bukang.order.app.SoldEntryQueryUseCase;
import com.bukang.order.app.SoldSummaryQueryUseCase;
import com.bukang.order.dto.ActiveTicketCountDto;
import com.bukang.order.dto.OrderLookupDto;
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
@Tag(name = "OrderInternal", description = "정산용 판매 내역, 탈퇴 확인용 참가권 수, 문의용 주문 요약 조회 API (서비스 간 내부 호출 전용)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/order")
public class InternalV1OrderController {
	// 대회일은 한국 날짜라, 서버 시간대(UTC 등)와 상관없이 한국 기준 "오늘"로 센다
	private static final ZoneId RACE_ZONE = ZoneId.of("Asia/Seoul");

	private final SoldEntryQueryUseCase soldEntryQueryUseCase;
	private final SoldSummaryQueryUseCase soldSummaryQueryUseCase;
	private final ActiveTicketQueryUseCase activeTicketQueryUseCase;
	private final OrderLookupQueryUseCase orderLookupQueryUseCase;

	@GetMapping("/sold-entries")
	@Operation(summary = "정산용 판매 내역 조회",
		description = "정산 서비스가 최종 정산 생성 배치에서 부른다. 이 대회의 주문 상품 중 결제 완료이고 취소되지 않은 참가권을 "
			+ "주문 상품 ID 순으로 돌려준다. 판매가 없거나 없는 대회면 빈 목록이다. "
			+ "필드는 정산 서비스의 SoldEntry와 같고, raceCourseId는 참가권의 코스, raceDate는 주문 때 복사한 대회 날짜, "
			+ "price는 1장 값(키트 포함)이다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (없으면 빈 목록)",
			content = @Content(examples = {
				@ExampleObject(name = "판매 내역 있음", value = OrderInternalApiExamples.SOLD_ENTRIES_SUCCESS),
				@ExampleObject(name = "판매 내역 없음", value = OrderInternalApiExamples.SOLD_ENTRIES_EMPTY)
			})),
		@ApiResponse(responseCode = "400", description = "대회 ID 없음 또는 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "대회 ID 없음", value = OrderInternalApiExamples.MISSING_RACE_ID),
				@ExampleObject(name = "대회 ID 형식 오류", value = OrderInternalApiExamples.INVALID_VALUE_TYPE)
			}))
	})
	public ResponseEntity<RsData<List<SoldEntryDto>>> findSoldEntries(
		@Parameter(description = "대회 ID", example = "5") @RequestParam long raceId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "판매 내역을 조회했습니다.", soldEntryQueryUseCase.findSoldEntries(raceId)));
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
		@Parameter(description = "대회 ID", example = "5") @RequestParam long raceId
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
		@Parameter(description = "회원 ID", example = "3") @PathVariable long memberId
	) {
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "끝나지 않은 참가권 수를 조회했습니다.",
			// 결제 기한은 주문을 만들 때 서버 시각(LocalDateTime.now())으로 정하므로 같은 시각으로 비교한다
			activeTicketQueryUseCase.countActiveTickets(memberId, LocalDate.now(RACE_ZONE), LocalDateTime.now())));
	}

	// 문의 서비스가 문의를 만들 때 주인·대회를 확인하고, 판매자·관리자 상세에 주문 정보를 보여 줄 때 부른다 (BUK-160)
	@GetMapping("/orders/{orderId}/summary")
	@Operation(summary = "문의용 주문 요약 조회",
		description = "문의 서비스가 부른다. 주문한 회원 ID, 주문번호, 결제 금액, 주문 상태와 주문 상품마다 대회 ID·대회명·종목명·"
			+ "키트 요약·키트 배송 상태를 돌려준다. status는 OrderStatus, deliveryStatus는 KitDeliveryStatus 이름 그대로다. "
			+ "deliveryStatus는 구매자 주문 상세와 같은 값이고, 키트가 없으면 kitSummary·deliveryStatus는 null이다. "
			+ "대회명·종목명은 상품 서비스에서 받고, 받지 못하면 raceName에 주문 때 남긴 상품명(대회명 종목명)을, courseName에 null을 준다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = {
				@ExampleObject(name = "키트 있음", value = OrderInternalApiExamples.ORDER_LOOKUP_SUCCESS),
				@ExampleObject(name = "키트 없음", value = OrderInternalApiExamples.ORDER_LOOKUP_NO_KIT)
			})),
		@ApiResponse(responseCode = "404", description = "없는 주문",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "없는 주문", value = OrderInternalApiExamples.ORDER_NOT_FOUND)
			}))
	})
	public ResponseEntity<RsData<OrderLookupDto>> findOrder(
		@Parameter(description = "주문 ID", example = "12") @PathVariable long orderId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "주문 요약을 조회했습니다.", orderLookupQueryUseCase.findOrder(orderId)));
	}
}
