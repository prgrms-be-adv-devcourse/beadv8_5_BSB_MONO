package com.bukang.cash.app.charge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.cash.app.CashFacade;
import com.bukang.cash.domain.charge.ChargeStatus;
import com.bukang.cash.domain.charge.WalletCharge;
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.out.charge.WalletChargeRepository;
import com.bukang.cash.out.member.CashMemberRepository;
import com.bukang.cash.out.pg.PaymentApprovalResult;
import com.bukang.cash.out.pg.PaymentClient;
import com.bukang.cash.out.pg.PaymentLookupResult;
import com.bukang.cash.out.wallet.WalletLedgerRepository;
import com.bukang.cash.out.wallet.WalletRepository;

// 신규 계정 고액 충전 검토 대상 표시 (정책 4장 2차 2-1 「신규 회원 고액 충전 표시」)
@SpringBootTest
@ActiveProfiles("test")
class NewAccountChargeReviewTest {
	private static final long CUSTOMER_ID = 11;

	@Autowired
	private CashFacade cashFacade;
	@Autowired
	private CashChargeReconcileUseCase cashChargeReconcileUseCase;
	@Autowired
	private CashMemberRepository cashMemberRepository;
	@Autowired
	private WalletRepository walletRepository;
	@Autowired
	private WalletChargeRepository walletChargeRepository;
	@Autowired
	private WalletLedgerRepository walletLedgerRepository;
	@Autowired
	private TransactionTemplate transactionTemplate;

	@MockitoBean
	private PaymentClient paymentClient;

	@AfterEach
	void tearDown() {
		walletChargeRepository.deleteAll();
		walletLedgerRepository.deleteAll();
		walletRepository.deleteAll();
		cashMemberRepository.deleteAll();
	}

	@Test
	@DisplayName("가입 24시간 안에 누적 충전이 50만 원을 처음 넘기면 그 충전에 검토 대상 표시가 남는다")
	void flagsChargeThatFirstCrossesThreshold() {
		createRecentlyJoinedCustomerWallet();
		given(paymentClient.approve(anyString(), anyString(), anyLong()))
			.willReturn(new PaymentApprovalResult(300_000, "toss_key_1", null))
			.willReturn(new PaymentApprovalResult(300_000, "toss_key_2", null));

		cashFacade.confirmWalletCharge(CUSTOMER_ID, "pay_1", "order_1", 300_000);
		cashFacade.confirmWalletCharge(CUSTOMER_ID, "pay_2", "order_2", 300_000);

		assertThat(walletChargeRepository.findByIdempotencyKey("order_1").orElseThrow().isReviewFlagged()).isFalse();
		assertThat(walletChargeRepository.findByIdempotencyKey("order_2").orElseThrow().isReviewFlagged()).isTrue();
	}

	@Test
	@DisplayName("가입 24시간 안이어도 누적 충전이 50만 원을 넘지 않으면 아무 충전에도 표시가 안 남는다")
	void doesNotFlagWhenCumulativeStaysUnderThreshold() {
		createRecentlyJoinedCustomerWallet();
		given(paymentClient.approve(anyString(), anyString(), anyLong()))
			.willReturn(new PaymentApprovalResult(200_000, "toss_key_1", null))
			.willReturn(new PaymentApprovalResult(200_000, "toss_key_2", null));

		cashFacade.confirmWalletCharge(CUSTOMER_ID, "pay_1", "order_1", 200_000);
		cashFacade.confirmWalletCharge(CUSTOMER_ID, "pay_2", "order_2", 200_000);

		assertThat(walletChargeRepository.findByIdempotencyKey("order_1").orElseThrow().isReviewFlagged()).isFalse();
		assertThat(walletChargeRepository.findByIdempotencyKey("order_2").orElseThrow().isReviewFlagged()).isFalse();
	}

	@Test
	@DisplayName("한 번 검토 대상으로 표시된 뒤에는 추가로 충전해도 그다음 충전에는 다시 표시되지 않는다")
	void doesNotReFlagAfterAlreadyFlaggedOnce() {
		createRecentlyJoinedCustomerWallet();
		given(paymentClient.approve(anyString(), anyString(), anyLong()))
			.willReturn(new PaymentApprovalResult(300_000, "toss_key_1", null))
			.willReturn(new PaymentApprovalResult(300_000, "toss_key_2", null))
			.willReturn(new PaymentApprovalResult(100_000, "toss_key_3", null));

		cashFacade.confirmWalletCharge(CUSTOMER_ID, "pay_1", "order_1", 300_000);
		cashFacade.confirmWalletCharge(CUSTOMER_ID, "pay_2", "order_2", 300_000);
		cashFacade.confirmWalletCharge(CUSTOMER_ID, "pay_3", "order_3", 100_000);

		assertThat(walletChargeRepository.findByIdempotencyKey("order_2").orElseThrow().isReviewFlagged()).isTrue();
		assertThat(walletChargeRepository.findByIdempotencyKey("order_3").orElseThrow().isReviewFlagged()).isFalse();
	}

