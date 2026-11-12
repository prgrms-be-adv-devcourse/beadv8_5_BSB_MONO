package com.bukang.cash.app.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.cash.domain.charge.WalletCharge;
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.wallet.LedgerCategory;
import com.bukang.cash.domain.wallet.LedgerRefType;
import com.bukang.cash.domain.wallet.LedgerType;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.dto.PageDto;
import com.bukang.cash.dto.wallet.WalletLedgerItemDto;
import com.bukang.cash.out.charge.WalletChargeRepository;
import com.bukang.cash.out.member.CashMemberRepository;
import com.bukang.cash.out.wallet.WalletLedgerRepository;
import com.bukang.cash.out.wallet.WalletRepository;

// 남은 금액은 충전 줄에만 붙으므로, 충전 줄이 없는 페이지에서는 충전 내역을 읽지 않는다 (BUK-96)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CashWalletLedgerQueryUseCaseTest {
	private static final long MEMBER_ID = 3;
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 10, 0);

	@Autowired
	private CashWalletLedgerQueryUseCase cashWalletLedgerQueryUseCase;
	@Autowired
	private CashMemberRepository cashMemberRepository;
	@Autowired
	private WalletRepository walletRepository;
	@Autowired
	private WalletLedgerRepository walletLedgerRepository;
	@MockitoSpyBean
	private WalletChargeRepository walletChargeRepository;

	@BeforeEach
	void setUp() {
		CashMember member = cashMemberRepository.save(new CashMember(MEMBER_ID, NOW, NOW, "러너3"));
		Wallet wallet = walletRepository.save(new Wallet(member));
		WalletCharge charge = new WalletCharge(wallet, 10_000, "TOSS", "order_1");
		LocalDateTime approvedAt = LocalDateTime.of(2099, 1, 1, 10, 0);
		charge.markCompleted("toss_key_1", approvedAt, approvedAt);
		walletChargeRepository.save(charge);
		walletLedgerRepository.save(wallet.credit(10_000, LedgerType.충전__PG결제_토스페이먼츠, charge));
		walletLedgerRepository.save(wallet.debit(5_000, LedgerType.사용__주문결제, LedgerRefType.Order, 100));
	}

	@AfterEach
	void tearDown() {
		reset(walletChargeRepository);
	}

	@Test
	@DisplayName("충전 줄이 없는 페이지(결제 탭, 결제 줄만 있는 전체 탭 페이지)에서는 충전 내역을 읽지 않는다")
	void skipsChargeQueryWithoutChargeRows() {
		reset(walletChargeRepository);

		PageDto<WalletLedgerItemDto> paymentTab =
			cashWalletLedgerQueryUseCase.findLedgers(MEMBER_ID, LedgerCategory.PAYMENT, 0, 20, NOW);
		PageDto<WalletLedgerItemDto> allTabFirstRow =
			cashWalletLedgerQueryUseCase.findLedgers(MEMBER_ID, null, 0, 1, NOW);

		assertThat(paymentTab.getContent()).hasSize(1);
		assertThat(allTabFirstRow.getContent()).hasSize(1);
		verify(walletChargeRepository, never()).findByWalletAndStatus(any(), any());
	}

	@Test
	@DisplayName("충전 줄이 있는 페이지에서는 충전 내역을 한 번 읽어 남은 금액을 붙인다")
	void readsChargesOnceWithChargeRows() {
		reset(walletChargeRepository);

		PageDto<WalletLedgerItemDto> allTab =
			cashWalletLedgerQueryUseCase.findLedgers(MEMBER_ID, null, 0, 20, NOW);

		assertThat(allTab.getContent().get(1).charge().remainingAmount()).isEqualTo(5_000);
		verify(walletChargeRepository, times(1)).findByWalletAndStatus(any(), any());
	}
}
