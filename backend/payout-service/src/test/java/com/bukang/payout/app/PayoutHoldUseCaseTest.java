package com.bukang.payout.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.common.global.eventpublisher.EventPublisher;
import com.bukang.common.shared.notification.NotificationKind;
import com.bukang.common.shared.notification.event.NotificationRequestedEvent;
import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutHold;
import com.bukang.payout.domain.PayoutHoldReason;
import com.bukang.payout.domain.PayoutHoldStatus;
import com.bukang.payout.domain.PayoutStatus;
import com.bukang.payout.domain.exception.PayoutHoldNotFoundException;
import com.bukang.payout.domain.exception.PayoutNotFoundException;
import com.bukang.payout.dto.PayoutHoldDto;
import com.bukang.payout.out.PayoutCreateFailureRepository;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRecoveryRepository;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.out.RaceSellerPort;

import jakarta.persistence.EntityManager;

@DataJpaTest
@ActiveProfiles("test")
class PayoutHoldUseCaseTest {
	private static final long ADMIN_ID = 99;
	private static final LocalDateTime HELD_AT = LocalDateTime.of(2026, 10, 12, 10, 0);
	// saveFinalPayout의 대회(10) 판매자의 회원 ID. 판매자 ID(1)와 다른 값이다
	private static final long SELLER_MEMBER_ID = 500;

	// 보류가 보낸 알림 요청을 모은다
	private final List<NotificationRequestedEvent> notifications = new ArrayList<>();
	private RaceSellerPort raceSellerPort = raceId -> raceId == 10 ? Optional.of(SELLER_MEMBER_ID) : Optional.empty();

	@Autowired
	private PayoutRepository payoutRepository;

	@Autowired
	private PayoutCreateFailureRepository payoutCreateFailureRepository;

	@Autowired
	private PayoutHoldRepository payoutHoldRepository;

	@Autowired
	private PayoutItemRepository payoutItemRepository;

	@Autowired
	private PayoutRecoveryRepository payoutRecoveryRepository;

	@Autowired
	private EntityManager entityManager;

	// 대회 취소를 표시한 지급 중이 없으면 부르지 않는다. 지급 단계를 만들려고 실제 저장소로 묶어 둔다
	private PayoutRaceCancelUseCase raceCancelUseCase() {
		return new PayoutRaceCancelUseCase(payoutRepository,
			new PayoutLedgerUseCase(payoutRepository, payoutItemRepository, payoutRecoveryRepository));
	}

	private PayoutHoldUseCase useCase() {
		return new PayoutHoldUseCase(payoutRepository, payoutHoldRepository, new BusinessDayCalendar(),
			raceSellerPort, new EventPublisher(event -> {
				if (event instanceof NotificationRequestedEvent notification) {
					notifications.add(notification);
				}
			}));
	}

	private PayoutPayUseCase payUseCase() {
		return new PayoutPayUseCase(new PayoutPayStepUseCase(payoutRepository, raceCancelUseCase(),
				payoutCreateFailureRepository), transfer -> {
		});
	}

	// 대회일 10/17 토: 지급 예정일 10/19 월
	private Payout saveFinalPayout() {
		return saveFinalPayout(1);
	}

	// 같은 대회(10)에 판매자 ID만 다른 정산 내역. 판매자 · 대회 · 정산 월 · 단계가 같으면 두 번 저장할 수 없다
	private Payout saveFinalPayout(long sellerId) {
		return payoutRepository.save(Payout.finalStage(sellerId, 10, "테스트 대회", LocalDate.of(2026, 10, 17),
			LocalDateTime.of(2026, 10, 18, 3, 0), new BusinessDayCalendar()));
	}

	private PayoutHoldDto holdOf(Payout payout) {
		return useCase().hold(payout.getId(), PayoutHoldReason.정산_계좌_미등록, "계좌 확인 중", ADMIN_ID, HELD_AT);
	}

	private Payout reload(Payout payout) {
		entityManager.flush();
		entityManager.clear();
		return payoutRepository.findById(payout.getId()).orElseThrow();
	}

	@Test
	@DisplayName("보류를 걸면 그 대회 판매자의 회원에게 정산 보류 알림을 한 번 보낸다")
	void holdNotifiesSeller() {
		Payout payout = saveFinalPayout();

		holdOf(payout);

		assertThat(notifications).singleElement().satisfies(notification -> {
			assertThat(notification.memberId()).isEqualTo(SELLER_MEMBER_ID);
			assertThat(notification.kind()).isEqualTo(NotificationKind.PAYOUT_HELD);
			assertThat(notification.body()).contains("테스트 대회", "정산 계좌 미등록");
			assertThat(notification.href()).isEqualTo("/seller/settlements");
		});
	}