	@Test
	@DisplayName("재확인 배치가 나중에 완료로 맞추는 충전도 똑같이 검토 대상으로 표시될 수 있다")
	void flagsChargeCompletedByReconcileBatch() {
		Wallet wallet = createRecentlyJoinedCustomerWallet();
		given(paymentClient.approve(anyString(), anyString(), anyLong()))
			.willReturn(new PaymentApprovalResult(300_000, "toss_key_1", null));
		cashFacade.confirmWalletCharge(CUSTOMER_ID, "pay_1", "order_1", 300_000);
		createUncertainCharge(wallet, "order_2", 300_000);
		given(paymentClient.lookupByOrderId("order_2"))
			.willReturn(PaymentLookupResult.completed(new PaymentApprovalResult(300_000, "toss_key_2", null)));

		cashChargeReconcileUseCase.reconcile(LocalDateTime.now());

		assertThat(walletChargeRepository.findByIdempotencyKey("order_2").orElseThrow().isReviewFlagged()).isTrue();
	}

	@Test
	@DisplayName("나중에 PG 결제취소된 충전도 누적 합계에서 빠지지 않는다")
	void includesCanceledChargeInCumulativeTotal() {
		createRecentlyJoinedCustomerWallet();
		given(paymentClient.approve(anyString(), anyString(), anyLong()))
			.willReturn(new PaymentApprovalResult(300_000, "toss_key_1", null))
			.willReturn(new PaymentApprovalResult(300_000, "toss_key_2", null));
		cashFacade.confirmWalletCharge(CUSTOMER_ID, "pay_1", "order_1", 300_000);
		cancelCharge("order_1", 300_000);

		cashFacade.confirmWalletCharge(CUSTOMER_ID, "pay_2", "order_2", 300_000);

		assertThat(walletChargeRepository.findByIdempotencyKey("order_2").orElseThrow().isReviewFlagged()).isTrue();
	}

	@Test
	@DisplayName("가입 후 24시간이 지난 뒤 생긴 충전은 그 전 충전들의 누적이 50만 원을 넘겼어도 표시되지 않는다")
	void doesNotFlagChargeMadeAfterTheReviewWindow() {
		LocalDateTime joinedAt = LocalDateTime.now().minusHours(25);
		Wallet wallet = createCustomerWalletJoinedAt(joinedAt);
		backdateCompletedCharge(wallet, "old_order_1", 300_000, joinedAt.plusHours(1));
		backdateCompletedCharge(wallet, "old_order_2", 300_000, joinedAt.plusHours(2));
		given(paymentClient.approve(anyString(), anyString(), anyLong()))
			.willReturn(new PaymentApprovalResult(50_000, "toss_key_new", null));

		cashFacade.confirmWalletCharge(CUSTOMER_ID, "pay_new", "order_new", 50_000);

		assertThat(walletChargeRepository.findByIdempotencyKey("order_new").orElseThrow().isReviewFlagged()).isFalse();
	}

	private Wallet createRecentlyJoinedCustomerWallet() {
		return createCustomerWalletJoinedAt(LocalDateTime.now());
	}

	private Wallet createCustomerWalletJoinedAt(LocalDateTime joinedAt) {
		return transactionTemplate.execute(status -> {
			CashMember member = cashMemberRepository.save(
				new CashMember(CUSTOMER_ID, joinedAt, joinedAt, "러너" + CUSTOMER_ID));
			return walletRepository.save(new Wallet(member));
		});
	}

	private void cancelCharge(String orderId, long amount) {
		transactionTemplate.executeWithoutResult(status -> {
			WalletCharge charge = walletChargeRepository.findByIdempotencyKey(orderId).orElseThrow();
			charge.recordCancel(amount);
			walletChargeRepository.saveAndFlush(charge);
		});
	}

	// 결과를 몰라 재확인 배치가 나중에 처리할 충전을 흉내 낸다
	private void createUncertainCharge(Wallet wallet, String orderId, long amount) {
		transactionTemplate.executeWithoutResult(status -> {
			WalletCharge charge = walletChargeRepository.save(new WalletCharge(wallet, amount, "TOSS", orderId));
			ReflectionTestUtils.setField(charge, "status", ChargeStatus.UNCERTAIN);
			walletChargeRepository.saveAndFlush(charge);
		});
	}

	// 재확인 배치처럼 가드를 거치지 않고 생긴 완료 충전을 흉내 낸다. createDate를 직접 지정해 윈도 경계를 테스트한다
	private void backdateCompletedCharge(Wallet wallet, String orderId, long amount, LocalDateTime createDate) {
		transactionTemplate.execute(status -> {
			WalletCharge charge = walletChargeRepository.save(new WalletCharge(wallet, amount, "TOSS", orderId));
			ReflectionTestUtils.setField(charge, "status", ChargeStatus.COMPLETED);
			ReflectionTestUtils.setField(charge, "createDate", createDate);
			return walletChargeRepository.saveAndFlush(charge);
		});
	}
}
