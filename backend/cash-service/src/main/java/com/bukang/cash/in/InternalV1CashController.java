package com.bukang.cash.in;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.cash.app.CashFacade;
import com.bukang.cash.dto.payment.CashCancelPaymentRequestDto;
import com.bukang.cash.dto.payment.CashPaymentRequestDto;
import com.bukang.cash.dto.payment.CashPaymentResponseDto;
import com.bukang.cash.dto.payment.CashRefundRequestDto;
import com.bukang.cash.dto.payment.CashRefundResponseDto;
import com.bukang.cash.dto.payout.CashPayoutRequestDto;
import com.bukang.cash.dto.payout.CashPayoutResponseDto;
import com.bukang.cash.dto.recovery.CashClaimVirtualAccountRequestDto;
import com.bukang.cash.dto.recovery.CashClaimVirtualAccountResponseDto;
import com.bukang.cash.dto.wallet.CashBalanceResponseDto;
import com.bukang.cash.dto.withdrawal.CashWithdrawalItemResolveRequestDto;
import com.bukang.cash.dto.withdrawal.CashWithdrawalItemResolveResponseDto;
import com.bukang.common.global.rsdata.RsData;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

/**
 * 서비스끼리 부르는 내부 API. 게이트웨이에 라우팅하지 않는다(/internal/** 경로)
 * 회원이 아니라 서비스가 부르므로 회원 ID 헤더를 받지 않는다 (file-service, order-service와 같은 방식)
 */
@Tag(name = "CashInternal",
	description = "주문 결제, 결제 취소(환원), 환불, 잔액 조회, 정산 지급, 청구용 가상계좌 발급, 환급 카드 몫 정리 API "
		+ "(서비스 간 내부 호출·담당자 전용)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/cash")
public class InternalV1CashController {
	private final CashFacade cashFacade;