	@Test
	@DisplayName("같은 사유로 이미 보류 중이면 대사 보류를 다시 걸지 않고 알림도 보내지 않는다")
	void alreadyHeldSendsNoNotification() {
		Payout payout = saveFinalPayout();
		assertThat(useCase().holdForReconcileMismatch(payout, HELD_AT)).isTrue();

		assertThat(useCase().holdForReconcileMismatch(payout, HELD_AT)).isFalse();

		assertThat(notifications).hasSize(1);
	}

	@Test
	@DisplayName("정산 계좌가 있으면 자동 보류를 걸지 않아 알림도 없고, 없으면 자동 보류와 알림이 한 번 간다")
	void autoHoldNotifiesOnlyWhenHeld() {
		Payout withAccount = saveFinalPayout();
		useCase().holdNew(withAccount, true, HELD_AT);
		assertThat(notifications).isEmpty();

		Payout withoutAccount = saveFinalPayout(2);
		useCase().holdNew(withoutAccount, false, HELD_AT);
		assertThat(notifications).singleElement()
			.satisfies(notification -> assertThat(notification.memberId()).isEqualTo(SELLER_MEMBER_ID));
	}

	@Test
	@DisplayName("판매자의 회원을 찾지 못하거나 상품 서비스가 실패해도 보류는 걸리고, 알림만 빠진다")
	void lookupFailureKeepsHold() {
		raceSellerPort = raceId -> {
			throw new IllegalStateException("상품 서비스 응답 없음");
		};
		Payout down = saveFinalPayout();
		holdOf(down);
		assertThat(reload(down).getStatus()).isEqualTo(PayoutStatus.지급보류);

		raceSellerPort = raceId -> Optional.empty();
		Payout missing = saveFinalPayout(2);
		holdOf(missing);
		assertThat(reload(missing).getStatus()).isEqualTo(PayoutStatus.지급보류);

		assertThat(notifications).isEmpty();
	}

	@Test
	@DisplayName("보류를 걸면 정산 내역이 지급보류가 되고, 지급보류 ID가 HD-보류한 날-16진수 4자리로 붙는다")
	void hold() {
		Payout payout = saveFinalPayout();

		PayoutHoldDto hold = holdOf(payout);

		assertThat(hold.getHoldCode()).isEqualTo("HD-20261012-%04X".formatted(hold.getId() & 0xFFFF));
		assertThat(hold.getPayoutId()).isEqualTo(payout.getId());
		assertThat(hold.getStatus()).isEqualTo(PayoutHoldStatus.지급보류);
		assertThat(hold.getHeldBy()).isEqualTo(ADMIN_ID);
		assertThat(reload(payout).getStatus()).isEqualTo(PayoutStatus.지급보류);
		assertThat(payoutHoldRepository.findById(hold.getId()).orElseThrow().getHoldCode())
			.isEqualTo(hold.getHoldCode());
	}

	@Test
	@DisplayName("보류 중인 최종 정산 내역은 지급일 11/20 배치에서 지급되지 않고, 11/23에 풀면 다음 날 11/24 배치에서 지급된다")
	void releasedPayoutIsPaidOnNextPayDate() {
		Payout payout = saveFinalPayout();
		PayoutHoldDto hold = holdOf(payout);

		payUseCase().run(LocalDate.of(2026, 11, 20), LocalDateTime.of(2026, 11, 20, 4, 0));
		assertThat(reload(payout).getStatus()).isEqualTo(PayoutStatus.지급보류);

		// 보류를 풀면 해제일 다음 날에 지급한다. 다음 달 20일까지 기다리지 않는다 (11/23 월 → 11/24 화)
		PayoutHoldDto released = useCase().release(hold.getId(), 77, LocalDateTime.of(2026, 11, 23, 9, 0));
		assertThat(released.getStatus()).isEqualTo(PayoutHoldStatus.해제);
		assertThat(released.getReleasedBy()).isEqualTo(77);
		assertThat(reload(payout).getScheduledPayDate()).isEqualTo(LocalDate.of(2026, 11, 24));

		payUseCase().run(LocalDate.of(2026, 11, 23), LocalDateTime.of(2026, 11, 23, 4, 0));
		assertThat(reload(payout).getStatus()).isEqualTo(PayoutStatus.지급_예정);

		payUseCase().run(LocalDate.of(2026, 11, 24), LocalDateTime.of(2026, 11, 24, 4, 0));
		assertThat(reload(payout).getStatus()).isEqualTo(PayoutStatus.지급_완료);
	}

	@Test
	@DisplayName("정산 내역을 미리 읽지 않고 보류만 읽어 풀어도(hold.getPayout()이 프록시) 풀린다")
	void releaseWithLazyPayoutProxy() {
		Payout payout = saveFinalPayout();
		PayoutHoldDto hold = holdOf(payout);
		// 실제 요청처럼 영속성 컨텍스트를 비워, 보류를 읽을 때 정산 내역이 프록시로 들어오게 한다
		entityManager.flush();
		entityManager.clear();

		PayoutHoldDto released = useCase().release(hold.getId(), ADMIN_ID, LocalDateTime.of(2026, 10, 15, 9, 0));

		assertThat(released.getStatus()).isEqualTo(PayoutHoldStatus.해제);
		assertThat(reload(payout).getStatus()).isEqualTo(PayoutStatus.지급_예정);
	}

