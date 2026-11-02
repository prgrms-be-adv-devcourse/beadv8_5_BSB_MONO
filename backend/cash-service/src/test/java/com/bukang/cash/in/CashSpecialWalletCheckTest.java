package com.bukang.cash.in;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.cash.config.CashWalletProperties;
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.out.CashMemberRepository;
import com.bukang.cash.out.WalletRepository;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

// @Profile("!test")라 실제 빈으로는 안 뜨므로, 직접 생성해서 run()을 호출해 확인한다
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CashSpecialWalletCheckTest {
	@Autowired
	private CashWalletProperties cashWalletProperties;
	@Autowired
	private CashMemberRepository cashMemberRepository;
	@Autowired
	private WalletRepository walletRepository;

	private ListAppender<ILoggingEvent> logAppender;

	@BeforeEach
	void setUp() {
		logAppender = new ListAppender<>();
		logAppender.start();
		((Logger)LoggerFactory.getLogger(CashSpecialWalletCheck.class)).addAppender(logAppender);
	}

	@AfterEach
	void tearDown() {
		((Logger)LoggerFactory.getLogger(CashSpecialWalletCheck.class)).detachAppender(logAppender);
	}

	@Test
	@DisplayName("시스템·홀딩 지갑이 없으면 경고만 남기고, 지갑을 만들지는 않는다")
	void warnsWhenWalletsMissing() {
		new CashSpecialWalletCheck(cashWalletProperties, walletRepository).run(null);

		assertThat(logAppender.list)
			.hasSize(2)
			.allMatch(event -> event.getLevel() == Level.WARN);
		assertThat(walletRepository.count()).isZero();
	}

	@Test
	@DisplayName("시스템·홀딩 지갑이 이미 있으면 경고를 남기지 않는다")
	void doesNotWarnWhenWalletsExist() {
		LocalDateTime now = LocalDateTime.of(2026, 10, 12, 10, 0);
		saveWallet(cashWalletProperties.systemMemberId(), "시스템", now);
		saveWallet(cashWalletProperties.holdingMemberId(), "홀딩", now);

		new CashSpecialWalletCheck(cashWalletProperties, walletRepository).run(null);

		assertThat(logAppender.list).isEmpty();
	}

	private void saveWallet(long memberId, String nickname, LocalDateTime now) {
		CashMember member = cashMemberRepository.save(new CashMember(memberId, now, now, nickname));
		walletRepository.save(new Wallet(member));
	}
}
