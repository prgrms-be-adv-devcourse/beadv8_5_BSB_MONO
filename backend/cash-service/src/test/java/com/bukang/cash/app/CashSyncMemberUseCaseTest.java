package com.bukang.cash.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.out.CashMemberRepository;
import com.bukang.cash.out.WalletRepository;

@SpringBootTest
@ActiveProfiles("test")
class CashSyncMemberUseCaseTest {
	private static final int MEMBER_ID = 5;

	@Autowired
	private CashFacade cashFacade;
	@Autowired
	private CashMemberRepository cashMemberRepository;
	@Autowired
	private WalletRepository walletRepository;

	@AfterEach
	void tearDown() {
		walletRepository.deleteAll();
		cashMemberRepository.deleteAll();
	}

	@Test
	@DisplayName("새 회원을 동기화하면 CashMember와 Wallet이 한 번의 호출로 함께 생성된다")
	void syncMember_newMember_createsWalletTogether() {
		LocalDateTime now = LocalDateTime.of(2026, 10, 16, 10, 0);

		CashMember cashMember = cashFacade.syncMember(MEMBER_ID, now, now, "러너5");

		assertThat(cashMember.getId()).isEqualTo(MEMBER_ID);
		assertThat(cashMemberRepository.findById(MEMBER_ID)).isPresent();
		assertThat(walletRepository.findById(MEMBER_ID))
			.as("새 회원이면 같은 호출 안에서 지갑도 바로 만들어져야 한다")
			.isPresent();
	}

	@Test
	@DisplayName("이미 있는 회원을 다시 동기화해도(정보 수정) 지갑을 새로 만들지 않는다")
	void syncMember_existingMember_doesNotCreateAnotherWallet() {
		LocalDateTime now = LocalDateTime.of(2026, 10, 16, 10, 0);
		cashFacade.syncMember(MEMBER_ID, now, now, "러너5");
		int walletCountAfterFirstSync = (int) walletRepository.count();

		cashFacade.syncMember(MEMBER_ID, now, now.plusDays(1), "러너5-수정");

		assertThat(walletRepository.count()).isEqualTo(walletCountAfterFirstSync);
		assertThat(cashMemberRepository.findById(MEMBER_ID).orElseThrow().getNickname())
			.isEqualTo("러너5-수정");
	}
}
