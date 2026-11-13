package com.bukang.cash.app.payout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.cash.app.CashFacade;
import com.bukang.cash.config.CashWalletProperties;
import com.bukang.cash.domain.exception.payout.PayoutTransferFailedException;
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.payout.PayoutStage;
import com.bukang.cash.domain.payout.PayoutTransfer;
import com.bukang.cash.domain.payout.PayoutTransferStatus;
import com.bukang.cash.domain.payout.PayoutTransferType;
import com.bukang.cash.domain.seller.CashSeller;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.out.bank.BankTransferClient;
import com.bukang.cash.out.bank.BankTransferResult;
import com.bukang.cash.out.member.CashMemberRepository;
import com.bukang.cash.out.payout.PayoutTransferRepository;
import com.bukang.cash.out.seller.CashSellerRepository;
import com.bukang.cash.out.wallet.WalletLedgerRepository;
import com.bukang.cash.out.wallet.WalletRepository;

@SpringBootTest
@ActiveProfiles("test")
class CashPayoutReconcileUseCaseTest {
	private static final long SELLER_APPLICATION_ID = 50;
	private static final long SELLER_MEMBER_ID = 10;
	private static final long PAYOUT_ID = 600;

	@Autowired
	private CashFacade cashFacade;
	@Autowired
	private CashPayoutReconcileUseCase cashPayoutReconcileUseCase;
	@Autowired
	private CashWalletProperties cashWalletProperties;
	@Autowired
	private CashMemberRepository cashMemberRepository;
	@Autowired
	private CashSellerRepository cashSellerRepository;
	@Autowired
	private WalletRepository walletRepository;
	@Autowired
	private WalletLedgerRepository walletLedgerRepository;
	@Autowired
	private PayoutTransferRepository payoutTransferRepository;
	@Autowired
	private TransactionTemplate transactionTemplate;
	@MockitoBean
	private BankTransferClient bankTransferClient;

	@BeforeEach
	void setUp() {
		transactionTemplate.executeWithoutResult(status -> {
			LocalDateTime now = LocalDateTime.of(2026, 11, 10, 10, 0);
			walletRepository.save(new Wallet(cashMemberRepository.save(
				new CashMember(cashWalletProperties.holdingMemberId(), now, now, "홀딩"))));
			CashMember seller = cashMemberRepository.save(new CashMember(SELLER_MEMBER_ID, now, now, "판매자"));
			walletRepository.save(new Wallet(seller));
			cashSellerRepository.save(new CashSeller(SELLER_APPLICATION_ID, seller));
		});
	}

	@AfterEach
	void tearDown() {
		payoutTransferRepository.deleteAll();
		walletLedgerRepository.deleteAll();
		walletRepository.deleteAll();
		cashSellerRepository.deleteAll();
		cashMemberRepository.deleteAll();
	}

	@ParameterizedTest(name = "{0}인 송금을 {2}분 뒤 조회해 {1} → {3}, 홀딩 {4}")
	@CsvSource({
		"UNKNOWN, COMPLETED, 0, COMPLETED, -90000",
		"UNKNOWN, FAILED, 0, FAILED, 0",
		"UNKNOWN, UNKNOWN, 0, UNKNOWN, -90000",
		"UNKNOWN, NOT_FOUND, 11, FAILED, 0",
		"UNKNOWN, NOT_FOUND, 0, UNKNOWN, -90000",
		"REQUESTED, COMPLETED, 0, COMPLETED, -90000",
		"REQUESTED, NOT_FOUND, 11, FAILED, 0"
	})
	@DisplayName("결과를 모르는 송금을 은행에 조회해 완료나 실패로 맞추고, 실패면 홀딩 지갑 차감을 되돌린다")
	void reconcile(
		PayoutTransferStatus startStatus, BankTransferResult lookupResult, int minutesLater,
		PayoutTransferStatus expectedStatus, long expectedHoldingBalance
	) {
		leaveTransferIn(startStatus);
		PayoutTransfer transfer = settlement();
		given(bankTransferClient.lookup(transfer.bankTransferId())).willReturn(lookupResult);

		cashPayoutReconcileUseCase.reconcile(LocalDateTime.now().plusMinutes(minutesLater));

		assertThat(settlement().getStatus()).isEqualTo(expectedStatus);
		assertThat(walletRepository.findById(cashWalletProperties.holdingMemberId()).orElseThrow().getBalance())
			.isEqualTo(expectedHoldingBalance);
	}

