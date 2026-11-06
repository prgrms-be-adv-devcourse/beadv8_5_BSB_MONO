package com.bukang.cash.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.cash.domain.charge.WalletCharge;
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.dto.RefundableChargeDto;
import com.bukang.cash.dto.RefundableChargesResponseDto;
import com.bukang.cash.out.CashMemberRepository;
import com.bukang.cash.out.WalletChargeRepository;
import com.bukang.cash.out.WalletRepository;

// 기대값은 정책 5장 2차 1 「PG 결제취소와 기한」의 예시에서 가져온다.
// 지갑 잔액은 ReflectionTestUtils로 직접 세팅한다 (결제 장부까지 만들 필요가 없다)
@SpringBootTest
@ActiveProfiles("test")
class CashRefundableChargeQueryTest {
	private static final long CUSTOMER_ID = 3;
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 10, 0);

	@Autowired
	private CashFacade cashFacade;
	@Autowired
	private CashMemberRepository cashMemberRepository;
	@Autowired
	private WalletRepository walletRepository;
	@Autowired
	private WalletChargeRepository walletChargeRepository;
	@Autowired
	private TransactionTemplate transactionTemplate;

	@AfterEach
	void tearDown() {
		walletChargeRepository.deleteAll();
		walletRepository.deleteAll();
		cashMemberRepository.deleteAll();
	}

	@Test
	@DisplayName("A 15,000·B 10,000·C 20,000·D 40,000원을 충전하고 20,000원을 쓰면 남은 금액은 A 0·B 5,000·C 20,000·D 40,000원이다")
	void remainingAmountsFillFromLatestCharge() {
		Wallet wallet = createWallet(CUSTOMER_ID, 65_000);
		completedCharge(wallet, 15_000, "charge_a", LocalDateTime.of(2026, 1, 13, 9, 0));
		completedCharge(wallet, 10_000, "charge_b", LocalDateTime.of(2026, 10, 10, 9, 0));
		completedCharge(wallet, 20_000, "charge_c", LocalDateTime.of(2026, 10, 13, 9, 0));
		completedCharge(wallet, 40_000, "charge_d", LocalDateTime.of(2026, 10, 14, 9, 0));

		RefundableChargesResponseDto response = cashFacade.findRefundableCharges(CUSTOMER_ID, NOW);

		assertThat(response.balance()).isEqualTo(65_000);
		assertThat(response.charges())
			.extracting(RefundableChargeDto::amount, RefundableChargeDto::remainingAmount)
			.containsExactly(
				tuple(40_000L, 40_000L),
				tuple(20_000L, 20_000L),
				tuple(10_000L, 5_000L),
				tuple(15_000L, 0L));
	}

	@Test
	@DisplayName("2027-10-10에 조회하면 10/10 충전분은 마지막 날(2027-10-09)이 지나 기한 지남, 10/13·10/14 충전분은 기한 안이다")
	void eachChargeHasLastCancelableDateAndExpiry() {
		Wallet wallet = createWallet(CUSTOMER_ID, 65_000);
		completedCharge(wallet, 10_000, "charge_b", LocalDateTime.of(2026, 10, 10, 9, 0));
		completedCharge(wallet, 20_000, "charge_c", LocalDateTime.of(2026, 10, 13, 9, 0));
		completedCharge(wallet, 40_000, "charge_d", LocalDateTime.of(2026, 10, 14, 9, 0));

		RefundableChargesResponseDto response =
			cashFacade.findRefundableCharges(CUSTOMER_ID, LocalDateTime.of(2027, 10, 10, 0, 0));

		assertThat(response.charges())
			.extracting(RefundableChargeDto::lastCancelableDate, RefundableChargeDto::expired)
			.containsExactly(
				tuple(LocalDate.of(2027, 10, 13), false),
				tuple(LocalDate.of(2027, 10, 12), false),
				tuple(LocalDate.of(2027, 10, 9), true));
	}

	@Test
	@DisplayName("가장 빠른 기한은 남은 금액이 있고 기한이 지나지 않은 충전 중에서 고른다 (모두 쓴 A, 기한 지난 B는 빼고 C의 2027-10-12)")
	void earliestDeadlineSkipsUsedUpAndExpiredCharges() {
		Wallet wallet = createWallet(CUSTOMER_ID, 65_000);
		completedCharge(wallet, 15_000, "charge_a", LocalDateTime.of(2026, 1, 13, 9, 0));
		completedCharge(wallet, 10_000, "charge_b", LocalDateTime.of(2026, 10, 10, 9, 0));
		completedCharge(wallet, 20_000, "charge_c", LocalDateTime.of(2026, 10, 13, 9, 0));
		completedCharge(wallet, 40_000, "charge_d", LocalDateTime.of(2026, 10, 14, 9, 0));

		RefundableChargesResponseDto response =
			cashFacade.findRefundableCharges(CUSTOMER_ID, LocalDateTime.of(2027, 10, 10, 0, 0));

		assertThat(response.earliestLastCancelableDate()).isEqualTo(LocalDate.of(2027, 10, 12));
	}

	@Test
	@DisplayName("잔액이 0원이라 남은 금액이 있는 충전이 없으면 가장 빠른 기한은 비어 있다")
	void earliestDeadlineIsEmptyWhenNothingRemains() {
		Wallet wallet = createWallet(CUSTOMER_ID, 0);
		completedCharge(wallet, 10_000, "charge_a", LocalDateTime.of(2026, 10, 10, 9, 0));

		RefundableChargesResponseDto response = cashFacade.findRefundableCharges(CUSTOMER_ID, NOW);

		assertThat(response.earliestLastCancelableDate()).isNull();
		assertThat(response.charges()).extracting(RefundableChargeDto::remainingAmount).containsExactly(0L);
	}

	@Test
	@DisplayName("승인 실패·확인 중인 충전은 돈이 들어오지 않았으므로 목록에 없다")
	void onlyCompletedChargesAreListed() {
		Wallet wallet = createWallet(CUSTOMER_ID, 10_000);
		completedCharge(wallet, 10_000, "charge_done", LocalDateTime.of(2026, 10, 10, 9, 0));
		WalletCharge failed = new WalletCharge(wallet, 30_000, "TOSS", "charge_failed");
		failed.markFailed();
		walletChargeRepository.save(failed);
		WalletCharge uncertain = new WalletCharge(wallet, 50_000, "TOSS", "charge_uncertain");
		uncertain.markUncertain();
		walletChargeRepository.save(uncertain);

		RefundableChargesResponseDto response = cashFacade.findRefundableCharges(CUSTOMER_ID, NOW);

		assertThat(response.charges()).extracting(RefundableChargeDto::amount).containsExactly(10_000L);
	}

	@Test
	@DisplayName("PG 승인 시각이 있으면 그 시각으로 기한을 계산한다 (재확인이 늦어 충전 시각이 다음 날 00:10이어도 2027-10-14)")
	void lastCancelableDateUsesPgApprovedAtOverChargeDate() {
		Wallet wallet = createWallet(CUSTOMER_ID, 10_000);
		WalletCharge charge = new WalletCharge(wallet, 10_000, "TOSS", "charge_late");
		LocalDateTime lateChargeDate = LocalDateTime.of(2026, 10, 16, 0, 10);
		LocalDateTime pgApprovedAt = LocalDateTime.of(2026, 10, 15, 23, 59);
		charge.markCompleted("toss_key_late", lateChargeDate, pgApprovedAt);
		walletChargeRepository.save(charge);

		RefundableChargesResponseDto response = cashFacade.findRefundableCharges(CUSTOMER_ID, NOW);

		assertThat(response.charges()).extracting(RefundableChargeDto::lastCancelableDate)
			.containsExactly(LocalDate.of(2027, 10, 14));
	}

	@Test
	@DisplayName("10,000원 충전 중 5,000원을 이미 PG 결제취소했으면 잔액이 10,000원이어도 그 충전에는 5,000원까지만 남은 금액으로 채운다")
	void remainingAmountExcludesAlreadyCanceledAmount() {
		Wallet wallet = createWallet(CUSTOMER_ID, 10_000);
		WalletCharge charge = new WalletCharge(wallet, 10_000, "TOSS", "charge_partly_canceled");
		charge.markCompleted("toss_key_partly_canceled", LocalDateTime.of(2026, 10, 10, 9, 0), null);
		charge.recordCancel(5_000);
		walletChargeRepository.save(charge);

		RefundableChargesResponseDto response = cashFacade.findRefundableCharges(CUSTOMER_ID, NOW);

		assertThat(response.charges()).extracting(RefundableChargeDto::remainingAmount).containsExactly(5_000L);
	}

	private Wallet createWallet(long memberId, long balance) {
		return transactionTemplate.execute(status -> {
			CashMember member = cashMemberRepository.save(new CashMember(memberId, NOW, NOW, "러너" + memberId));
			Wallet wallet = new Wallet(member);
			ReflectionTestUtils.setField(wallet, "balance", balance);
			return walletRepository.save(wallet);
		});
	}

	private WalletCharge completedCharge(Wallet wallet, long amount, String orderId, LocalDateTime chargeDate) {
		WalletCharge charge = new WalletCharge(wallet, amount, "TOSS", orderId);
		charge.markCompleted("toss_key_" + orderId, chargeDate, null);
		return walletChargeRepository.save(charge);
	}
}
