package com.bukang.cash.app.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.LocalDateTime;
import java.util.List;

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
import com.bukang.common.shared.notification.NotificationKind;
import com.bukang.common.shared.notification.event.NotificationRequestedEvent;

// 정책 5장 2차 1 「PG 결제취소와 기한 › 기한 알림」. Kafka로 보내기 전에 발행한 알림 이벤트를 본다 (보내는 일은 common이 맡는다).
// 2026-10-10 승인 → 마지막 날 2027-10-09. 2027-10-06이 3일 전, 2027-10-08이 1일 전이다
@SpringBootTest
@ActiveProfiles("test")
@RecordApplicationEvents
class CashCardCancelDeadlineNoticeTest {
	private static final long CUSTOMER_ID = 3;
	private static final long OTHER_ID = 4;
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
	private ApplicationEvents applicationEvents;

	@AfterEach
	void tearDown() {
		walletChargeRepository.deleteAll();
		walletRepository.deleteAll();
		cashMemberRepository.deleteAll();
	}

	@Test
	@DisplayName("마지막 날이 3일 뒤인 몫은 「3일 남았어요」, 1일 뒤인 몫은 「내일까지예요」로 회원에게 알린다")
	void notifiesThreeDaysAndOneDayBefore() {
		Wallet customer = createWallet(CUSTOMER_ID, 5_000);
		completedCharge(customer, 10_000, "charge_a", LocalDateTime.of(2026, 10, 10, 9, 0));
		Wallet other = createWallet(OTHER_ID, 20_000);
		completedCharge(other, 20_000, "charge_b", LocalDateTime.of(2026, 10, 8, 9, 0));

		cashCardCancelDeadlineNoticeUseCase.notifyDeadlines(THREE_DAYS_BEFORE);

		assertThat(notices())
			.extracting(NotificationRequestedEvent::memberId, NotificationRequestedEvent::kind,
				NotificationRequestedEvent::title, NotificationRequestedEvent::body, NotificationRequestedEvent::href)
			.containsExactlyInAnyOrder(
				tuple(CUSTOMER_ID, NotificationKind.CARD_CANCEL_DEADLINE, "카드로 돌려받을 수 있는 기한이 3일 남았어요",
					"2027-10-09까지 환급하면 5,000원을 카드로 돌려받아요.\n기한이 지나면 계좌로만 돌려받을 수 있어요.",
					"/me/deposit/refund"),
				tuple(OTHER_ID, NotificationKind.CARD_CANCEL_DEADLINE, "카드로 돌려받을 수 있는 기한이 내일까지예요",
					"2027-10-07까지 환급하면 20,000원을 카드로 돌려받아요.\n기한이 지나면 계좌로만 돌려받을 수 있어요.",
					"/me/deposit/refund"));
	}

	@Test
	@DisplayName("마지막 날이 같은 충전끼리는 남은 금액을 합쳐 알림 한 건으로 보낸다")
	void sameDeadlineChargesAreCombined() {
		Wallet wallet = createWallet(CUSTOMER_ID, 30_000);
		completedCharge(wallet, 10_000, "charge_morning", LocalDateTime.of(2026, 10, 10, 9, 0));
		completedCharge(wallet, 20_000, "charge_night", LocalDateTime.of(2026, 10, 10, 22, 0));

		cashCardCancelDeadlineNoticeUseCase.notifyDeadlines(THREE_DAYS_BEFORE);

		assertThat(notices()).extracting(NotificationRequestedEvent::body)
			.containsExactly("2027-10-09까지 환급하면 30,000원을 카드로 돌려받아요.\n기한이 지나면 계좌로만 돌려받을 수 있어요.");
	}

	@Test
	@DisplayName("남은 예치금을 최근 충전부터 채워 기한이 다가온 충전의 남은 금액이 0원이면 알림을 보내지 않는다")
	void noNoticeWhenRemainingAmountIsZero() {
		Wallet wallet = createWallet(CUSTOMER_ID, 15_000);
		completedCharge(wallet, 10_000, "charge_old", LocalDateTime.of(2026, 10, 10, 9, 0));
		completedCharge(wallet, 20_000, "charge_new", LocalDateTime.of(2027, 1, 5, 9, 0));

		cashCardCancelDeadlineNoticeUseCase.notifyDeadlines(THREE_DAYS_BEFORE);

		assertThat(notices()).isEmpty();
	}

