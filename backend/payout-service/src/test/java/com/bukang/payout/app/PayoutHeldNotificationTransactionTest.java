package com.bukang.payout.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.common.global.eventpublisher.EventPublisher;
import com.bukang.common.shared.notification.NotificationKafkaSender;
import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutHoldReason;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.out.PayoutStatusHistoryRepository;
import com.bukang.payout.out.RaceSellerPort;

/**
 * 정산 보류 알림이 보류 트랜잭션이 커밋된 뒤에만 나가는지 확인한다 (PR 570 리뷰)
 * 상품 서비스를 보류 트랜잭션(정산 내역 잠금) 안에서 부르지 않아야 하므로, 테스트를 감싸는 트랜잭션을 끄고 진짜 커밋 · 롤백을 본다
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({PayoutHoldUseCase.class, PayoutHeldNotifier.class, EventPublisher.class, BusinessDayCalendar.class,
	PayoutHeldNotificationTransactionTest.CountingRaceSeller.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PayoutHeldNotificationTransactionTest {
	private static final LocalDateTime HELD_AT = LocalDateTime.of(2026, 10, 12, 10, 0);
	private static final AtomicInteger SELLER_LOOKUPS = new AtomicInteger();

	@MockitoBean
	private NotificationKafkaSender notificationKafkaSender;

	@Autowired
	private PayoutHoldUseCase holdUseCase;

	@Autowired
	private PayoutRepository payoutRepository;

	@Autowired
	private PayoutHoldRepository payoutHoldRepository;

	@Autowired
	private PayoutStatusHistoryRepository payoutStatusHistoryRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@TestConfiguration
	static class CountingRaceSeller {
		@Bean
		RaceSellerPort raceSellerPort() {
			return raceId -> {
				SELLER_LOOKUPS.incrementAndGet();
				return Optional.of(500L);
			};
		}
	}

	@BeforeEach
	void resetLookups() {
		SELLER_LOOKUPS.set(0);
	}

	@AfterEach
	void cleanUp() {
		payoutHoldRepository.deleteAllInBatch();
		payoutStatusHistoryRepository.deleteAllInBatch();
		payoutRepository.deleteAllInBatch();
	}

	private long savePayout() {
		return payoutRepository.save(Payout.finalStage(1, 10, "테스트 대회", LocalDate.of(2026, 10, 17),
			LocalDateTime.of(2026, 10, 18, 3, 0), new BusinessDayCalendar())).getId();
	}

	@Test
	@DisplayName("보류가 커밋되면 그 뒤에 판매자를 찾아 알림을 한 번 보낸다")
	void notifiesOnceAfterCommit() {
		long payoutId = savePayout();

		holdUseCase.hold(payoutId, PayoutHoldReason.정산_계좌_미등록, null, 99, HELD_AT);

		assertThat(SELLER_LOOKUPS).hasValue(1);
		verify(notificationKafkaSender, times(1)).send(any());
	}

	@Test
	@DisplayName("보류 트랜잭션이 롤백되면 상품 서비스를 부르지 않고 알림도 보내지 않는다")
	void rollbackSendsNothing() {
		long payoutId = savePayout();
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);

		assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
			holdUseCase.hold(payoutId, PayoutHoldReason.정산_계좌_미등록, null, 99, HELD_AT);
			throw new IllegalStateException("보류 뒤 다른 일이 실패");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(SELLER_LOOKUPS).hasValue(0);
		verify(notificationKafkaSender, never()).send(any());
		assertThat(payoutHoldRepository.count()).isZero();
	}
}