	// 풀기 API는 보류를 읽고 hold.getPayout().release를 부른다. 이때 정산 내역의 보류 목록이 처음 지연 로딩되므로
	// 방금 푼 보류가 목록에서도 풀린 객체로 보여야 남은 보류를 바르게 센다 (PR #330 리뷰)
	@Test
	@DisplayName("보류 두 개 중 하나를 풀기 API로 풀면 지급보류 그대로이고, 나머지를 풀면 지급 예정으로 돌아간다 (BUK-67)")
	void releaseOneOfTwoHolds() {
		Payout payout = saveFinalPayout();
		PayoutHoldDto accountHold = holdOf(payout);
		PayoutHoldDto mismatchHold = useCase().hold(payout.getId(), PayoutHoldReason.대사_불일치, null,
			PayoutHold.SYSTEM_MEMBER_ID, HELD_AT);
		entityManager.flush();
		entityManager.clear();

		useCase().release(accountHold.getId(), ADMIN_ID, LocalDateTime.of(2026, 11, 23, 9, 0));

		Payout stillHeld = reload(payout);
		assertThat(stillHeld.getStatus()).isEqualTo(PayoutStatus.지급보류);
		assertThat(stillHeld.getScheduledPayDate()).isEqualTo(LocalDate.of(2026, 11, 20));
		entityManager.clear();

		useCase().release(mismatchHold.getId(), ADMIN_ID, LocalDateTime.of(2026, 11, 25, 9, 0));

		Payout released = reload(payout);
		assertThat(released.getStatus()).isEqualTo(PayoutStatus.지급_예정);
		assertThat(released.getScheduledPayDate()).isEqualTo(LocalDate.of(2026, 11, 26));
	}

	@Test
	@DisplayName("다른 정산 내역의 보류로는 풀 수 없다 (ID로 비교)")
	void cannotReleaseWithOtherPayoutsHold() {
		Payout payout = saveFinalPayout();
		Payout other = payoutRepository.save(Payout.finalStage(2, 10, "테스트 대회", LocalDate.of(2026, 10, 17),
			LocalDateTime.of(2026, 10, 18, 3, 0), new BusinessDayCalendar()));
		holdOf(payout);
		PayoutHoldDto otherHold = holdOf(other);
		entityManager.flush();
		entityManager.clear();

		Payout loaded = payoutRepository.findById(payout.getId()).orElseThrow();
		PayoutHold otherHoldEntity = payoutHoldRepository.findById(otherHold.getId()).orElseThrow();

		assertThatThrownBy(() -> loaded.release(otherHoldEntity, ADMIN_ID, LocalDateTime.of(2026, 10, 15, 9, 0),
			new BusinessDayCalendar()))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(loaded.getStatus()).isEqualTo(PayoutStatus.지급보류);
		assertThat(otherHoldEntity.getStatus()).isEqualTo(PayoutHoldStatus.지급보류);
	}

	@Test
	@DisplayName("없는 정산 내역에 보류를 걸거나 없는 보류를 풀면 찾을 수 없다는 예외가 난다")
	void notFound() {
		assertThatThrownBy(() -> useCase().hold(9999, PayoutHoldReason.대회_취소_처리, null, ADMIN_ID, HELD_AT))
			.isInstanceOf(PayoutNotFoundException.class);
		assertThatThrownBy(() -> useCase().release(9999, ADMIN_ID, HELD_AT))
			.isInstanceOf(PayoutHoldNotFoundException.class);
	}

	@Test
	@DisplayName("지급 배치가 읽은 뒤 보류가 걸리면, 배치가 그 정산 내역을 지급_완료로 덮어쓰지 못한다 (@Version)")
	void staleBatchCannotOverwriteHold() {
		Payout payout = saveFinalPayout();
		entityManager.flush();
		entityManager.clear();

		// 지급 배치가 CALCULATED(version 0)로 읽어 둔 상태를 흉내 낸다
		Payout readByBatch = payoutRepository.findById(payout.getId()).orElseThrow();
		// 상태를 바꾸면 이력 목록(LAZY)에 한 줄을 더하므로, 세션이 있을 때 미리 읽어 둔다 (운영의 지급 배치는 트랜잭션 안이다)
		readByBatch.getStatusHistories().size();
		entityManager.detach(readByBatch);

		// 그 사이 관리자가 보류를 건다 (version 1)
		holdOf(payout);
		entityManager.flush();
		entityManager.clear();

		readByBatch.pay(LocalDateTime.of(2026, 11, 20, 4, 0));
		assertThatThrownBy(() -> payoutRepository.saveAndFlush(readByBatch))
			.isInstanceOf(ObjectOptimisticLockingFailureException.class);
	}
}
