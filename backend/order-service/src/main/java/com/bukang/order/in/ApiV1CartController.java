package com.bukang.order.in;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.global.web.IdempotencyKeyHeader;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.order.app.CartAddResult;
import com.bukang.order.app.CartFacade;
import com.bukang.order.dto.CartCheckoutDto;
import com.bukang.order.dto.CartCheckoutRequestDto;
import com.bukang.order.dto.CartDto;
import com.bukang.order.dto.CartItemAddRequestDto;
import com.bukang.order.dto.CartItemDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@Tag(name = "Cart", description = "장바구니 조회, 참가권 담기, 빼기, 장바구니 결제 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/order/cart")
public class ApiV1CartController {
	private final CartFacade cartFacade;

	// 에러 응답은 GlobalExceptionHandler, OrderExceptionHandler가 RsData(data: null) 형식으로 반환한다
	@GetMapping
	@Operation(summary = "내 장바구니 조회",
		description = "회원별로 서버에 저장한 장바구니를 돌려준다. 장바구니에는 한 종목만 담기므로 항목은 하나다. 담은 적이 없으면 빈 목록이다. "
			+ "항목마다 그 종목으로 저장한 결제 전 신청서(PENDING)의 ID·대회 ID·참가자 이름을 붙인다(장바구니 결제에 보낼 신청서). "
			+ "신청서 없이 담았으면 세 값은 null이다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = {
				@ExampleObject(name = "담은 항목 있음", value = CartApiExamples.FIND_SUCCESS),
				@ExampleObject(name = "빈 장바구니", value = CartApiExamples.FIND_EMPTY)
			})),
		@ApiResponse(responseCode = "400", description = "회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "회원 ID 형식 오류", value = CartApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = CartApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CartApiExamples.LOGIN_REQUIRED)))
	})
	public ResponseEntity<RsData<CartDto>> findCart(
		@LoginMemberId long memberId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "장바구니를 조회했습니다.", cartFacade.findCart(memberId)));
	}

	// 새로 담으면 201, 이미 담긴 종목이면 200으로 그 항목을 돌려준다 (springdoc이 추론하지 못하므로 둘 다 명시한다)
	@PostMapping("/items")
	@Operation(summary = "장바구니에 참가권 담기",
		description = "종목(참가권)을 장바구니에 담는다. 수량은 1로 고정이라 같은 종목을 다시 담으면 기존 항목을 그대로 돌려준다. "
			+ "담기만으로는 자리를 확보하지 않는다. 접수가 마감된 종목은 담을 수 없다. "
			+ "장바구니에는 한 종목만 담는다. 다른 종목이 담겨 있으면 409(ORDER_CART_ONE_COURSE_ONLY)다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "새로 담음",
			content = @Content(examples = @ExampleObject(value = CartApiExamples.ADD_SUCCESS))),
		@ApiResponse(responseCode = "200", description = "이미 담긴 참가권",
			content = @Content(examples = @ExampleObject(value = CartApiExamples.ADD_ALREADY))),
		@ApiResponse(responseCode = "400", description = "입력값 검증 실패, 요청 본문 형식 오류 또는 회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 검증 실패", value = CartApiExamples.INVALID_INPUT),
				@ExampleObject(name = "요청 본문 형식 오류", value = CartApiExamples.INVALID_BODY),
				@ExampleObject(name = "회원 ID 형식 오류", value = CartApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = CartApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CartApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "404", description = "없는 종목",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CartApiExamples.COURSE_NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "접수 마감된 종목, 다른 종목이 이미 담김, 동시에 담는 요청이 겹침",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "접수 마감", value = CartApiExamples.APPLICATION_CLOSED),
				@ExampleObject(name = "다른 종목이 담김", value = CartApiExamples.ONE_COURSE_ONLY),
				@ExampleObject(name = "요청 겹침", value = CartApiExamples.CONFLICT)
			}))
	})
	public ResponseEntity<RsData<CartItemDto>> addItem(
		@LoginMemberId long memberId,
		@Valid @RequestBody CartItemAddRequestDto request
	) {
		CartAddResult result = cartFacade.addItem(request, memberId);
		if (!result.added()) {
			return ResponseEntity.ok()
				.body(RsData.of(HttpStatus.OK, "이미 장바구니에 담긴 참가권입니다.", result.item()));
		}
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(RsData.of(HttpStatus.CREATED, "장바구니에 담았습니다.", result.item()));
	}

	@DeleteMapping("/items/{cartItemId}")
	@Operation(summary = "장바구니에서 빼기", description = "본인 장바구니의 항목을 빼고, 남은 장바구니를 돌려준다. "
		+ "그 종목으로 저장한 결제 전 신청서(PENDING)는 남기며, 같은 대회에 다시 신청하면 새 신청서로 바뀐다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "빼기 성공",
			content = @Content(examples = @ExampleObject(value = CartApiExamples.REMOVE_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "장바구니 항목 ID·회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "장바구니 항목 ID·회원 ID 형식 오류", value = CartApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = CartApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CartApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "다른 회원의 장바구니 항목",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CartApiExamples.NOT_OWNER))),
		@ApiResponse(responseCode = "404", description = "없는 장바구니 항목",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CartApiExamples.ITEM_NOT_FOUND)))
	})
	public ResponseEntity<RsData<CartDto>> removeItem(
		@LoginMemberId long memberId,
		@Parameter(description = "장바구니 항목 ID", example = "1")
		@PathVariable long cartItemId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "장바구니에서 뺐습니다.", cartFacade.removeItem(cartItemId, memberId)));
	}

	// 항목별 결과(주문함·매진·실패)는 200의 data에 담는다. 요청 자체가 잘못되면 자리를 확보하기 전에 4xx로 끝난다
	@PostMapping("/checkout")
	@Operation(summary = "장바구니 결제",
		description = "장바구니에 담은 참가권(한 종목)의 참가 신청으로 [결제하기]를 해 결제 대기 주문(CREATED)을 만든다. "
			+ "장바구니에는 한 종목만 담으므로 참가 신청은 1건만 받는다(2건 이상이면 400). 주문한 종목은 장바구니에서 뺀다. "
			+ "[결제하기]처럼 대기열에 입장 중인지 확인해, 입장 중이 아니면 실패(FAILED)로 돌려준다. "
			+ "매진·실패하면 장바구니에 남는다. 같은 Idempotency-Key로 다시 보내면 이미 만든 주문을 돌려준다(EXISTING). "
			+ "결제는 주문마다 예치금 결제 API로 한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "처리 결과 (매진·실패여도 200)",
			content = @Content(examples = {
				@ExampleObject(name = "주문함", value = CartApiExamples.CHECKOUT_SUCCESS),
				@ExampleObject(name = "주문 0건", value = CartApiExamples.CHECKOUT_NONE)
			})),
		@ApiResponse(responseCode = "400", description = "입력값·헤더 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "참가 신청 없음", value = CartApiExamples.CHECKOUT_EMPTY),
				@ExampleObject(name = "2건 이상", value = CartApiExamples.CHECKOUT_TOO_MANY),
				@ExampleObject(name = "참가 신청 ID 오류", value = CartApiExamples.CHECKOUT_INVALID_ID),
				@ExampleObject(name = "요청 본문 형식 오류", value = CartApiExamples.INVALID_BODY),
				@ExampleObject(name = "Idempotency-Key 없음", value = CartApiExamples.MISSING_IDEMPOTENCY_KEY),
				@ExampleObject(name = "Idempotency-Key 길이 오류", value = CartApiExamples.INVALID_IDEMPOTENCY_KEY),
				@ExampleObject(name = "회원 ID 형식 오류", value = CartApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = CartApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CartApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "다른 회원의 참가 신청이 섞여 있음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CartApiExamples.PARTICIPATION_NOT_OWNER))),
		@ApiResponse(responseCode = "404", description = "없는 참가 신청이 섞여 있음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CartApiExamples.PARTICIPATION_NOT_FOUND)))
	})
	public ResponseEntity<RsData<CartCheckoutDto>> checkout(
		@LoginMemberId long memberId,
		@Parameter(description = "중복 주문 방지 키. 같은 장바구니 결제 재시도에는 같은 값을 보낸다 (예: UUID)",
			example = "6f1c2a7e-3b9d-4f0a-9c55-0d2e8b7a1f43")
		@RequestHeader(IdempotencyKeyHeader.NAME)
		@Size(min = 1, max = IdempotencyKeyHeader.MAX_LENGTH, message = IdempotencyKeyHeader.INVALID_MESSAGE)
		String idempotencyKey,
		@Valid @RequestBody CartCheckoutRequestDto request
	) {
		CartCheckoutDto result = cartFacade.checkout(request, idempotencyKey, memberId);
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, checkoutMessage(result), result));
	}

	// 하나도 주문하지 못했으면 결제를 안내하지 않는다
	private String checkoutMessage(CartCheckoutDto result) {
		if (result.getOrderedCount() == 0) {
			return "주문한 참가권이 없습니다. 항목별 결과를 확인해주세요.";
		}
		return "참가권 %d건 중 %d건의 자리를 확보했습니다. 결제 기한 안에 결제해주세요."
			.formatted(result.getResults().size(), result.getOrderedCount());
	}
}