	@Test
	@DisplayName("배치가 같은 날 두 번 돌아도 메시지 ID가 같아 알림 서비스에는 한 건만 쌓인다")
	void rerunUsesSameEventId() {
		Wallet wallet = createWallet(CUSTOMER_ID, 5_000);
		completedCharge(wallet, 10_000, "charge_a", LocalDateTime.of(2026, 10, 10, 9, 0));

		cashCardCancelDeadlineNoticeUseCase.notifyDeadlines(THREE_DAYS_BEFORE);
		cashCardCancelDeadlineNoticeUseCase.notifyDeadlines(THREE_DAYS_BEFORE.plusHours(1));

		List<String> eventIds = notices().stream().map(NotificationRequestedEvent::eventId).toList();
		assertThat(eventIds).hasSize(2).doesNotContainNull();
		assertThat(eventIds.get(0)).isEqualTo(eventIds.get(1));
		assertThat(eventIds.get(0)).hasSizeLessThanOrEqualTo(36);
	}

	@Test
	@DisplayName("3일 전 알림과 1일 전 알림은 메시지 ID가 달라 둘 다 쌓인다")
	void threeDaysAndOneDayNoticesHaveDifferentEventIds() {
		Wallet wallet = createWallet(CUSTOMER_ID, 5_000);
		completedCharge(wallet, 10_000, "charge_a", LocalDateTime.of(2026, 10, 10, 9, 0));

		cashCardCancelDeadlineNoticeUseCase.notifyDeadlines(THREE_DAYS_BEFORE);
		cashCardCancelDeadlineNoticeUseCase.notifyDeadlines(THREE_DAYS_BEFORE.plusDays(2));

		assertThat(notices()).extracting(NotificationRequestedEvent::eventId).doesNotHaveDuplicates().hasSize(2);
	}

	@Test
	@DisplayName("PG 승인 시각이 있으면 그 시각으로 마지막 날을 본다 (10/9 23:59 승인, 10/10 00:10 기록 → 마지막 날 2027-10-08)")
	void usesPgApprovedAtOverChargeDate() {
		Wallet wallet = createWallet(CUSTOMER_ID, 10_000);
		WalletCharge charge = new WalletCharge(wallet, 10_000, "TOSS", "charge_late");
		charge.markCompleted("toss_key_late", LocalDateTime.of(2026, 10, 10, 0, 10),
			LocalDateTime.of(2026, 10, 9, 23, 59));
		walletChargeRepository.save(charge);

		cashCardCancelDeadlineNoticeUseCase.notifyDeadlines(THREE_DAYS_BEFORE);
		assertThat(notices()).isEmpty();

		cashCardCancelDeadlineNoticeUseCase.notifyDeadlines(THREE_DAYS_BEFORE.minusDays(1));
		assertThat(notices()).extracting(NotificationRequestedEvent::body).singleElement().asString()
			.startsWith("2027-10-08까지");
	}

	@Test
	@DisplayName("2028-02-29 승인분은 마지막 날이 2029-02-27이라 2029-02-24에 3일 전 알림을 보낸다")
	void leapDayApprovalIsFound() {
		Wallet wallet = createWallet(CUSTOMER_ID, 10_000);
		completedCharge(wallet, 10_000, "charge_leap", LocalDateTime.of(2028, 2, 29, 9, 0));

		cashCardCancelDeadlineNoticeUseCase.notifyDeadlines(LocalDateTime.of(2029, 2, 24, 9, 0));

		assertThat(notices()).extracting(NotificationRequestedEvent::body).singleElement().asString()
			.startsWith("2029-02-27까지");
	}

	private List<NotificationRequestedEvent> notices() {
		return applicationEvents.stream(NotificationRequestedEvent.class).toList();
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
