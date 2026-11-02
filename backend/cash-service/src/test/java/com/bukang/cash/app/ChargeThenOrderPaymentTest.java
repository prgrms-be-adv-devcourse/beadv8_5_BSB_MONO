package com.bukang.cash.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

import com.bukang.cash.config.CashWalletProperties;
import com.bukang.cash.domain.exception.PaymentMemberMismatchException;
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.payment.Payment;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.dto.CashPaymentResponseDto;
import com.bukang.cash.out.CashMemberRepository;
import com.bukang.cash.out.PaymentRepository;
import com.bukang.cash.out.WalletChargeRepository;
import com.bukang.cash.out.WalletLedgerRepository;
import com.bukang.cash.out.WalletRepository;
import com.bukang.cash.out.pg.PaymentApprovalResult;
import com.bukang.cash.out.pg.PaymentClient;

// PRO-82: 충전(/charges/confirm)과 결제(completeOrderPayment)는 서로 다른 API 호출(별개 트랜잭션)이다.
// 충전이 이미 커밋된 뒤 결제가 실패해도 충전금에 영향이 없는지, 실패 방식을 둘로 나눠 확인한다.
// 1) 잔액 부족: 예외 없이 FAILED로 커밋되는 경로
// 2) 회원 불일치: 예외로 거절돼 그 결제 시도 자체가 롤백되는 경로
@SpringBootTest
@ActiveProfiles("test")
class ChargeThenOrderPaymentTest {
	private static final long CUSTOMER_ID = 3;

	@Autowired
	private CashFacade cashFacade;
	@Autowired
	private CashWalletProperties cashWalletProperties;
	@Autowired
	private CashMemberRepository cashMemberRepository;
	@Autowired
	private WalletRepository walletRepository;
	@Autowired
	private WalletChargeRepository walletChargeRepository;
	@Autowired
	private WalletLedgerRepository walletLedgerRepository;
	@Autowired
	private PaymentRepository paymentRepository;
	@Autowired
	private TransactionTemplate transactionTemplate;

	@MockitoBean
	private PaymentClient paymentClient;

	@AfterEach
	void tearDown() {
		paymentRepository.deleteAll();
		walletChargeRepository.deleteAll();
		walletLedgerRepository.deleteAll();
		walletRepository.deleteAll();
		cashMemberRepository.deleteAll();
	}

	@Test
	@DisplayName("충전 후 다른 결제가 잔액 부족으로 실패해도(예외 없이 FAILED 커밋) 충전금은 그대로 지갑에 남는다")
	void chargeSucceeds_thenOrderPaymentFailsWithInsufficientBalance_chargedAmountStays() {
		createCustomerWallet(0);
		given(paymentClient.approve("pay_1", "order_charge_1", 10_000))
			.willReturn(new PaymentApprovalResult(10_000, "toss_key_1"));

		cashFacade.confirmWalletCharge(CUSTOMER_ID, "pay_1", "order_charge_1", 10_000);
		assertBalance(CUSTOMER_ID, 10_000);

		// 충전했어도 여전히 부족한 금액으로 결제 시도 (충전과 무관한 다른 주문)
		CashPaymentResponseDto result = cashFacade.completeOrderPayment(1_000, CUSTOMER_ID, 20_000);

		assertThat(result.outcome()).isEqualTo(CashPaymentResponseDto.Outcome.INSUFFICIENT_BALANCE);
		assertThat(result.shortage()).isEqualTo(10_000);
		assertBalance(CUSTOMER_ID, 10_000);
	}

	@Test
	@DisplayName("충전 후 다른 결제가 회원 불일치로 예외 거절돼도(그 결제 시도만 롤백) 충전금은 그대로 지갑에 남는다")
	void chargeSucceeds_thenOrderPaymentRejectedByMemberMismatch_chargedAmountStays() {
		createCustomerWallet(0);
		given(paymentClient.approve("pay_1", "order_charge_2", 10_000))
			.willReturn(new PaymentApprovalResult(10_000, "toss_key_1"));
		cashFacade.confirmWalletCharge(CUSTOMER_ID, "pay_1", "order_charge_2", 10_000);
		assertBalance(CUSTOMER_ID, 10_000);

		// 다른 회원 지갑으로 만들어진 결제 행에, 같은 orderId로 결제 시도 → 회원 불일치로 예외 거절
		long otherMemberId = 4;
		Wallet otherWallet = createWallet(otherMemberId, 0);
		long orderId = 2_000;
		paymentRepository.save(new Payment(orderId, otherWallet, 5_000));

		assertThatThrownBy(() -> cashFacade.completeOrderPayment(orderId, CUSTOMER_ID, 5_000))
			.isInstanceOf(PaymentMemberMismatchException.class);

		assertBalance(CUSTOMER_ID, 10_000);
	}

	@Test
	@DisplayName("충전 후 같은 주문으로 재결제하면 충전금을 그대로 써서 결제가 성공한다")
	void chargeSucceeds_thenRetryingSameOrderPaymentSucceeds() {
		createCustomerWallet(0);
		long orderId = 3_000;
		long amount = 10_000;

		CashPaymentResponseDto first = cashFacade.completeOrderPayment(orderId, CUSTOMER_ID, amount);
		assertThat(first.outcome()).isEqualTo(CashPaymentResponseDto.Outcome.INSUFFICIENT_BALANCE);
		assertThat(first.shortage()).isEqualTo(10_000);

		given(paymentClient.approve("pay_1", "order_charge_3", 10_000))
			.willReturn(new PaymentApprovalResult(10_000, "toss_key_1"));
		cashFacade.confirmWalletCharge(CUSTOMER_ID, "pay_1", "order_charge_3", 10_000);

		CashPaymentResponseDto second = cashFacade.completeOrderPayment(orderId, CUSTOMER_ID, amount);

		assertThat(second.outcome()).isEqualTo(CashPaymentResponseDto.Outcome.PAID);
		assertBalance(CUSTOMER_ID, 0);
		assertBalance(cashWalletProperties.holdingMemberId(), 10_000);
	}

	private void createCustomerWallet(long initialBalance) {
		createWallet(CUSTOMER_ID, initialBalance);
		createWallet(cashWalletProperties.holdingMemberId(), 0);
	}

	private Wallet createWallet(long memberId, long initialBalance) {
		return transactionTemplate.execute(status -> {
			LocalDateTime now = LocalDateTime.of(2026, 10, 16, 10, 0);
			CashMember member = cashMemberRepository.save(new CashMember(memberId, now, now, "러너" + memberId));
			Wallet wallet = new Wallet(member);
			if (initialBalance != 0) {
				ReflectionTestUtils.setField(wallet, "balance", initialBalance);
			}
			return walletRepository.save(wallet);
		});
	}

	private void assertBalance(long holderId, int expected) {
		assertThat(walletRepository.findById(holderId).orElseThrow().getBalance()).isEqualTo(expected);
	}
}
