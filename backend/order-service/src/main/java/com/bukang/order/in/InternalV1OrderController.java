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
import com.bukang.order.app.TicketQueryUseCase;
import com.bukang.order.dto.ActiveTicketCountDto;
import com.bukang.order.dto.OrderLookupDto;
import com.bukang.order.dto.SoldEntriesDto;
import com.bukang.order.dto.SoldEntriesFormat;
import com.bukang.order.dto.SoldSummaryDto;
import com.bukang.order.dto.TicketDto;

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
@Tag(name = "OrderInternal", description = "정산용 판매 내역, 탈퇴 확인용 참가권 수, 문의용 주문 요약, 기록증 제출용 참가권 조회 API (서비스 간 내부 호출 전용)")
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
	private final TicketQueryUseCase ticketQueryUseCase;

	@GetMapping("/sold-entries")
	@Operation(summary = "정산용 판매 내역 조회",
		description = "정산 서비스가 최종 정산 생성 · 대사 배치에서 부른다. 이 대회의 주문 상품 중 결제 완료이거나 취소 중인 참가권을 "
			+ "코스로 묶어 돌려준다. 대회 · 판매자 · 대회일(주문 때 복사)은 맨 위에 한 번, 코스마다 1장 가격과 "
			+ "결제 완료(paidIds) · 취소 중(blockingIds) 주문 상품 ID를 준다. 코스는 코스 ID 순, ID는 주문 상품 ID 순이다. "
			+ "판매가 없거나 없는 대회면 courses가 빈 배열이고 raceDate · sellerId는 null이다. "
			+ "format이 BITSET · ROARING이면 결제 완료 ID를 paidIds 대신 비트맵으로 준다: paidFormat(형식), "
			+ "paidBase(위치 0의 주문 상품 ID), paidCount(장수), paidBitmap(base64). ID = paidBase + 켜진 비트 위치다. "
			+ "ID 범위가 int를 넘는 코스는 비트맵을 요청해도 paidIds로 준다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (없으면 courses가 빈 배열)",
			content = @Content(examples = {
				@ExampleObject(name = "판매 내역 있음", value = OrderInternalApiExamples.SOLD_ENTRIES_SUCCESS),
				@ExampleObject(name = "비트맵으로 받기 (format=BITSET)", value = OrderInternalApiExamples.SOLD_ENTRIES_BITMAP),
				@ExampleObject(name = "판매 내역 없음", value = OrderInternalApiExamples.SOLD_ENTRIES_EMPTY)
			})),
		@ApiResponse(responseCode = "400", description = "대회 ID 없음, 대회 ID 형식 오류, format 값 오류(IDS · BITSET · ROARING 밖)",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "대회 ID 없음", value = OrderInternalApiExamples.MISSING_RACE_ID),
				@ExampleObject(name = "대회 ID · format 형식 오류", value = OrderInternalApiExamples.INVALID_VALUE_TYPE)
			}))
	})
	public ResponseEntity<RsData<SoldEntriesDto>> findSoldEntries(
		@Parameter(description = "대회 ID", example = "5") @RequestParam long raceId,
		@Parameter(description = "결제 완료 ID를 담는 방식. IDS(기본, 숫자 배열) · BITSET · ROARING(비트맵 base64)", example = "IDS")
		@RequestParam(defaultValue = "IDS") SoldEntriesFormat format
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "판매 내역을 조회했습니다.",
				soldEntryQueryUseCase.findSoldEntries(raceId, format)));
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

	// 회원 서비스가 기록증 제출을 받을 때 참가권 주인·상태를 확인한다 (BUK-271)
	@GetMapping("/order-items/{orderItemId}/ticket")
	@Operation(summary = "기록증 제출용 참가권 조회",
		description = "회원 서비스가 기록증 제출을 받을 때 부른다. 주문 상품 ID로 산 회원 ID(주문의 구매자), 대회 ID, 종목 ID, "
			+ "주문 상품 상태를 돌려준다. 대회·종목 ID는 주문 당시 값이고, status는 OrderItemStatus 이름 그대로"
			+ "(ORDERED · PAID · CANCELING · CANCELED)다. 상품 서비스는 부르지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = {
				@ExampleObject(name = "결제 완료", value = OrderInternalApiExamples.TICKET_PAID),
				@ExampleObject(name = "환불됨", value = OrderInternalApiExamples.TICKET_CANCELED)
			})),
		@ApiResponse(responseCode = "404", description = "없는 참가권",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "없는 참가권", value = OrderInternalApiExamples.TICKET_NOT_FOUND)
			}))
	})
	public ResponseEntity<RsData<TicketDto>> findTicket(
		@Parameter(description = "주문 상품 ID", example = "42") @PathVariable long orderItemId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "참가권을 조회했습니다.", ticketQueryUseCase.findTicket(orderItemId)));
	}
}
