package com.bukang.cash.in;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.cash.app.CashFacade;
import com.bukang.cash.domain.wallet.LedgerCategory;
import com.bukang.cash.dto.PageDto;
import com.bukang.cash.dto.charge.CashChargeConfirmRequestDto;
import com.bukang.cash.dto.charge.CashChargeResponseDto;
import com.bukang.cash.dto.payment.RefundableChargesResponseDto;
import com.bukang.cash.dto.wallet.WalletBalanceResponseDto;
import com.bukang.cash.dto.wallet.WalletLedgerItemDto;
import com.bukang.cash.dto.withdrawal.CashWithdrawalRequestDto;
import com.bukang.cash.dto.withdrawal.CashWithdrawalResponseDto;
import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.global.web.IdempotencyKeyHeader;
import com.bukang.common.shared.member.web.LoginMemberId;

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

@Tag(name = "Cash", description = "예치금 지갑 잔액 조회 API")
@RestController
@RequestMapping("/api/v1/cash")
@RequiredArgsConstructor
public class ApiV1CashController {
	private static final int MAX_PAGE_SIZE = 100;
	private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

	private final CashFacade cashFacade;

	// 에러 응답은 GlobalExceptionHandler, CashExceptionHandler가 RsData(data: null) 형식으로 반환한다
	// PRO-247: 경로 변수로 받던 회원 ID를 X-Member-Id 헤더로 바꿔, 요청자 본인의 지갑만 조회하게 한다 (IDOR 수정)
	@GetMapping("/wallets/balance")
	@Operation(summary = "지갑 잔액 조회", description = "요청자 본인의 지갑 잔액을 조회한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = CashApiExamples.GET_BALANCE_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "회원 ID 형식 오류 또는 0 이하",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "회원 ID 형식 오류", value = CashApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = CashApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CashApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "404", description = "지갑 없음 (회원 가입 이벤트가 아직 처리되지 않았거나 없는 회원)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CashApiExamples.WALLET_NOT_FOUND)))
	})
	public ResponseEntity<RsData<WalletBalanceResponseDto>> getBalance(
		@LoginMemberId long memberId
	) {
		WalletBalanceResponseDto walletBalanceResponseDto = cashFacade.getBalance(memberId);
		return ResponseEntity.ok(RsData.of(HttpStatus.OK, "잔액 조회 성공", walletBalanceResponseDto));
	}

	// 에러 응답은 GlobalExceptionHandler, CashExceptionHandler가 RsData(data: null) 형식으로 반환한다
	// PRO-258: 프론트 "내역" 화면이 쓰던 목업 데이터를 실제 WalletLedger 조회로 대체한다
	@GetMapping("/wallets/ledgers")
	@Operation(summary = "예치금 내역 조회", description = "요청자 본인 지갑의 내역을 최신순으로 조회한다. "
		+ "category로 탭(CHARGE/PAYMENT/REFUND/WITHDRAWAL/PAYOUT)을 필터링할 수 있고, 생략하면 전체를 돌려준다. "
		+ "충전 줄의 charge에는 환급 화면용 충전 내역 조회와 같은 남은 금액·카드 취소 마지막 날이 있고, 다른 줄은 null이다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (내역이 없으면 빈 목록)",
			content = @Content(examples = @ExampleObject(value = CashApiExamples.LEDGER_LIST_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "페이지·회원 ID·category 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "페이지 크기 초과", value = CashApiExamples.INVALID_PAGE),
				@ExampleObject(name = "값 형식 오류", value = CashApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = CashApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CashApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "404", description = "지갑 없음 (회원 가입 이벤트가 아직 처리되지 않았거나 없는 회원)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CashApiExamples.WALLET_NOT_FOUND)))
	})
	public ResponseEntity<RsData<PageDto<WalletLedgerItemDto>>> getLedgers(
		@LoginMemberId long memberId,
		@Parameter(description = "탭 필터. 생략하면 전체", example = "CHARGE")
		@RequestParam(required = false) LedgerCategory category,
		@Parameter(description = "페이지 번호 (0부터)", example = "0")
		@RequestParam(defaultValue = "0") @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.") int page,
		@Parameter(description = "페이지 크기 (1~100)", example = "20")
		@RequestParam(defaultValue = "20") @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
		@Max(value = MAX_PAGE_SIZE, message = "한 번에 100개까지 조회할 수 있습니다.") int size
	) {
		return ResponseEntity.ok(RsData.of(HttpStatus.OK, "예치금 내역을 조회했습니다.",
			cashFacade.findLedgers(memberId, category, page, size, LocalDateTime.now(KOREA))));
	}

	// 정책 5장 2차 1 「PG 결제취소와 기한」: 기한은 한국 시간으로 판단하므로 서버 시간대와 상관없이 한국 시각을 넘긴다
	@GetMapping("/wallets/refundable-charges")
	@Operation(summary = "환급 화면용 충전 내역 조회", description = "요청자 본인의 완료된 충전을 최근 순으로 조회한다. "
		+ "남은 예치금을 최근 충전부터 채워 충전 건별 남은 금액을 계산하고, 건마다 카드 취소 마지막 날(승인일 + 1년 − 1일, "
		+ "이날 24:00까지 한국 시간)과 기한 지남 여부를 붙인다. earliestLastCancelableDate는 남은 금액이 있고 "
		+ "기한이 지나지 않은 충전 중 가장 빠른 마지막 날이며, 없으면 null이다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (충전이 없으면 빈 목록)",
			content = @Content(examples = @ExampleObject(value = CashApiExamples.REFUNDABLE_CHARGES_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "회원 ID 형식 오류 또는 0 이하",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "회원 ID 형식 오류", value = CashApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = CashApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CashApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "404", description = "지갑 없음 (회원 가입 이벤트가 아직 처리되지 않았거나 없는 회원)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CashApiExamples.WALLET_NOT_FOUND)))
	})
	public ResponseEntity<RsData<RefundableChargesResponseDto>> getRefundableCharges(
		@LoginMemberId long memberId
	) {
		return ResponseEntity.ok(RsData.of(HttpStatus.OK, "환급 화면용 충전 내역을 조회했습니다.",
			cashFacade.findRefundableCharges(memberId, LocalDateTime.now(KOREA))));
	}

	// 에러 응답은 GlobalExceptionHandler, CashExceptionHandler가 RsData(data: null) 형식으로 반환한다
	// PG 승인이 거절돼도 예외가 아니라 200으로 응답한다 (data.outcome이 FAILED)
	@PostMapping("/charges/confirm")
	@Operation(summary = "충전 승인 확정", description = "프론트가 Toss 결제위젯 콜백으로 받은 paymentKey·orderId·amount로 "
		+ "PG 승인을 확정하고, 성공하면 예치금 지갑에 반영한다. orderId를 멱등키로 써서 같은 요청이 여러 번 와도 한 번만 처리한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "충전 확정 처리됨 (성공 또는 PG 승인 거절)",
			content = @Content(examples = {
				@ExampleObject(name = "충전 성공", value = CashApiExamples.CHARGE_CONFIRM_SUCCESS),
				@ExampleObject(name = "PG 승인 거절", value = CashApiExamples.CHARGE_CONFIRM_FAILED)
			})),
		@ApiResponse(responseCode = "400",
			description = "최소 충전 금액 미만, 입력값 검증 실패, 요청 본문 형식 오류, 회원 ID 형식 오류 또는 0 이하",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "최소 충전 금액 미만", value = CashApiExamples.CHARGE_AMOUNT_TOO_SMALL),
				@ExampleObject(name = "입력값 검증 실패", value = CashApiExamples.CHARGE_CONFIRM_INVALID_INPUT),
				@ExampleObject(name = "요청 본문 형식 오류", value = CashApiExamples.INVALID_BODY),
				@ExampleObject(name = "회원 ID 형식 오류", value = CashApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = CashApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CashApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "404", description = "지갑 없음 (회원 가입 이벤트가 아직 처리되지 않았거나 없는 회원)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CashApiExamples.WALLET_NOT_FOUND)))
	})
	public ResponseEntity<RsData<CashChargeResponseDto>> confirmWalletCharge(
		@LoginMemberId long memberId,
		@Valid @RequestBody CashChargeConfirmRequestDto request
	) {
		CashChargeResponseDto response = cashFacade.confirmWalletCharge(
			memberId, request.getPaymentKey(), request.getOrderId(), request.getAmount());
		return ResponseEntity.ok(RsData.of(HttpStatus.OK, response.message(), response));
	}

	// 에러 응답은 GlobalExceptionHandler, CashExceptionHandler가 RsData(data: null) 형식으로 반환한다
	@PostMapping("/withdrawals")
	@Operation(summary = "예치금 전액 환급 신청", description = "회원이 남은 예치금 전액을 환급 신청한다. "
		+ "PG 결제취소 기한 안인 충전 건은 결제한 카드로 결제취소하고, 나머지(기한이 지난 몫, 충전이 아닌 정산 대금)는 "
		+ "계좌로 보낸다. 은행 정보는 계좌로 보낼 몫이 있을 때만 필요하다. 결제취소 결과를 모르는 몫은 처리 중으로 두고 "
		+ "재확인이 맞추며, 거절된 몫은 예치금에 되돌린다. Idempotency-Key 헤더로 같은 요청은 한 번만 처리하고, "
		+ "같은 키로 다시 오면 지금 처리 상태를 돌려준다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "환급 신청 완료. 금액은 카드 결제취소 요청·계좌 송금·처리 중·"
			+ "예치금에 되돌림으로 나뉜다. accountRequired가 true면 카드로 돌려줄 수 없는 몫이 계좌 정보가 없어 되돌아갔다",
			content = @Content(examples = {
				@ExampleObject(name = "카드와 계좌로 나눠 환급", value = CashApiExamples.WITHDRAWAL_SUCCESS),
				@ExampleObject(name = "일부 처리 중·되돌림", value = CashApiExamples.WITHDRAWAL_PARTIAL),
				@ExampleObject(name = "계좌 정보가 없어 되돌림",
					value = CashApiExamples.WITHDRAWAL_ACCOUNT_NEEDED_AFTER_REJECT)
			})),
		@ApiResponse(responseCode = "400",
			description = "환급할 잔액 없음, 계좌로 보낼 몫이 있는데 계좌 정보 없음, 입력값 검증 실패, 요청 본문 형식 오류, "
				+ "회원 ID 형식 오류·0 이하 또는 Idempotency-Key 헤더 없음·길이 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "환급할 잔액 없음", value = CashApiExamples.WALLET_BALANCE_EMPTY),
				@ExampleObject(name = "계좌 정보 필요", value = CashApiExamples.WITHDRAWAL_ACCOUNT_REQUIRED),
				@ExampleObject(name = "입력값 검증 실패", value = CashApiExamples.WITHDRAWAL_INVALID_INPUT),
				@ExampleObject(name = "요청 본문 형식 오류", value = CashApiExamples.INVALID_BODY),
				@ExampleObject(name = "회원 ID 형식 오류", value = CashApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = CashApiExamples.INVALID_MEMBER_ID),
				@ExampleObject(name = "Idempotency-Key 헤더 없음", value = CashApiExamples.MISSING_IDEMPOTENCY_KEY),
				@ExampleObject(name = "Idempotency-Key 길이 오류", value = CashApiExamples.INVALID_IDEMPOTENCY_KEY)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CashApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "404", description = "지갑 없음 (회원 가입 이벤트가 아직 처리되지 않았거나 없는 회원)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CashApiExamples.WALLET_NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "처리 중인 환급 있음 또는 다른 회원이 환급 신청에 쓴 Idempotency-Key",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "처리 중인 환급 있음", value = CashApiExamples.WITHDRAWAL_IN_PROGRESS),
				@ExampleObject(name = "다른 회원의 Idempotency-Key", value = CashApiExamples.WITHDRAWAL_MEMBER_MISMATCH)
			}))
	})
	public ResponseEntity<RsData<CashWithdrawalResponseDto>> requestWithdrawal(
		@LoginMemberId long memberId,
		@Parameter(description = "중복 환급 방지 키. 같은 [환급 신청] 재시도에는 같은 값을 보낸다 (예: UUID)",
			example = "6f1c2a7e-3b9d-4f0a-9c55-0d2e8b7a1f43")
		@RequestHeader(IdempotencyKeyHeader.NAME)
		@Size(min = 1, max = IdempotencyKeyHeader.MAX_LENGTH, message = IdempotencyKeyHeader.INVALID_MESSAGE)
		String idempotencyKey,
		@Valid @RequestBody CashWithdrawalRequestDto request
	) {
		CashWithdrawalResponseDto response = cashFacade.requestWithdrawal(
			memberId, request.getBankCode(), request.getAccountNumber(), request.getAccountHolder(), idempotencyKey,
			LocalDateTime.now(KOREA));
		return ResponseEntity.ok(RsData.of(HttpStatus.OK, response.message(), response));
	}
}
