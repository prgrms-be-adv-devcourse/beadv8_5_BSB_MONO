package com.bukang.cash.app.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.cash.domain.charge.WalletCharge;
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.out.charge.WalletChargeRepository;
import com.bukang.cash.out.member.CashMemberRepository;
import com.bukang.cash.out.wallet.WalletRepository;
import com.bukang.common.shared.notification.event.NotificationRequestedEvent;

import jakarta.persistence.EntityManagerFactory;

// 기한 알림 배치의 SQL 수가 대상 회원 수만큼 늘지 않는지 본다 (부하 측정 B-4에서 대상 × 2 + 4개였음)
// SQL 수는 Hibernate 통계로 센다. 통계를 켜야 해서 다른 테스트와 컨텍스트를 따로 쓴다
@SpringBootTest(properties = {
	"spring.jpa.properties.hibernate.generate_statistics=true",
	"logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN"
})
@ActiveProfiles("test")
@RecordApplicationEvents
class CashCardCancelDeadlineNoticeQueryCountTest {
	private static final int MEMBERS = 30;
	private static final long FIRST_MEMBER_ID = 100;
	// 2026-10-10 승인 → 마지막 날 2027-10-09, 3일 전은 2027-10-06
	private static final LocalDateTime THREE_DAYS_BEFORE = LocalDateTime.of(2027, 10, 6, 9, 0);

	@Autowired
	private CashCardCancelDeadlineNoticeUseCase cashCardCancelDeadlineNoticeUseCase;
	@Autowired
	private CashMemberRepository cashMemberRepository;
	@Autowired
	private WalletRepository walletRepository;
	@Autowired
	private WalletChargeRepository walletChargeRepository;
	@Autowired
	private TransactionTemplate transactionTemplate;
	@Autowired
	private EntityManagerFactory entityManagerFactory;
	@Autowired
	private ApplicationEvents applicationEvents;

	@AfterEach
	void tearDown() {
		walletChargeRepository.deleteAll();
		walletRepository.deleteAll();
		cashMemberRepository.deleteAll();
	}

	@Test
	@DisplayName("대상 회원이 30명이어도 지갑·충전은 한 번에 읽어 SQL이 회원 수만큼 늘지 않는다")
	void queriesDoNotGrowWithMembers() {
		for (long id = FIRST_MEMBER_ID; id < FIRST_MEMBER_ID + MEMBERS; id++) {
			Wallet wallet = createWallet(id, 10_000);
			completedCharge(wallet, 10_000, "charge_" + id, LocalDateTime.of(2026, 10, 10, 9, 0));
		}
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();

		cashCardCancelDeadlineNoticeUseCase.notifyDeadlines(THREE_DAYS_BEFORE);

		assertThat(applicationEvents.stream(NotificationRequestedEvent.class)).hasSize(MEMBERS);
		// 대상 찾기 2번(3일 전·1일 전) + 대상이 있는 날마다 지갑·충전 1번씩. 회원마다 읽으면 2 + 30 × 2 = 62개다
		assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(4);
	}

	private Wallet createWallet(long memberId, long balance) {
		return transactionTemplate.execute(status -> {
			LocalDateTime joined = LocalDateTime.of(2026, 1, 1, 0, 0);
			CashMember member = cashMemberRepository.save(new CashMember(memberId, joined, joined, "러너" + memberId));
			Wallet wallet = new Wallet(member);
			ReflectionTestUtils.setField(wallet, "balance", balance);
			return walletRepository.save(wallet);
		});
	}

	private void completedCharge(Wallet wallet, long amount, String orderId, LocalDateTime chargeDate) {
		WalletCharge charge = new WalletCharge(wallet, amount, "TOSS", orderId);
		charge.markCompleted("toss_key_" + orderId, chargeDate, null);
		walletChargeRepository.save(charge);
	}
}
