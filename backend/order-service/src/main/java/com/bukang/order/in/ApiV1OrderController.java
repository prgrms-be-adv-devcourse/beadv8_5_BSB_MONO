package com.bukang.order.in;

import java.net.URI;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.global.web.IdempotencyKeyHeader;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.order.app.OrderCancelUseCase;
import com.bukang.order.app.OrderCreateResult;
import com.bukang.order.app.OrderFacade;
import com.bukang.order.app.OrderPayResult;
import com.bukang.order.dto.OrderCreateRequestDto;
import com.bukang.order.dto.OrderDto;
import com.bukang.order.dto.OrderPageDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@Tag(name = "Order", description = "바로 결제 예매(주문 생성·자리 임시 확보), 예치금 결제, 취소·환불, 주문 내역 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/order/orders")
public class ApiV1OrderController {
	private static final int MAX_PAGE_SIZE = 100;

	private final OrderFacade orderFacade;

	// 201·200·409(매진)를 직접 고르므로 응답 코드를 모두 명시한다
	// 에러 응답은 GlobalExceptionHandler, OrderExceptionHandler가 RsData(data: null) 형식으로 반환한다
	@PostMapping
	@Operation(summary = "바로 결제 예매 ([결제하기])",
		description = "대기열에 입장 중인지 확인하고, 자리 확보 전(PENDING) 참가 신청으로 상품 서비스에 자리 1석을 확보받아 "
			+ "입장 끝 시각을 결제 기한으로 하는 주문(CREATED)을 만든다. 입장 중이 아니면 403(ORDER_QUEUE_NOT_ADMITTED). "
			+ "같은 Idempotency-Key로 다시 보내면 새 주문 없이 처음 주문을 돌려준다. "
			+ "남은 자리가 없으면 참가 신청이 확보 실패(FAILED)가 되고 주문은 만들지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "자리 확보, 주문 생성",
			content = @Content(examples = @ExampleObject(value = OrderApiExamples.CREATE_SUCCESS))),
		@ApiResponse(responseCode = "200", description = "같은 Idempotency-Key로 이미 만든 주문",
			content = @Content(examples = @ExampleObject(value = OrderApiExamples.CREATE_EXISTING))),
		@ApiResponse(responseCode = "400", description = "입력값·헤더 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 검증 실패", value = OrderApiExamples.INVALID_INPUT),
				@ExampleObject(name = "요청 본문 형식 오류", value = OrderApiExamples.INVALID_BODY),
				@ExampleObject(name = "Idempotency-Key 없음", value = OrderApiExamples.MISSING_IDEMPOTENCY_KEY),
				@ExampleObject(name = "Idempotency-Key 길이 오류", value = OrderApiExamples.INVALID_IDEMPOTENCY_KEY),
				@ExampleObject(name = "회원 ID 형식 오류", value = OrderApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = OrderApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = OrderApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "다른 회원의 참가 신청, 대기열에 입장 중이 아님",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "다른 회원의 참가 신청", value = OrderApiExamples.NOT_OWNER),
				@ExampleObject(name = "대기열 입장 전", value = OrderApiExamples.QUEUE_NOT_ADMITTED)
			})),
		@ApiResponse(responseCode = "404", description = "없는 참가 신청 또는 종목",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "없는 참가 신청", value = OrderApiExamples.PARTICIPATION_NOT_FOUND),
				@ExampleObject(name = "없는 종목", value = OrderApiExamples.COURSE_NOT_FOUND)
			})),
		@ApiResponse(responseCode = "409", description = "매진, 결제할 수 없는 상태, 접수 기간 아님, 요청 겹침, 키 재사용",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "매진", value = OrderApiExamples.SOLD_OUT),
				@ExampleObject(name = "결제할 수 없는 상태", value = OrderApiExamples.NOT_PAYABLE),
				@ExampleObject(name = "접수 기간 아님", value = OrderApiExamples.NOT_ACCEPTING),
				@ExampleObject(name = "요청 겹침", value = OrderApiExamples.CONFLICT),
				@ExampleObject(name = "다른 신청서에 쓴 키", value = OrderApiExamples.KEY_REUSED)
			}))
	})
	public ResponseEntity<RsData<OrderDto>> create(
		@LoginMemberId long memberId,
		@Parameter(description = "중복 주문 방지 키. 같은 [결제하기] 재시도에는 같은 값을 보낸다 (예: UUID)",
			example = "6f1c2a7e-3b9d-4f0a-9c55-0d2e8b7a1f43")
		@RequestHeader(IdempotencyKeyHeader.NAME)
		@Size(min = 1, max = IdempotencyKeyHeader.MAX_LENGTH, message = IdempotencyKeyHeader.INVALID_MESSAGE)
		String idempotencyKey,
		@Valid @RequestBody OrderCreateRequestDto request
	) {
		OrderCreateResult result = orderFacade.create(request, idempotencyKey, memberId);
		return switch (result.outcome()) {
			case CREATED -> ResponseEntity.created(URI.create("/api/v1/order/orders/" + result.order().getId()))
				.body(RsData.of(HttpStatus.CREATED, OrderCreateResult.CREATED_MESSAGE, result.order()));
			case EXISTING -> ResponseEntity.ok()
				.body(RsData.of(HttpStatus.OK, OrderCreateResult.EXISTING_MESSAGE, result.order()));
			case SOLD_OUT -> ResponseEntity.status(HttpStatus.CONFLICT)
				.body(RsData.of(HttpStatus.CONFLICT, OrderCreateResult.SOLD_OUT_CODE,
					OrderCreateResult.SOLD_OUT_MESSAGE, null));
		};
	}

	@PostMapping("/{orderId}/payment")
	@Operation(summary = "예치금 결제",
		description = "결제 대기(CREATED) 주문을 예치금으로 결제하고 참가를 확정한다. 순서: 예치금 차감 → 자리 판매 → 주문 PAID·참가 신청 CONFIRMED. "
			+ "잔액이 부족하면 주문과 자리를 그대로 두므로, 충전한 뒤 같은 주문으로 다시 결제한다. "
			+ "기한이 지나 자리 판매가 거절되면 차감한 예치금을 돌려주고 409를 돌려준다. 이미 결제한 주문은 다시 차감하지 않고 200으로 돌려준다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "결제 완료 또는 이미 결제한 주문",
			content = @Content(examples = {
				@ExampleObject(name = "결제 완료", value = OrderApiExamples.PAY_SUCCESS),
				@ExampleObject(name = "이미 결제함", value = OrderApiExamples.PAY_ALREADY)
			})),
		@ApiResponse(responseCode = "400", description = "주문 ID·회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "주문 ID·회원 ID 형식 오류", value = OrderApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = OrderApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = OrderApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "다른 회원의 주문",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = OrderApiExamples.PAY_NOT_OWNER))),
		@ApiResponse(responseCode = "404", description = "없는 주문",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = OrderApiExamples.ORDER_NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "잔액 부족, 기한 지남, 자리 판매 거절(예치금 환원), 결제 대기가 아닌 주문",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "잔액 부족", value = OrderApiExamples.INSUFFICIENT_BALANCE),
				@ExampleObject(name = "기한 지남", value = OrderApiExamples.PAY_DEADLINE_PASSED),
				@ExampleObject(name = "자리 판매 거절", value = OrderApiExamples.SELL_EXPIRED),
				@ExampleObject(name = "결제 대기가 아님", value = OrderApiExamples.NOT_PAYABLE_ORDER)
			}))
	})
	public ResponseEntity<RsData<OrderDto>> pay(
		@LoginMemberId long memberId,
		@Parameter(description = "주문 ID", example = "1")
		@PathVariable long orderId
	) {
		OrderPayResult result = orderFacade.pay(orderId, memberId);
		String message = result.alreadyPaid() ? "이미 결제한 주문입니다." : "결제했습니다. 참가가 확정되었습니다.";
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, message, result.order()));
	}

	@PostMapping("/{orderId}/items/{orderItemId}/cancel")
	@Operation(summary = "참가권 취소·환불",
		description = "결제한 주문 상품(참가권, 키트 포함)을 취소하고 예치금으로 전액 돌려준다. 환불 기한 날짜까지(그날 23:59:59 포함)는 자동 승인하고, "
			+ "판매 시작 뒤 대회 일정이 바뀌었으면 바뀐 날(알린 날)부터 7일 동안은 환불 기한과 상관없이 자동 승인한다. "
			+ "일부만 취소하면 주문은 PARTIAL_CANCELED, 모두 취소하면 CANCELED가 되고, 참가 신청은 CANCELED가 되어 같은 대회에 다시 신청할 수 있다. "
			+ "정산 대상일(대회 다음 날)이 지난 참가권은 정산 서비스가 거절한다. 같은 취소를 다시 보내면 409로 막혀 환불은 한 번만 일어난다. "
			+ "예치금 환불 응답을 받지 못하면(시간 초과·5xx) 202로 답하고 주문 상품은 취소 중(CANCELING)으로 남는다. "
			+ "1분 뒤부터 재처리가 환불을 다시 확인해 취소로 마무리하므로, 주문 상세를 다시 조회해 상태를 본다. "
			+ "예치금이 환불을 거절하면(4xx) 결제 완료로 되돌리고 503으로 답한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "취소·환불 완료",
			content = @Content(examples = @ExampleObject(value = OrderApiExamples.CANCEL_SUCCESS))),
		@ApiResponse(responseCode = "202", description = "취소 처리 중 (예치금 환불 결과를 아직 모름, 재처리가 마무리)",
			content = @Content(examples = @ExampleObject(value = OrderApiExamples.CANCEL_PENDING))),
		@ApiResponse(responseCode = "400", description = "주문 ID·주문 상품 ID·회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "ID 형식 오류", value = OrderApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = OrderApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = OrderApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "다른 회원의 주문",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = OrderApiExamples.CANCEL_NOT_OWNER))),
		@ApiResponse(responseCode = "404", description = "없는 주문·주문 상품 또는 종목",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "없는 주문", value = OrderApiExamples.ORDER_NOT_FOUND),
				@ExampleObject(name = "주문에 없는 상품", value = OrderApiExamples.ORDER_ITEM_NOT_FOUND),
				@ExampleObject(name = "없는 종목", value = OrderApiExamples.COURSE_NOT_FOUND)
			})),
		@ApiResponse(responseCode = "409", description = "이미 취소함, 취소 처리 중, 결제 전 상품, 환불 기한 지남, 정산 대상일 지남",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "이미 취소함", value = OrderApiExamples.ALREADY_CANCELED),
				@ExampleObject(name = "취소 처리 중", value = OrderApiExamples.ALREADY_CANCELING),
				@ExampleObject(name = "결제 전 상품", value = OrderApiExamples.NOT_PAID_ITEM),
				@ExampleObject(name = "환불 기한 지남", value = OrderApiExamples.REFUND_PERIOD_PASSED),
				@ExampleObject(name = "환불 기한·일정 변경 환불 기간 지남", value = OrderApiExamples.SCHEDULE_CHANGE_REFUND_PASSED),
				@ExampleObject(name = "정산 대상일 지남", value = OrderApiExamples.PAYOUT_STARTED)
			})),
		@ApiResponse(responseCode = "503",
			description = "정산 서비스에 환불 가능 여부를 묻지 못함 (연결 실패, 시간 초과, 오류 응답, 응답 형식 오류), "
				+ "예치금이 환불을 거절함(결제 완료로 되돌림)",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "정산 서비스 연결 실패", value = OrderApiExamples.PAYOUT_UNAVAILABLE),
				@ExampleObject(name = "예치금 환불 거절", value = OrderApiExamples.WALLET_UNAVAILABLE)
			}))
	})
	public ResponseEntity<RsData<OrderDto>> cancelItem(
		@LoginMemberId long memberId,
		@Parameter(description = "주문 ID", example = "1")
		@PathVariable long orderId,
		@Parameter(description = "취소할 주문 상품 ID", example = "1")
		@PathVariable long orderItemId
	) {
		OrderCancelUseCase.CancelResult result = orderFacade.cancelItem(orderId, orderItemId, memberId);
		if (result.refundPending()) {
			return ResponseEntity.accepted().body(RsData.of(HttpStatus.ACCEPTED,
				"취소 처리 중입니다. 예치금 환불이 확인되면 취소가 마무리됩니다.", result.order()));
		}
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK,
			"취소했습니다. 예치금 %,d원을 돌려드렸습니다.".formatted(result.refundedAmount()), result.order()));
	}

	@GetMapping
	@Operation(summary = "내 주문 목록",
		description = "내 주문을 최신순으로 돌려준다. 조회 기간(from·to, 주문 일자 기준)은 각각 생략할 수 있고, to 날짜의 주문까지 포함한다. "
			+ "상품은 대표 상품명과 개수만 담는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (주문이 없으면 빈 목록)",
			content = @Content(examples = @ExampleObject(value = OrderApiExamples.LIST_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "조회 기간·페이지·회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "시작일이 종료일보다 늦음", value = OrderApiExamples.INVALID_PERIOD),
				@ExampleObject(name = "페이지 크기 초과", value = OrderApiExamples.INVALID_PAGE),
				@ExampleObject(name = "날짜·숫자 형식 오류", value = OrderApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = OrderApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = OrderApiExamples.LOGIN_REQUIRED)))
	})
	public ResponseEntity<RsData<OrderPageDto>> findMine(
		@LoginMemberId long memberId,
		@Parameter(description = "조회 시작일 (주문 일자, 생략 가능)", example = "2026-10-01")
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
		@Parameter(description = "조회 종료일 (이날 주문까지 포함, 생략 가능)", example = "2026-10-31")
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
		@Parameter(description = "페이지 번호 (0부터)", example = "0")
		@RequestParam(defaultValue = "0") @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.") int page,
		@Parameter(description = "페이지 크기 (1~100)", example = "20")
		@RequestParam(defaultValue = "20") @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
		@Max(value = MAX_PAGE_SIZE, message = "한 번에 100개까지 조회할 수 있습니다.") int size
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "주문 내역을 조회했습니다.", orderFacade.findMine(memberId, from, to, page, size)));
	}

	@GetMapping("/{orderId}")
	@Operation(summary = "주문 상세", description = "본인 주문의 상품·상태·금액·결제 기한을 돌려준다. 상품명·단가는 주문 당시 값이다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = OrderApiExamples.FIND_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "주문 ID·회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "주문 ID·회원 ID 형식 오류", value = OrderApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = OrderApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = OrderApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "다른 회원의 주문",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = OrderApiExamples.VIEW_NOT_OWNER))),
		@ApiResponse(responseCode = "404", description = "없는 주문",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = OrderApiExamples.ORDER_NOT_FOUND)))
	})
	public ResponseEntity<RsData<OrderDto>> findById(
		@LoginMemberId long memberId,
		@Parameter(description = "주문 ID", example = "1")
		@PathVariable long orderId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "주문을 조회했습니다.", orderFacade.findById(orderId, memberId)));
	}
}
