package com.bukang.order.in;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.order.app.SellerKitFacade;
import com.bukang.order.domain.KitDeliveryStatus;
import com.bukang.order.dto.KitShipmentRequestDto;
import com.bukang.order.dto.OrderItemDto;
import com.bukang.order.dto.SellerKitPageDto;
import com.bukang.order.dto.SellerKitSummaryDto;

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
import lombok.RequiredArgsConstructor;

// 판매자 키트 배송 관리 (시안 S08, PRO-197). 로그인한 회원이 승인된 판매자일 때만 쓴다
// 에러 응답은 GlobalExceptionHandler, OrderExceptionHandler가 RsData(data: null) 형식으로 반환한다
@Tag(name = "SellerKit", description = "판매자 키트 배송 관리 API (키트 주문 목록, 배송 건수, 발송, 배송 완료)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/order/seller/kits")
public class ApiV1SellerKitController {
	private static final int MAX_PAGE_SIZE = 100;

	private final SellerKitFacade sellerKitFacade;

	@GetMapping
	@Operation(summary = "내 대회 키트 주문 목록",
		description = "판매자 대회의 키트가 있는 참가권 중 결제했거나 이미 보낸 것을 최근 주문부터 돌려준다. "
			+ "대회(raceId)·배송 상태(status)로 거를 수 있다. 보낸 뒤 취소된 참가권은 orderItemStatus가 CANCELED로 남는다. "
			+ "구매자 이름·사이즈·배송지는 참가 신청 때 판매자 제3자 제공에 동의한 범위다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (없으면 빈 목록)",
			content = @Content(examples = @ExampleObject(value = SellerKitApiExamples.LIST_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "페이지·배송 상태·회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "페이지 크기 초과", value = SellerKitApiExamples.INVALID_PAGE),
				@ExampleObject(name = "없는 배송 상태·숫자 아닌 값", value = SellerKitApiExamples.INVALID_FORMAT),
				@ExampleObject(name = "회원 ID가 0 이하", value = SellerKitApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerKitApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "승인된 판매자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerKitApiExamples.NOT_SELLER))),
		@ApiResponse(responseCode = "503", description = "판매자 확인을 아직 쓸 수 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerKitApiExamples.SELLER_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<SellerKitPageDto>> findKits(
		@LoginMemberId long memberId,
		@Parameter(description = "대회 ID (생략하면 모든 대회)", example = "1")
		@RequestParam(required = false) Long raceId,
		@Parameter(description = "배송 상태 (PREPARING 배송 준비 / SHIPPING 배송 중 / DELIVERED 배송 완료, 생략 가능)")
		@RequestParam(required = false) KitDeliveryStatus status,
		@Parameter(description = "페이지 번호 (0부터)", example = "0")
		@RequestParam(defaultValue = "0") @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.") int page,
		@Parameter(description = "페이지 크기 (1~100)", example = "20")
		@RequestParam(defaultValue = "20") @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
		@Max(value = MAX_PAGE_SIZE, message = "한 번에 100개까지 조회할 수 있습니다.") int size
	) {
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "키트 주문 목록을 조회했습니다.",
			sellerKitFacade.findKits(memberId, raceId, status, page, size)));
	}

	@GetMapping("/summary")
	@Operation(summary = "키트 배송 건수",
		description = "배송 준비·배송 중·배송 완료 건수와, 배송 준비 중 아직 환불할 수 있어 보낼 수 없는(환불 기한 전) 건수를 돌려준다. "
			+ "목록과 같은 기준으로 센다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = SellerKitApiExamples.SUMMARY_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "회원 ID가 0 이하",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerKitApiExamples.INVALID_MEMBER_ID))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerKitApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "승인된 판매자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerKitApiExamples.NOT_SELLER))),
		@ApiResponse(responseCode = "503", description = "판매자 확인 또는 상품 서비스(종목 조회)를 쓸 수 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerKitApiExamples.SELLER_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<SellerKitSummaryDto>> summarize(
		@LoginMemberId long memberId,
		@Parameter(description = "대회 ID (생략하면 모든 대회)", example = "1")
		@RequestParam(required = false) Long raceId
	) {
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "키트 배송 건수를 조회했습니다.",
			sellerKitFacade.summarize(memberId, raceId)));
	}

	@PostMapping("/{orderItemId}/shipment")
	@Operation(summary = "키트 발송 (배송 준비 → 배송 중)",
		description = "택배사와 송장 번호를 넣어 배송 중으로 바꾼다. 결제한 참가권의 키트만, 한 번만 보낸다. "
			+ "정책상 키트는 환불 기한이 지난 뒤 보내므로, 아직 환불할 수 있는 동안(환불 기한 날까지, 일정 변경 뒤 7일, 취소된 대회)은 409다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "발송 처리함",
			content = @Content(examples = @ExampleObject(value = SellerKitApiExamples.SHIP_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "택배사·송장 번호·회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "송장 번호 없음", value = SellerKitApiExamples.INVALID_SHIPMENT),
				@ExampleObject(name = "회원 ID가 0 이하", value = SellerKitApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerKitApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "판매자가 아니거나 다른 판매자의 참가권",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "판매자가 아님", value = SellerKitApiExamples.NOT_SELLER),
				@ExampleObject(name = "다른 판매자의 참가권", value = SellerKitApiExamples.NOT_MY_RACE)
			})),
		@ApiResponse(responseCode = "404", description = "없는 주문 상품",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerKitApiExamples.ITEM_NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "환불 기한 전, 이미 발송함, 결제 전·취소, 키트 없음",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "환불 기한 전", value = SellerKitApiExamples.BEFORE_REFUND_DEADLINE),
				@ExampleObject(name = "이미 발송함", value = SellerKitApiExamples.ALREADY_SHIPPED)
			})),
		@ApiResponse(responseCode = "503", description = "판매자 확인 또는 상품 서비스(종목 조회)를 쓸 수 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerKitApiExamples.SELLER_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<OrderItemDto>> ship(
		@LoginMemberId long memberId,
		@Parameter(description = "주문 상품 ID", example = "12") @PathVariable long orderItemId,
		@Valid @RequestBody KitShipmentRequestDto request
	) {
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "키트를 발송 처리했습니다.",
			sellerKitFacade.ship(memberId, orderItemId, request)));
	}

	@PostMapping("/{orderItemId}/delivery")
	@Operation(summary = "키트 배송 완료 (배송 중 → 배송 완료)",
		description = "보낸 키트를 배송 완료로 바꾼다. 한 번만 바꾼다. 보낸 뒤 취소된 참가권도 키트는 받으므로 바꿀 수 있다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "배송 완료로 바꿈",
			content = @Content(examples = @ExampleObject(value = SellerKitApiExamples.DELIVER_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "회원 ID가 0 이하",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerKitApiExamples.INVALID_MEMBER_ID))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerKitApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "판매자가 아니거나 다른 판매자의 참가권",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "판매자가 아님", value = SellerKitApiExamples.NOT_SELLER),
				@ExampleObject(name = "다른 판매자의 참가권", value = SellerKitApiExamples.NOT_MY_RACE)
			})),
		@ApiResponse(responseCode = "404", description = "없는 주문 상품",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerKitApiExamples.ITEM_NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "아직 발송 전이거나 이미 배송 완료",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerKitApiExamples.NOT_SHIPPED_YET))),
		@ApiResponse(responseCode = "503", description = "판매자 확인을 아직 쓸 수 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerKitApiExamples.SELLER_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<OrderItemDto>> completeDelivery(
		@LoginMemberId long memberId,
		@Parameter(description = "주문 상품 ID", example = "12") @PathVariable long orderItemId
	) {
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "키트를 배송 완료로 바꿨습니다.",
			sellerKitFacade.completeDelivery(memberId, orderItemId)));
	}
}
