package com.bukang.order.in;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.order.app.SellerOrderFacade;
import com.bukang.order.domain.OrderItemStatus;
import com.bukang.order.dto.SellerOrderDetailDto;
import com.bukang.order.dto.SellerOrderPageDto;
import com.bukang.order.dto.SellerPartyPageDto;
import com.bukang.order.dto.SellerSalesSummaryDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

// 판매자 주문 목록·파티별 내역 (시안 S03·S03-1·S05, BUK-227), 기간 판매·취소 합계 (BUK-247·BUK-264). 로그인한 회원이 승인된 판매자일 때만 쓴다
// 에러 응답은 GlobalExceptionHandler, OrderExceptionHandler가 RsData(data: null) 형식으로 반환한다
@Tag(name = "SellerOrder", description = "판매자 주문 API (대회별 주문·참가자 목록, 주문 상세, 파티별 신청·결제 인원)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/order/seller")
public class ApiV1SellerOrderController {
	private static final int MAX_PAGE_SIZE = 100;

	private final SellerOrderFacade sellerOrderFacade;

	@GetMapping("/orders")
	@Operation(summary = "내 대회 주문 목록",
		description = "판매자 대회의 주문 상품(참가권 1장)을 최근 주문부터 돌려준다. 키트 없는 참가권도 나오고, 기한이 지나 만료된 주문은 빠진다. "
			+ "대회·종목·상태·주문일(from~to, 한 번에 12개월까지)로 거른다. 파티 번호(partyId)로 찾으면 주문일은 보지 않는다. "
			+ "keyword로 찾으면 참가자 이름 또는 주문번호에 그 말이 든 주문만 나온다(다른 조건과 함께 걸린다). "
			+ "대시보드 최근 주문은 size를 작게 줘서 같은 목록을 쓴다. 참가자 연락처·생년월일·배송지는 넣지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (없으면 빈 목록)",
			content = @Content(examples = @ExampleObject(value = SellerOrderApiExamples.ORDERS_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "페이지·상태·기간·회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "페이지 크기 초과", value = SellerOrderApiExamples.INVALID_PAGE),
				@ExampleObject(name = "없는 상태·숫자 아닌 값·날짜 형식", value = SellerOrderApiExamples.INVALID_FORMAT),
				@ExampleObject(name = "시작일이 종료일보다 늦음", value = SellerOrderApiExamples.PERIOD_REVERSED),
				@ExampleObject(name = "기간이 12개월 넘음", value = SellerOrderApiExamples.PERIOD_TOO_LONG),
				@ExampleObject(name = "회원 ID가 0 이하", value = SellerOrderApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerOrderApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "승인된 판매자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerOrderApiExamples.NOT_SELLER))),
		@ApiResponse(responseCode = "503", description = "판매자 확인을 아직 쓸 수 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerOrderApiExamples.SELLER_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<SellerOrderPageDto>> findSellerOrders(
		@LoginMemberId long memberId,
		@Parameter(description = "대회 ID (생략하면 모든 대회)", example = "1")
		@RequestParam(required = false) Long raceId,
		@Parameter(description = "종목 ID (생략 가능)", example = "1")
		@RequestParam(required = false) Long raceCourseId,
		@Parameter(description = "주문 상품 상태 (ORDERED 결제 대기 / PAID 결제 완료 / CANCELING 취소 중 / CANCELED 취소, 생략 가능)")
		@RequestParam(required = false) OrderItemStatus status,
		@Parameter(description = "파티 ID로 찾기 (있으면 주문일은 보지 않음)", example = "3")
		@RequestParam(required = false) Long partyId,
		@Parameter(description = "조회 시작일 (주문일, 생략 가능)", example = "2026-10-01")
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
		@Parameter(description = "조회 종료일 (이날 주문까지 포함, 생략 가능)", example = "2026-10-31")
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
		@Parameter(description = "참가자 이름 또는 주문번호로 찾기 (한 칸, 부분 일치, 생략 가능)", example = "박파티")
		@RequestParam(required = false) String keyword,
		@Parameter(description = "페이지 번호 (0부터)", example = "0")
		@RequestParam(defaultValue = "0") @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.") int page,
		@Parameter(description = "페이지 크기 (1~100)", example = "20")
		@RequestParam(defaultValue = "20") @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
		@Max(value = MAX_PAGE_SIZE, message = "한 번에 100개까지 조회할 수 있습니다.") int size
	) {
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "주문 목록을 조회했습니다.",
			sellerOrderFacade.findOrders(memberId, raceId, raceCourseId, status, partyId, from, to, keyword, page,
				size)));
	}

	@GetMapping("/orders/{orderItemId}")
	@Operation(summary = "내 대회 주문 상세",
		description = "판매자 대회의 주문 상품(참가권 1장) 하나를 주문 상세 창에 필요한 칸까지 돌려준다. "
			+ "참가권 단가·결제 금액, 취소 시각·환불 금액·취소 이유, 키트 배송지·배송 요청사항·택배사·송장·이동 기록을 담는다. "
			+ "보내기 전에 취소된 키트 주문도 열린다(kitDeliveryStatus는 목록과 같이 PREPARING, status가 CANCELED). "
			+ "키트가 없거나 결제 전이면 배송 칸은 모두 null이다. 배송지·연락처는 키트 배송 목록과 같은 범위다. "
			+ "취소된 주문(보내기 전 취소, 취소 중)은 배송지·배송 요청사항이 null이다. 보낸 뒤 취소된 참가권은 보인다. "
			+ "kitTrackingUrl은 「배송 조회」 버튼이 여는 Delivery Tracker 주소로, 구매자 주문 상세와 같이 송장이 있을 때만 채운다. "
			+ "대회 이름·대회일·환불 마감은 상품 대회 상세 API로 붙인다. 기한이 지나 만료된 주문은 404다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = SellerOrderApiExamples.ORDER_DETAIL_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "숫자 아닌 주문 상품 ID, 회원 ID가 0 이하",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "숫자 아닌 값", value = SellerOrderApiExamples.INVALID_FORMAT),
				@ExampleObject(name = "회원 ID가 0 이하", value = SellerOrderApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerOrderApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "승인된 판매자가 아님, 다른 판매자 대회의 주문",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "승인된 판매자가 아님", value = SellerOrderApiExamples.NOT_SELLER),
				@ExampleObject(name = "다른 판매자 대회의 주문", value = SellerOrderApiExamples.OTHER_SELLER_ORDER)
			})),
		@ApiResponse(responseCode = "404", description = "없는 주문 상품 또는 기한이 지나 만료된 주문",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerOrderApiExamples.ORDER_ITEM_NOT_FOUND))),
		@ApiResponse(responseCode = "503", description = "판매자 확인을 아직 쓸 수 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerOrderApiExamples.SELLER_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<SellerOrderDetailDto>> findSellerOrder(
		@LoginMemberId long memberId,
		@Parameter(description = "주문 상품 ID", example = "12") @PathVariable long orderItemId
	) {
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "주문 상세를 조회했습니다.",
			sellerOrderFacade.findOrder(memberId, orderItemId)));
	}

	@GetMapping("/parties")
	@Operation(summary = "대회 파티별 내역",
		description = "대회에 신청한 파티마다 신청 인원·결제 인원·결제하지 않은 자리·결제 기한을 최근 파티부터 돌려준다. "
			+ "신청 인원 = 결제 인원 + 결제하지 않은 자리(결제 중인 자리와 기한이 지나 풀린 자리). "
			+ "결제 기한은 결제 진행 중(PAYING)일 때만 의미가 있고, 결제 끝(PAID)·종료(CLOSED)는 화면이 「끝남」으로 보여 준다. "
			+ "파티 신청일(from~to, 한 번에 12개월까지)로 거르고, 파티 번호(partyId)로 찾으면 기간은 보지 않는다. "
			+ "크루 이름은 아직 주지 않고 크루 ID만 준다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (없으면 빈 목록)",
			content = @Content(examples = @ExampleObject(value = SellerOrderApiExamples.PARTIES_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "대회 ID 없음, 페이지·기간·회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "대회 ID 없음", value = SellerOrderApiExamples.MISSING_RACE),
				@ExampleObject(name = "페이지 크기 초과", value = SellerOrderApiExamples.INVALID_PAGE),
				@ExampleObject(name = "숫자 아닌 값·날짜 형식", value = SellerOrderApiExamples.INVALID_FORMAT),
				@ExampleObject(name = "시작일이 종료일보다 늦음", value = SellerOrderApiExamples.PERIOD_REVERSED),
				@ExampleObject(name = "기간이 12개월 넘음", value = SellerOrderApiExamples.PERIOD_TOO_LONG),
				@ExampleObject(name = "회원 ID가 0 이하", value = SellerOrderApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerOrderApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "승인된 판매자가 아님, 다른 판매자의 대회",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "승인된 판매자가 아님", value = SellerOrderApiExamples.NOT_SELLER),
				@ExampleObject(name = "다른 판매자의 대회", value = SellerOrderApiExamples.OTHER_SELLER_PARTY)
			})),
		@ApiResponse(responseCode = "404", description = "파티의 종목을 상품 서비스에서 찾지 못함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerOrderApiExamples.COURSE_NOT_FOUND))),
		@ApiResponse(responseCode = "503", description = "판매자 확인 또는 상품 서비스(종목 조회)를 쓸 수 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerOrderApiExamples.SELLER_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<SellerPartyPageDto>> findSellerParties(
		@LoginMemberId long memberId,
		@Parameter(description = "대회 ID", example = "1")
		@RequestParam long raceId,
		@Parameter(description = "파티 ID로 찾기 (있으면 기간은 보지 않음)", example = "3")
		@RequestParam(required = false) Long partyId,
		@Parameter(description = "조회 시작일 (파티 신청일, 생략 가능)", example = "2026-10-01")
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
		@Parameter(description = "조회 종료일 (이날 신청까지 포함, 생략 가능)", example = "2026-10-31")
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
		@Parameter(description = "페이지 번호 (0부터)", example = "0")
		@RequestParam(defaultValue = "0") @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.") int page,
		@Parameter(description = "페이지 크기 (1~100)", example = "20")
		@RequestParam(defaultValue = "20") @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
		@Max(value = MAX_PAGE_SIZE, message = "한 번에 100개까지 조회할 수 있습니다.") int size
	) {
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "파티별 내역을 조회했습니다.",
			sellerOrderFacade.findParties(memberId, raceId, partyId, from, to, page, size)));
	}

	@GetMapping("/orders/summary")
	@Operation(summary = "기간 판매·취소 합계",
		description = "기간은 from~to(종료일 포함, 한 번에 12개월까지)다. "
			+ "판매(soldCount·soldAmount): 결제일이 기간 안인 판매자 대회 참가권 중 결제 완료·취소 중인 것의 장수와 결제 금액 합. "
			+ "정산이 판매를 세는 기준과 같다(취소 중은 환불이 거절되면 결제 완료로 돌아오므로 판매로 센다). "
			+ "취소(canceledCount·refundedAmount): 취소를 마친 시각이 기간 안인 참가권의 장수와 환불액 합(대회 취소 일괄 환불 포함, 취소 중은 아직 안 셈). "
			+ "결제 전 주문은 빠진다. 판매자 AI 「이번 달 판매액」이 쓴다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (판매가 없으면 0)",
			content = @Content(examples = @ExampleObject(value = SellerOrderApiExamples.SALES_SUMMARY_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "기간 없음·형식 오류, 기간 오류, 회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "기간 없음", value = SellerOrderApiExamples.MISSING_FROM),
				@ExampleObject(name = "날짜 형식", value = SellerOrderApiExamples.INVALID_FORMAT),
				@ExampleObject(name = "시작일이 종료일보다 늦음", value = SellerOrderApiExamples.PERIOD_REVERSED),
				@ExampleObject(name = "기간이 12개월 넘음", value = SellerOrderApiExamples.PERIOD_TOO_LONG),
				@ExampleObject(name = "회원 ID가 0 이하", value = SellerOrderApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerOrderApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "승인된 판매자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerOrderApiExamples.NOT_SELLER))),
		@ApiResponse(responseCode = "503", description = "판매자 확인을 아직 쓸 수 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerOrderApiExamples.SELLER_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<SellerSalesSummaryDto>> summarizeSellerSales(
		@LoginMemberId long memberId,
		@Parameter(description = "조회 시작일 (결제일)", example = "2026-11-01")
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
		@Parameter(description = "조회 종료일 (이날 결제까지 포함)", example = "2026-11-16")
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
	) {
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "판매 합계를 조회했습니다.",
			sellerOrderFacade.summarizeSales(memberId, from, to)));
	}
}
