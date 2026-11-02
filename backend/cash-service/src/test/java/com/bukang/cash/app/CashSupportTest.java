package com.bukang.cash.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.cash.config.CashWalletProperties;
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.out.CashMemberRepository;
import com.bukang.cash.out.WalletRepository;

import jakarta.persistence.EntityManager;

// 시스템 지갑(1번)·홀딩 지갑(2번)은 member-service가 그 ID로 만든 회원의 가입 이벤트로 생긴다(CashSpecialWalletCheck 참고).
// 여기서는 그 결과(지갑이 이미 있는 상태)를 직접 만들어 두고, CashSupport의 조회 메서드를 검증한다
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CashSupportTest {
	@Autowired
	private CashSupport cashSupport;
	@Autowired
	private CashWalletProperties cashWalletProperties;
	@Autowired
	private CashMemberRepository cashMemberRepository;
	@Autowired
	private WalletRepository walletRepository;
	@Autowired
	private EntityManager entityManager;

	@Test
	@DisplayName("시스템 지갑(1번)·홀딩 지갑(2번)은 잔액 0으로 보장되어 있다")
	void specialWalletsStartAtZeroBalance() {
		Wallet systemWallet = createWallet(cashWalletProperties.systemMemberId(), "시스템");
		Wallet holdingWallet = createWallet(cashWalletProperties.holdingMemberId(), "홀딩");

		assertThat(systemWallet.getBalance()).isZero();
		assertThat(holdingWallet.getBalance()).isZero();
	}

	@Test
	@DisplayName("findSystemWallet()으로 시스템 지갑을 조회할 수 있다")
	void findSystemWallet() {
		createWallet(cashWalletProperties.systemMemberId(), "시스템");
		entityManager.flush();
		entityManager.clear();

		Wallet wallet = cashSupport.findSystemWallet();

		assertThat(wallet.getHolder().getId()).isEqualTo(cashWalletProperties.systemMemberId());
	}

	@Test
	@DisplayName("findHoldingWallet()으로 홀딩 지갑을 조회할 수 있다")
	void findHoldingWallet() {
		createWallet(cashWalletProperties.holdingMemberId(), "홀딩");
		entityManager.flush();
		entityManager.clear();

		Wallet wallet = cashSupport.findHoldingWallet();

		assertThat(wallet.getHolder().getId()).isEqualTo(cashWalletProperties.holdingMemberId());
	}

	private Wallet createWallet(long memberId, String nickname) {
		LocalDateTime now = LocalDateTime.of(2026, 10, 12, 10, 0);
		CashMember member = cashMemberRepository.save(new CashMember(memberId, now, now, nickname));
		return walletRepository.save(new Wallet(member));
	}
}
