package com.bukang.cash.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.cash.config.CashWalletProperties;
import com.bukang.cash.domain.charge.WalletCharge;
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.payment.PaymentStatus;
import com.bukang.cash.domain.seller.CashSeller;
import com.bukang.cash.domain.wallet.LedgerRefType;
import com.bukang.cash.domain.wallet.LedgerType;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.domain.withdrawal.WalletWithdrawal;
import com.bukang.cash.domain.withdrawal.WalletWithdrawalItem;
import com.bukang.cash.domain.withdrawal.WithdrawalItemStatus;
import com.bukang.cash.out.bank.BankTransferClient;
import com.bukang.cash.out.bank.BankTransferResult;
import com.bukang.cash.out.charge.WalletChargeRepository;
import com.bukang.cash.out.member.CashMemberRepository;
import com.bukang.cash.out.payment.PaymentRepository;
import com.bukang.cash.out.payout.PayoutTransferRepository;
import com.bukang.cash.out.recovery.RecoveryDepositRepository;
import com.bukang.cash.out.seller.CashSellerRepository;
import com.bukang.cash.out.wallet.WalletLedgerRepository;
import com.bukang.cash.out.wallet.WalletRepository;
import com.bukang.cash.out.withdrawal.WalletWithdrawalItemRepository;
import com.bukang.cash.out.withdrawal.WalletWithdrawalRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalV1CashControllerTest {
	private static final long CUSTOMER_ID = 3;
	private static final long ORDER_ID = 100;

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private CashWalletProperties cashWalletProperties;
	@Autowired
	private CashMemberRepository cashMemberRepository;
	@Autowired
	private WalletRepository walletRepository;
	@Autowired
	private WalletLedgerRepository walletLedgerRepository;
	@Autowired
	private PaymentRepository paymentRepository;
	@Autowired
	private PayoutTransferRepository payoutTransferRepository;
	@Autowired
	private CashSellerRepository cashSellerRepository;
	@Autowired
	private RecoveryDepositRepository recoveryDepositRepository;
	@Autowired
	private WalletChargeRepository walletChargeRepository;
	@Autowired
	private WalletWithdrawalRepository walletWithdrawalRepository;
	@Autowired
	private WalletWithdrawalItemRepository walletWithdrawalItemRepository;
	@Autowired
	private TransactionTemplate transactionTemplate;
	@MockitoBean
	private BankTransferClient bankTransferClient;

	@BeforeEach
	void setUp() {
		given(bankTransferClient.transfer(anyString(), anyLong(), anyLong())).willReturn(BankTransferResult.COMPLETED);
		transactionTemplate.executeWithoutResult(status -> {
			createWallet(cashWalletProperties.holdingMemberId(), "홀딩");
			createWallet(cashWalletProperties.systemMemberId(), "시스템");
			Wallet customerWallet = createWallet(CUSTOMER_ID, "러너3");
			walletLedgerRepository.save(
				customerWallet.credit(10_000, LedgerType.충전__PG결제_토스페이먼츠, LedgerRefType.WalletCharge, 1));
		});
	}

	@AfterEach
	void tearDown() {
		walletWithdrawalItemRepository.deleteAll();
		walletWithdrawalRepository.deleteAll();
		walletChargeRepository.deleteAll();
		paymentRepository.deleteAll();
		payoutTransferRepository.deleteAll();
		walletLedgerRepository.deleteAll();
		walletRepository.deleteAll();
		cashSellerRepository.deleteAll();
		cashMemberRepository.deleteAll();
		recoveryDepositRepository.deleteAll();
	}

	@Test
	@DisplayName("정산이 청구 ID와 금액으로 가상계좌를 요청하면 발급한 은행 이름과 계좌번호를 돌려준다 (가짜 PG)")
	void issueClaimVirtualAccount() throws Exception {
		mockMvc.perform(post("/internal/v1/cash/claims/{claimCode}/virtual-account", "RC-20261214-0A3F")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\": 400000}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("가상계좌를 발급했습니다."))
			.andExpect(jsonPath("$.data.bankName").value("국민은행"))
			.andExpect(jsonPath("$.data.accountNumber").value("X-fake-RC-20261214-0A3F"));

		assertThat(recoveryDepositRepository.findByClaimCode("RC-20261214-0A3F").orElseThrow().getAmount())
			.isEqualTo(400_000);
	}

	@Test
	@DisplayName("가상계좌 요청에 금액이 없거나 0 이하면 400으로 거절하고 발급하지 않는다")
	void issueClaimVirtualAccountRejectsInvalidAmount() throws Exception {
		issueClaimVirtualAccount("RC-20261214-0A3F", "{}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("금액을 작성해주세요."));
		issueClaimVirtualAccount("RC-20261214-0A3F", "{\"amount\": 0}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("금액은 0보다 커야 합니다."));

		assertThat(recoveryDepositRepository.count()).isZero();
	}

	@Test
	@DisplayName("청구 ID가 PG 주문번호 규칙(영문·숫자·-·_ 6~64자)에 맞지 않으면 400으로 거절하고 발급하지 않는다")
	void issueClaimVirtualAccountRejectsInvalidClaimCode() throws Exception {
		issueClaimVirtualAccount("RC 0A3F", "{\"amount\": 400000}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value("청구 ID는 영문·숫자·-·_로 6~64자여야 합니다."));
		issueClaimVirtualAccount("R".repeat(65), "{\"amount\": 400000}")
			.andExpect(status().isBadRequest());

		assertThat(recoveryDepositRepository.count()).isZero();
	}

	@Test
	@DisplayName("잔액이 충분하면 결제가 되고 고객 지갑은 줄고 홀딩 지갑은 늘어난다")
	void payWithSufficientBalance() throws Exception {
		pay(ORDER_ID, CUSTOMER_ID, 3_000)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("결제했습니다."))
			.andExpect(jsonPath("$.data.outcome").value("PAID"))
			.andExpect(jsonPath("$.data.shortage").value(0))
			.andExpect(jsonPath("$.data.message").doesNotExist());

		assertBalance(CUSTOMER_ID, 7_000);
		assertBalance(cashWalletProperties.holdingMemberId(), 3_000);
	}

	@Test
	@DisplayName("잔액이 부족하면 차감 없이 부족한 금액을 돌려준다")
	void payWithInsufficientBalance() throws Exception {
		pay(ORDER_ID, CUSTOMER_ID, 15_000)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("예치금이 부족합니다."))
			.andExpect(jsonPath("$.data.outcome").value("INSUFFICIENT_BALANCE"))
			.andExpect(jsonPath("$.data.shortage").value(5_000));

		assertBalance(CUSTOMER_ID, 10_000);
	}

	@Test
	@DisplayName("같은 주문 ID로 다시 결제 요청해도 중복 차감 없이 PAID만 돌아온다")
	void payIsIdempotentByOrderId() throws Exception {
		pay(ORDER_ID, CUSTOMER_ID, 3_000).andExpect(status().isOk());

		pay(ORDER_ID, CUSTOMER_ID, 3_000)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.outcome").value("PAID"));

		assertBalance(CUSTOMER_ID, 7_000);
	}

	@Test
	@DisplayName("존재하지 않는 회원의 지갑으로 결제하면 404다")
	void payWithoutWallet() throws Exception {
		pay(ORDER_ID, 999, 1_000)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("존재하지 않는 지갑입니다."));
	}

	@Test
	@DisplayName("회원 ID나 금액이 없으면 400이다")
	void invalidRequest() throws Exception {
		mockMvc.perform(post("/internal/v1/cash/payments/" + ORDER_ID)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest());
	}


	@Test
	@DisplayName("결제 완료된 주문을 취소(주문 서비스 요청)하면 고객 잔액이 돌아오고 보관 잔액은 빠진다")
	void cancelCompletedPayment() throws Exception {
		pay(ORDER_ID, CUSTOMER_ID, 3_000).andExpect(status().isOk());

		cancelPayment(ORDER_ID, CUSTOMER_ID).andExpect(status().isOk());

		assertBalance(CUSTOMER_ID, 10_000);
		assertBalance(cashWalletProperties.holdingMemberId(), 0);
		assertThat(countLedgersForOrder(ORDER_ID)).as("결제 2줄 + 취소 반대 2줄").isEqualTo(4);
		assertThat(paymentRepository.findByOrderId(ORDER_ID).orElseThrow().getStatus())
			.isEqualTo(PaymentStatus.CANCELED);
	}

	@Test
	@DisplayName("같은 주문을 두 번 취소해도 잔액은 한 번만 바뀐다")
	void cancelPaymentIsIdempotent() throws Exception {
		pay(ORDER_ID, CUSTOMER_ID, 3_000).andExpect(status().isOk());

		cancelPayment(ORDER_ID, CUSTOMER_ID).andExpect(status().isOk());
		cancelPayment(ORDER_ID, CUSTOMER_ID).andExpect(status().isOk());

		assertBalance(CUSTOMER_ID, 10_000);
	}

	@Test
	@DisplayName("결제 전 주문을 취소로 선점하면, 뒤늦게 온 결제 요청은 거절된다")
	void cancelBeforePaymentRejectsLatePay() throws Exception {
		// 첫 번째 pay 요청이 네트워크 지연 등으로 늦게 도착하는 사이, 주문 서비스가 먼저 이 주문을 포기하고
		// cancelPayment를 보낼 수 있다. 이때 cash에는 아직 이 주문의 결제 기록이 없다.
		// 여기서 아무것도 안 하고 끝내면, 뒤늦게 도착한 pay가 정상 결제로 처리돼 버려
		// 이미 포기된 주문인데 고객 돈만 빠지는 상태가 된다.
		// 그래서 결제 기록이 없어도 CANCELED로 미리 선점해 두고, 나중에 온 pay를 거절한다.
		cancelPayment(ORDER_ID, CUSTOMER_ID).andExpect(status().isOk());

		pay(ORDER_ID, CUSTOMER_ID, 3_000)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("이미 취소·환불된 결제입니다."));

		assertBalance(CUSTOMER_ID, 10_000);
	}

	@Test
	@DisplayName("결제 완료된 주문을 환불하면 고객 잔액이 돌아오고, 응답에 환불 금액이 담긴다")
	void refundCompletedPayment() throws Exception {
		pay(ORDER_ID, CUSTOMER_ID, 3_000).andExpect(status().isOk());

		refund(ORDER_ID, CUSTOMER_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.amount").value(3_000));

		assertBalance(CUSTOMER_ID, 10_000);
		assertBalance(cashWalletProperties.holdingMemberId(), 0);
		assertThat(countLedgersForOrder(ORDER_ID)).as("결제 2줄 + 환불 반대 2줄").isEqualTo(4);
		assertThat(paymentRepository.findByOrderId(ORDER_ID).orElseThrow().getStatus())
			.isEqualTo(PaymentStatus.REFUNDED);
	}

	@Test
	@DisplayName("같은 주문을 두 번 환불해도 잔액은 한 번만 바뀐다")
	void refundIsIdempotent() throws Exception {
		pay(ORDER_ID, CUSTOMER_ID, 3_000).andExpect(status().isOk());

		refund(ORDER_ID, CUSTOMER_ID).andExpect(status().isOk());
		refund(ORDER_ID, CUSTOMER_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.amount").value(3_000));

		assertBalance(CUSTOMER_ID, 10_000);
	}

	@Test
	@DisplayName("결제가 끝난 적 없는 주문을 환불하면 거절된다")
	void refundRejectsUnpaidOrder() throws Exception {
		refund(ORDER_ID, CUSTOMER_ID)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("환불할 결제가 없습니다."));

		assertBalance(CUSTOMER_ID, 10_000);
	}

	@Test
	@DisplayName("최종 정산 지급은 대금을 은행으로 보내고 수수료를 시스템 지갑으로 옮긴다")
	void payoutFinalStageTransfersSettlementAndFee() throws Exception {
		long sellerApplicationId = 50;
		long sellerMemberId = 10;
		createSellerWithMapping(sellerApplicationId, sellerMemberId, "판매자");

		payout(900, sellerApplicationId, "FINAL", 90_000, 5_500)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("정산금을 지급했습니다."))
			.andExpect(jsonPath("$.data.outcome").value("PAID"))
			.andExpect(jsonPath("$.data.settlementAmount").value(90_000))
			.andExpect(jsonPath("$.data.feeAmount").value(5_500));

		assertBalance(sellerMemberId, 0);
		assertBalance(cashWalletProperties.holdingMemberId(), -95_500);
		assertBalance(cashWalletProperties.systemMemberId(), 5_500);
	}

	@Test
	@DisplayName("1차 정산 지급은 수수료 없이 대금만 옮긴다")
	void payoutFirstStageTransfersSettlementOnly() throws Exception {
		long sellerApplicationId = 51;
		long sellerMemberId = 11;
		createSellerWithMapping(sellerApplicationId, sellerMemberId, "판매자2");

		payout(901, sellerApplicationId, "FIRST", 40_000, 0)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.outcome").value("PAID"))
			.andExpect(jsonPath("$.data.feeAmount").value(0));

		assertBalance(sellerMemberId, 0);
		assertBalance(cashWalletProperties.holdingMemberId(), -40_000);
		assertBalance(cashWalletProperties.systemMemberId(), 0);
	}

	@Test
	@DisplayName("같은 정산 ID로 다시 지급 요청해도 중복 이동 없이 ALREADY_PAID만 돌아온다")
	void payoutIsIdempotentByPayoutId() throws Exception {
		long sellerApplicationId = 52;
		long sellerMemberId = 12;
		createSellerWithMapping(sellerApplicationId, sellerMemberId, "판매자3");

		payout(902, sellerApplicationId, "FINAL", 90_000, 5_500).andExpect(status().isOk());

		payout(902, sellerApplicationId, "FINAL", 90_000, 5_500)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.outcome").value("ALREADY_PAID"));

		assertBalance(cashWalletProperties.holdingMemberId(), -95_500);
		assertBalance(cashWalletProperties.systemMemberId(), 5_500);
	}

	@Test
	@DisplayName("같은 정산 ID로 다른 금액을 다시 요청하면 지갑을 건드리지 않고 저장된 값으로 409를 돌려준다")
	void payoutWithDifferentAmountRejectsAndKeepsStoredValue() throws Exception {
		long sellerApplicationId = 53;
		long sellerMemberId = 14;
		createSellerWithMapping(sellerApplicationId, sellerMemberId, "판매자5");
		payout(906, sellerApplicationId, "FINAL", 90_000, 5_500).andExpect(status().isOk());

		payout(906, sellerApplicationId, "FINAL", 95_000, 5_500)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.data.outcome").value("ALREADY_PAID"))
			.andExpect(jsonPath("$.data.settlementAmount").value(90_000))
			.andExpect(jsonPath("$.data.feeAmount").value(5_500));

		assertBalance(cashWalletProperties.holdingMemberId(), -95_500);
	}

	@ParameterizedTest(name = "은행이 {0}로 답하면 {1} {2}")
	@CsvSource({
		"FAILED, 422, PAYOUT_TRANSFER_FAILED",
		"UNKNOWN, 503, PAYOUT_TRANSFER_UNKNOWN"
	})
	@DisplayName("정산금 송금이 실패하면 422, 결과를 모르면 503으로 정산에 답한다")
	void payoutBankFailedOrUnknown(BankTransferResult bankResult, int httpStatus, String code) throws Exception {
		long sellerApplicationId = 54;
		createSellerWithMapping(sellerApplicationId, 15, "판매자6");
		given(bankTransferClient.transfer(anyString(), anyLong(), anyLong())).willReturn(bankResult);

		payout(907, sellerApplicationId, "FIRST", 40_000, 0)
			.andExpect(status().is(httpStatus))
			.andExpect(jsonPath("$.code").value(code));
	}

	@Test
	@DisplayName("존재하지 않는 판매자(신청서 ID)로 정산 지급을 요청하면 404다")
	void payoutWithoutSellerMapping() throws Exception {
		payout(903, 999, "FINAL", 90_000, 5_500)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("존재하지 않는 판매자입니다. sellerId=999"));
	}

	@Test
	@DisplayName("정산 지급 요청에 필수 값이 없으면 400이다")
	void payoutInvalidRequest() throws Exception {
		mockMvc.perform(post("/internal/v1/cash/payouts/904")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("최종 정산에서 대금이 0원이면 수수료만 옮기고, 판매자 매핑이 없어도 성공한다")
	void payoutWithZeroSettlementTransfersFeeOnly() throws Exception {
		payout(905, 13, "FINAL", 0, 5_500)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.outcome").value("PAID"))
			.andExpect(jsonPath("$.data.settlementAmount").value(0))
			.andExpect(jsonPath("$.data.feeAmount").value(5_500));

		assertBalance(cashWalletProperties.systemMemberId(), 5_500);
	}

	@Test
	@DisplayName("회원 잔액 조회는 지갑 잔액을 주고, 지갑이 없는 회원은 404가 아니라 0원을 준다")
	void balanceForWithdrawal() throws Exception {
		mockMvc.perform(get("/internal/v1/cash/wallets/{memberId}/balance", CUSTOMER_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("잔액을 조회했습니다."))
			.andExpect(jsonPath("$.data.balance").value(10_000));
		mockMvc.perform(get("/internal/v1/cash/wallets/{memberId}/balance", 999))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.balance").value(0));
	}

	@Test
	@DisplayName("경로의 회원 ID가 숫자가 아니면 400 INVALID_REQUEST로 응답한다")
	void balanceWithNonNumericMemberId() throws Exception {
		mockMvc.perform(get("/internal/v1/cash/wallets/abc/balance"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value("요청 값의 형식이 올바르지 않습니다."));
	}

	@Test
	@DisplayName("수동 확인 필요인 환급 카드 몫을 안 나감으로 정리하면 예치금에 되돌리고, 마지막 몫인데 돌려준 몫이 없어 환급이 실패로 끝났다고 돌려준다")
	void resolveWithdrawalItem() throws Exception {
		long itemId = createWithdrawalItem(WithdrawalItemStatus.MANUAL_CHECK);

		resolveWithdrawalItem(itemId, "{\"result\": \"NOT_CANCELED\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("환급 카드 몫을 정리했습니다."))
			.andExpect(jsonPath("$.data.itemId").value(itemId))
			.andExpect(jsonPath("$.data.itemStatus").value("REVERTED"))
			.andExpect(jsonPath("$.data.withdrawalStatus").value("FAILED"));

		assertBalance(CUSTOMER_ID, 15_000);
	}

	@Test
	@DisplayName("환급 카드 몫 정리는 결과가 없으면 400, 없는 몫이면 404, 수동 확인 필요가 아닌 몫이면 409로 거절한다")
	void resolveWithdrawalItemRejects() throws Exception {
		long itemId = createWithdrawalItem(WithdrawalItemStatus.UNKNOWN);

		resolveWithdrawalItem(itemId, "{}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value("정리 결과를 작성해주세요."));
		resolveWithdrawalItem(itemId + 1, "{\"result\": \"CARD_CANCELED\"}")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("WITHDRAWAL_ITEM_NOT_FOUND"));
		resolveWithdrawalItem(itemId, "{\"result\": \"CARD_CANCELED\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("WITHDRAWAL_ITEM_NOT_MANUAL_CHECK"));

		assertBalance(CUSTOMER_ID, 10_000);
	}

	// 잔액 10,000원인 고객 지갑에 5,000원 카드 몫이 있는 처리 중 환급을 만든다 (신청 때의 차감 원장 줄은 생략)
	private long createWithdrawalItem(WithdrawalItemStatus status) {
		return transactionTemplate.execute(tx -> {
			Wallet wallet = walletRepository.findById(CUSTOMER_ID).orElseThrow();
			WalletCharge charge = new WalletCharge(wallet, 5_000, "TOSS", "charge_1");
			LocalDateTime approvedAt = LocalDateTime.of(2026, 10, 10, 9, 0);
			charge.markCompleted("toss_key_charge_1", approvedAt, approvedAt);
			walletChargeRepository.save(charge);
			WalletWithdrawal withdrawal =
				walletWithdrawalRepository.save(new WalletWithdrawal(wallet, 5_000, null, null, null, "withdrawal_1"));
			withdrawal.markInProgress();
			return walletWithdrawalItemRepository.save(
				new WalletWithdrawalItem(withdrawal, charge, 5_000, status, "withdrawal_1:" + charge.getId())).getId();
		});
	}

	private ResultActions resolveWithdrawalItem(long itemId, String body) throws Exception {
		return mockMvc.perform(post("/internal/v1/cash/withdrawal-items/{itemId}/resolve", itemId)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions issueClaimVirtualAccount(String claimCode, String body) throws Exception {
		return mockMvc.perform(post("/internal/v1/cash/claims/{claimCode}/virtual-account", claimCode)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions pay(long orderId, long memberId, long amount) throws Exception {
		return mockMvc.perform(post("/internal/v1/cash/payments/" + orderId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"memberId\": %d, \"amount\": %d}".formatted(memberId, amount)));
	}


	private ResultActions cancelPayment(long orderId, long memberId) throws Exception {
		return mockMvc.perform(post("/internal/v1/cash/payments/" + orderId + "/cancel")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"memberId\": %d}".formatted(memberId)));
	}

	private ResultActions refund(long orderId, long memberId) throws Exception {
		return mockMvc.perform(post("/internal/v1/cash/payments/" + orderId + "/refund")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"memberId\": %d}".formatted(memberId)));
	}

	private ResultActions payout(long payoutId, long sellerId, String payoutStage, long settlementAmount,
		long feeAmount) throws Exception {
		return mockMvc.perform(post("/internal/v1/cash/payouts/" + payoutId)
			.contentType(MediaType.APPLICATION_JSON)
			.content(("{\"sellerId\": %d, \"payoutStage\": \"%s\", \"settlementAmount\": %d, \"feeAmount\": %d}")
				.formatted(sellerId, payoutStage, settlementAmount, feeAmount)));
	}


	private void assertBalance(long holderId, int expected) {
		assertThat(walletRepository.findById(holderId).orElseThrow().getBalance()).isEqualTo(expected);
	}


	private long countLedgersForOrder(long orderId) {
		return walletLedgerRepository.findAll().stream()
			.filter(ledger -> ledger.getRefTypeCode() == LedgerRefType.Order && ledger.getRefId() == orderId)
			.count();
	}

	private Wallet createWallet(long memberId, String nickname) {
		LocalDateTime now = LocalDateTime.of(2026, 10, 12, 10, 0);
		CashMember member = cashMemberRepository.save(new CashMember(memberId, now, now, nickname));
		return walletRepository.save(new Wallet(member));
	}

	// 판매자 신청서 ID(sellerApplicationId) → 회원(memberId) 매핑까지 함께 만든다
	private void createSellerWithMapping(long sellerApplicationId, long memberId, String nickname) {
		transactionTemplate.executeWithoutResult(status -> {
			LocalDateTime now = LocalDateTime.of(2026, 10, 12, 10, 0);
			CashMember member = cashMemberRepository.save(new CashMember(memberId, now, now, nickname));
			walletRepository.save(new Wallet(member));
			cashSellerRepository.save(new CashSeller(sellerApplicationId, member));
		});
	}
}