	@ParameterizedTest(name = "재확인이 먼저 {0}로 맞추고 은행이 {1}로 답하면 → {2}, 홀딩 {3}")
	@CsvSource({
		"FAILED, FAILED, FAILED, 0",
		"COMPLETED, UNKNOWN, COMPLETED, -90000"
	})
	@DisplayName("지급 요청이 은행 응답을 기다리는 사이 재확인이 먼저 반영하면, 지급 요청은 다시 반영하지 않고 그 결과로 답한다")
	void reconcileWinsWhilePayoutWaitsForBank(
		BankTransferResult lookupResult, BankTransferResult transferResult,
		PayoutTransferStatus expectedStatus, long expectedHoldingBalance
	) {
		given(bankTransferClient.lookup(anyString())).willReturn(lookupResult);
		given(bankTransferClient.transfer(anyString(), anyLong(), anyLong())).willAnswer(invocation -> {
			cashPayoutReconcileUseCase.reconcile(LocalDateTime.now());
			return transferResult;
		});

		Throwable thrown =
			catchThrowable(() -> cashFacade.payout(PAYOUT_ID, SELLER_APPLICATION_ID, PayoutStage.FIRST, 90_000, 0));

		assertThat(settlement().getStatus()).isEqualTo(expectedStatus);
		assertThat(walletRepository.findById(cashWalletProperties.holdingMemberId()).orElseThrow().getBalance())
			.as("되돌리기는 한 번만 일어나야 한다").isEqualTo(expectedHoldingBalance);
		if (expectedStatus == PayoutTransferStatus.FAILED) {
			assertThat(thrown).isInstanceOf(PayoutTransferFailedException.class);
		} else {
			assertThat(thrown).isNull();
		}
	}

	@Test
	@DisplayName("한 송금을 처리하다 오류가 나도(은행 조회 실패 포함) 그 송금만 결과 모름으로 두고 나머지 송금은 맞춘다")
	void unexpectedErrorSkipsOnlyThatTransfer() {
		given(bankTransferClient.transfer(anyString(), anyLong(), anyLong())).willReturn(BankTransferResult.UNKNOWN);
		for (long payoutId = PAYOUT_ID; payoutId < PAYOUT_ID + 3; payoutId++) {
			long id = payoutId;
			catchThrowable(() -> cashFacade.payout(id, SELLER_APPLICATION_ID, PayoutStage.FIRST, 90_000, 0));
		}
		given(bankTransferClient.lookup(anyString())).willReturn(BankTransferResult.COMPLETED);
		given(bankTransferClient.lookup(settlement(PAYOUT_ID).bankTransferId()))
			.willThrow(new IllegalStateException("은행 조회 실패"));

		cashPayoutReconcileUseCase.reconcile(LocalDateTime.now());

		assertThat(settlement(PAYOUT_ID).getStatus()).isEqualTo(PayoutTransferStatus.UNKNOWN);
		assertThat(settlement(PAYOUT_ID + 1).getStatus()).isEqualTo(PayoutTransferStatus.COMPLETED);
		assertThat(settlement(PAYOUT_ID + 2).getStatus()).isEqualTo(PayoutTransferStatus.COMPLETED);
	}

	// 결과 모름: 은행이 결과 모름으로 답함. 요청함: 은행을 부르는 중 서버가 멈춘 것처럼 결과 반영 전에 끊김
	private void leaveTransferIn(PayoutTransferStatus status) {
		if (status == PayoutTransferStatus.UNKNOWN) {
			given(bankTransferClient.transfer(anyString(), anyLong(), anyLong()))
				.willReturn(BankTransferResult.UNKNOWN);
		} else {
			given(bankTransferClient.transfer(anyString(), anyLong(), anyLong()))
				.willThrow(new IllegalStateException("서버 멈춤"));
		}
		catchThrowable(() -> cashFacade.payout(PAYOUT_ID, SELLER_APPLICATION_ID, PayoutStage.FIRST, 90_000, 0));
		assertThat(settlement().getStatus()).isEqualTo(status);
	}

	private PayoutTransfer settlement() {
		return settlement(PAYOUT_ID);
	}

	private PayoutTransfer settlement(long payoutId) {
		return payoutTransferRepository.findByPayoutIdAndTransferType(payoutId, PayoutTransferType.SETTLEMENT)
			.orElseThrow();
	}
}