	// 에러 응답은 GlobalExceptionHandler, CashExceptionHandler가 RsData(data: null) 형식으로 반환한다
	@PostMapping("/payments/{orderId}")
	@Operation(summary = "주문 결제", description = "주문 ID로 결제 행을 멱등하게 찾거나 만들고, 예치금 잔액에서 결제 금액을 차감한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "결제 성공 또는 잔액 부족",
			content = @Content(examples = {
				@ExampleObject(name = "결제 성공", value = CashInternalApiExamples.PAY_SUCCESS_PAID),
				@ExampleObject(name = "잔액 부족", value = CashInternalApiExamples.PAY_SUCCESS_INSUFFICIENT)
			})),
		@ApiResponse(responseCode = "400", description = "입력값 검증 실패, 요청 본문 형식 오류 또는 주문 ID 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 검증 실패", value = CashInternalApiExamples.INVALID_MEMBER_ID),
				@ExampleObject(name = "요청 본문 형식 오류", value = CashInternalApiExamples.INVALID_BODY),
				@ExampleObject(name = "주문 ID 형식 오류", value = CashInternalApiExamples.INVALID_VALUE_TYPE)
			})),
		@ApiResponse(responseCode = "404", description = "존재하지 않는 지갑",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CashInternalApiExamples.WALLET_NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "이미 취소·환불된 결제 또는 결제한 회원과 요청한 회원이 다름",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "이미 취소·환불됨", value = CashInternalApiExamples.PAYMENT_ALREADY_FINALIZED),
				@ExampleObject(name = "결제 회원 불일치", value = CashInternalApiExamples.PAYMENT_MEMBER_MISMATCH)
			}))
	})
	public ResponseEntity<RsData<CashPaymentResponseDto>> pay(
		@Parameter(description = "주문 ID", example = "100") @PathVariable long orderId,
		@Valid @RequestBody CashPaymentRequestDto request
	) {
		CashPaymentResponseDto result = cashFacade.completeOrderPayment(
			orderId, request.getMemberId(), request.getAmount());
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, result.message(), result));
	}


	@PostMapping("/payments/{orderId}/cancel")
	@Operation(summary = "결제 취소(환원)", description = "결제 완료된 주문이면 예치금을 돌려주고, 결제 전이면 뒤늦게 오는 결제를 "
		+ "거절하도록 선점한다. 이미 취소·환불됐으면 아무것도 바꾸지 않고 성공한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "취소 성공",
			content = @Content(examples = @ExampleObject(value = CashInternalApiExamples.CANCEL_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "입력값 검증 실패, 요청 본문 형식 오류 또는 주문 ID 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 검증 실패", value = CashInternalApiExamples.INVALID_MEMBER_ID),
				@ExampleObject(name = "요청 본문 형식 오류", value = CashInternalApiExamples.INVALID_BODY),
				@ExampleObject(name = "주문 ID 형식 오류", value = CashInternalApiExamples.INVALID_VALUE_TYPE)
			})),
		@ApiResponse(responseCode = "404", description = "존재하지 않는 지갑",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CashInternalApiExamples.WALLET_NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "결제한 회원과 요청한 회원이 다름",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CashInternalApiExamples.PAYMENT_MEMBER_MISMATCH)))
	})
	public ResponseEntity<RsData<Void>> cancelPayment(
		@Parameter(description = "주문 ID", example = "100") @PathVariable long orderId,
		@Valid @RequestBody CashCancelPaymentRequestDto request
	) {
		cashFacade.cancelPayment(orderId, request.getMemberId());
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "결제를 취소했습니다.", null));
	}

	@PostMapping("/payments/{orderId}/refund")
	@Operation(summary = "예치금 환불", description = "결제 완료된 주문만 환불한다. 이미 환원·환불됐으면 그대로 성공하고, "
		+ "결제된 적 없는 주문은 거절한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "환불 성공",
			content = @Content(examples = @ExampleObject(value = CashInternalApiExamples.REFUND_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "입력값 검증 실패, 요청 본문 형식 오류 또는 주문 ID 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 검증 실패", value = CashInternalApiExamples.INVALID_MEMBER_ID),
				@ExampleObject(name = "요청 본문 형식 오류", value = CashInternalApiExamples.INVALID_BODY),
				@ExampleObject(name = "주문 ID 형식 오류", value = CashInternalApiExamples.INVALID_VALUE_TYPE)
			})),
		@ApiResponse(responseCode = "404", description = "존재하지 않는 지갑",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CashInternalApiExamples.WALLET_NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "환불할 결제가 없음, 결제가 완료되지 않음 또는 결제한 회원과 요청한 회원이 다름",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "환불할 결제 없음", value = CashInternalApiExamples.REFUND_NOT_FOUND),
				@ExampleObject(name = "결제 미완료", value = CashInternalApiExamples.REFUND_NOT_COMPLETED),
				@ExampleObject(name = "결제 회원 불일치", value = CashInternalApiExamples.PAYMENT_MEMBER_MISMATCH)
			}))
	})
	public ResponseEntity<RsData<CashRefundResponseDto>> refund(
		@Parameter(description = "주문 ID", example = "100") @PathVariable long orderId,
		@Valid @RequestBody CashRefundRequestDto request
	) {
		CashRefundResponseDto result = cashFacade.refund(orderId, request.getMemberId());
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "환불했습니다.", result));
	}

	// 회원 서비스가 탈퇴 전에 남은 예치금이 있는지 묻는다 (BE-022). 읽기만 하고 지갑을 만들지 않는다
	@GetMapping("/wallets/{memberId}/balance")
	@Operation(summary = "회원 잔액 조회", description = "회원 탈퇴 확인용으로 회원의 예치금 잔액을 돌려준다. "
		+ "지갑이 없는 회원은 남은 예치금이 없으므로 404가 아니라 0원으로 답한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (지갑이 없으면 0원)",
			content = @Content(examples = {
				@ExampleObject(name = "잔액 있음", value = CashInternalApiExamples.BALANCE_SUCCESS),
				@ExampleObject(name = "지갑 없음", value = CashInternalApiExamples.BALANCE_NO_WALLET)
			})),
		@ApiResponse(responseCode = "400", description = "회원 ID 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CashInternalApiExamples.INVALID_VALUE_TYPE)))
	})
	public ResponseEntity<RsData<CashBalanceResponseDto>> getBalance(
		@Parameter(description = "회원 ID (= 지갑 ID)", example = "3") @PathVariable long memberId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "잔액을 조회했습니다.", cashFacade.getBalanceOrZero(memberId)));
	}

	// 정산 서비스가 지급 배치에서 정산 내역마다 부른다. 에러 응답은 GlobalExceptionHandler, CashExceptionHandler가 처리한다
	@PostMapping("/payouts/{payoutId}")
	@Operation(summary = "정산 지급", description = "홀딩 지갑에서 정산 대금을 빼고 은행으로 판매자 계좌에 송금하며, 최종 정산이면 "
		+ "시스템 지갑으로 수수료도 옮긴다. 완료된 정산 ID를 다시 요청하면 그대로 성공하고, 송금이 실패한 정산 ID는 새로 송금한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "지급 성공 또는 이미 지급됨",
			content = @Content(examples = {
				@ExampleObject(name = "지급 성공", value = CashInternalApiExamples.PAYOUT_SUCCESS_PAID),
				@ExampleObject(name = "이미 지급됨", value = CashInternalApiExamples.PAYOUT_ALREADY_PAID)
			})),
		@ApiResponse(responseCode = "400", description = "입력값 검증 실패, 요청 본문 형식 오류 또는 정산 ID 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 검증 실패", value = CashInternalApiExamples.INVALID_SELLER_ID),
				@ExampleObject(name = "요청 본문 형식 오류", value = CashInternalApiExamples.INVALID_BODY),
				@ExampleObject(name = "정산 ID 형식 오류", value = CashInternalApiExamples.INVALID_VALUE_TYPE)
			})),
		@ApiResponse(responseCode = "404", description = "존재하지 않는 판매자 또는 존재하지 않는 홀딩·시스템 지갑",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "존재하지 않는 판매자", value = CashInternalApiExamples.SELLER_NOT_FOUND),
				@ExampleObject(name = "존재하지 않는 지갑", value = CashInternalApiExamples.WALLET_NOT_FOUND)
			})),
		@ApiResponse(responseCode = "409", description = "같은 정산 ID로 이전과 다른 판매자·금액이 요청됨 (data에 저장된 원래 값이 담김)",
			content = @Content(examples = @ExampleObject(value = CashInternalApiExamples.PAYOUT_MISMATCH))),
		@ApiResponse(responseCode = "422", description = "은행이 송금을 거절함. 홀딩 지갑 차감은 되돌렸고, 같은 정산 ID로 다시 요청하면 새로 송금한다",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CashInternalApiExamples.PAYOUT_TRANSFER_FAILED))),
		@ApiResponse(responseCode = "503", description = "송금 결과를 아직 모름. 은행에 다시 보내지 않고 매일 03:30 재확인이 맞춘다",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CashInternalApiExamples.PAYOUT_TRANSFER_UNKNOWN)))
	})
	public ResponseEntity<RsData<CashPayoutResponseDto>> payout(
		@Parameter(description = "정산 ID", example = "500") @PathVariable long payoutId,
		@Valid @RequestBody CashPayoutRequestDto request
	) {
		CashPayoutResponseDto result = cashFacade.payout(payoutId, request.getSellerId(), request.getPayoutStage(),
			request.getSettlementAmount(), request.getFeeAmount());
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, result.message(), result));
	}

	// 정산 서비스가 청구 배치에서 아직 계좌가 없는 청구마다 부른다. 실패하면 정산은 다음 배치에서 같은 청구 ID로 다시 부른다
	@PostMapping("/claims/{claimCode}/virtual-account")
	@Operation(summary = "청구용 가상계좌 발급", description = "판매자가 갚을 돈(청구)을 받을 가상계좌를 PG에서 발급받아 회수 입금으로 "
		+ "저장한다. 같은 청구 ID로 다시 요청하면 새로 발급하지 않고 처음 발급한 계좌를 돌려준다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "발급 성공 또는 이미 발급한 계좌",
			content = @Content(
				examples = @ExampleObject(value = CashInternalApiExamples.CLAIM_VIRTUAL_ACCOUNT_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "금액 검증 실패, 청구 ID 형식 오류 또는 요청 본문 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "금액 없음", value = CashInternalApiExamples.CLAIM_AMOUNT_MISSING),
				@ExampleObject(name = "금액 0 이하", value = CashInternalApiExamples.CLAIM_AMOUNT_NOT_POSITIVE),
				@ExampleObject(name = "청구 ID 형식 오류", value = CashInternalApiExamples.INVALID_CLAIM_CODE),
				@ExampleObject(name = "요청 본문 형식 오류", value = CashInternalApiExamples.INVALID_BODY)
			})),
		@ApiResponse(responseCode = "503",
			description = "PG가 발급·조회를 거절함, PG 연결 실패, 응답에 계좌 정보가 없음 또는 PG에서 찾은 계좌의 금액이 다름",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "발급 거절", value = CashInternalApiExamples.VIRTUAL_ACCOUNT_REJECTED),
				@ExampleObject(name = "조회 거절", value = CashInternalApiExamples.VIRTUAL_ACCOUNT_LOOKUP_REJECTED),
				@ExampleObject(name = "PG 연결 실패", value = CashInternalApiExamples.VIRTUAL_ACCOUNT_PG_UNREACHABLE),
				@ExampleObject(name = "계좌 정보 없음", value = CashInternalApiExamples.VIRTUAL_ACCOUNT_INVALID_RESPONSE),
				@ExampleObject(name = "금액이 다른 계좌", value = CashInternalApiExamples.VIRTUAL_ACCOUNT_AMOUNT_MISMATCH)
			}))
	})
	public ResponseEntity<RsData<CashClaimVirtualAccountResponseDto>> issueClaimVirtualAccount(
		// PG 주문번호로 그대로 쓰므로 Toss 주문번호 규칙에 맞는지 먼저 본다. 어긋나면 Toss가 거절해 503처럼 보이게 된다
		@Parameter(description = "정산의 청구 ID (PG 주문번호로 쓴다, 영문·숫자·-·_ 6~64자)", example = "RC-20261214-0A3F")
		@PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{6,64}", message = "청구 ID는 영문·숫자·-·_로 6~64자여야 합니다.")
		String claimCode,
		@Valid @RequestBody CashClaimVirtualAccountRequestDto request
	) {
		CashClaimVirtualAccountResponseDto result = cashFacade.issueClaimVirtualAccount(claimCode, request.getAmount());
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "가상계좌를 발급했습니다.", result));
	}

	// 담당자가 PG 관리 화면에서 취소 기록을 보고 부른다. 관리자 화면은 없다(정책 5장 2차 1 「수동 확인 필요」)
	@PostMapping("/withdrawal-items/{itemId}/resolve")
	@Operation(summary = "환급 카드 몫 수동 정리", description = "수동 확인 필요인 환급 카드 몫을 담당자의 결론대로 반영한다. "
		+ "카드로 나감(CARD_CANCELED)이면 몫을 완료로 바꾸고 충전 건의 취소한 금액에 더하며, 안 나감(NOT_CANCELED)이면 "
		+ "예치금에 되돌린다. 마지막 몫이 정리되면 환급이 완료되고 회원에게 완료 알림이 간다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "정리 성공",
			content = @Content(examples = {
				@ExampleObject(name = "카드로 나감, 환급 완료",
					value = CashInternalApiExamples.WITHDRAWAL_ITEM_RESOLVED_CARD),
				@ExampleObject(name = "안 나감, 다른 몫이 남음",
					value = CashInternalApiExamples.WITHDRAWAL_ITEM_RESOLVED_NOT_CANCELED)
			})),
		@ApiResponse(responseCode = "400", description = "결과 누락, 요청 본문 형식 오류 또는 몫 ID 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "결과 누락", value = CashInternalApiExamples.WITHDRAWAL_ITEM_RESULT_MISSING),
				@ExampleObject(name = "요청 본문 형식 오류", value = CashInternalApiExamples.INVALID_BODY),
				@ExampleObject(name = "몫 ID 형식 오류", value = CashInternalApiExamples.INVALID_VALUE_TYPE)
			})),
		@ApiResponse(responseCode = "404", description = "존재하지 않는 환급 카드 몫",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CashInternalApiExamples.WITHDRAWAL_ITEM_NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "수동 확인 필요가 아닌 몫 (재확인 중이거나 이미 끝남)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CashInternalApiExamples.WITHDRAWAL_ITEM_NOT_MANUAL_CHECK)))
	})
	public ResponseEntity<RsData<CashWithdrawalItemResolveResponseDto>> resolveWithdrawalItem(
		@Parameter(description = "환급 카드 몫 ID", example = "42") @PathVariable long itemId,
		@Valid @RequestBody CashWithdrawalItemResolveRequestDto request
	) {
		CashWithdrawalItemResolveResponseDto result =
			cashFacade.resolveWithdrawalItem(itemId, request.getResult(), LocalDateTime.now());
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "환급 카드 몫을 정리했습니다.", result));
	}
}
