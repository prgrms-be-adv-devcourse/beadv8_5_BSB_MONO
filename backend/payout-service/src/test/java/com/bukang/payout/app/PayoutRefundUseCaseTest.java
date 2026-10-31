package com.bukang.payout.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.common.shared.order.event.OrderItemRefundedEvent;
import com.bukang.payout.app.PayoutCreateStepUseCase.CourseSale;
import com.bukang.payout.app.PayoutCreateStepUseCase.FirstPayoutSource;
import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.PayoutRecovery;
import com.bukang.payout.domain.PayoutRecoveryStatus;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRecoveryRepository;
import com.bukang.payout.out.PayoutRepository;

import jakarta.persistence.EntityManager;

/**
 * 참가권 환불 알림을 받아 회수를 남긴다 (선 환불 후 회수 R1, AC-RECOV)
 * 대회 7, 판매자 1, 참가권 5만 원 × 10장. 1장의 1차 몫 20,000원
 */
@DataJpaTest
@ActiveProfiles("test")
class PayoutRefundUseCaseTest {
	private static final long SELLER_ID = 1;
	private static final long RACE_ID = 7;
	private static final long PRICE = 50_000L;
	private static final LocalDate REFUND_DEADLINE = LocalDate.of(2026, 10, 31);
	private static final LocalDateTime REFUNDED_AT = LocalDateTime.of(2026, 11, 26, 10, 0);

	@Autowired
	private PayoutRepository payoutRepository;

	@Autowired
	private PayoutItemRepository payoutItemRepository;

	@Autowired
	private PayoutHoldRepository payoutHoldRepository;

	@Autowired
	private PayoutRecoveryRepository payoutRecoveryRepository;

	@Autowired
	private EntityManager entityManager;

	private PayoutCreateStepUseCase creator;
	private PayoutRefundUseCase refundUseCase;

	@BeforeEach
	void setUp() {
		BusinessDayCalendar calendar = new BusinessDayCalendar();
		PayoutLedgerUseCase ledgerUseCase = new PayoutLedgerUseCase(payoutRepository, payoutItemRepository,
			payoutRecoveryRepository);
		creator = new PayoutCreateStepUseCase(payoutRepository, calendar, ledgerUseCase,
			new PayoutHoldUseCase(payoutRepository, payoutHoldRepository, calendar));
		refundUseCase = new PayoutRefundUseCase(payoutRepository, ledgerUseCase);
	}

	private void createFirst() {
		creator.createFirst(new FirstPayoutSource(SELLER_ID, RACE_ID, "대회 A", true,
			List.of(new CourseSale(RACE_ID, PRICE * 10, 10))), REFUND_DEADLINE,
			REFUND_DEADLINE.plusDays(1).atTime(3, 0));
	}

	private OrderItemRefundedEvent refunded(long orderItemId, long price) {
		return new OrderItemRefundedEvent(orderItemId, RACE_ID, RACE_ID, price, REFUNDED_AT);
	}

	private List<PayoutRecovery> recoveries() {
		entityManager.flush();
		entityManager.clear();
		return payoutRecoveryRepository.findAll();
	}

	@Test
	@DisplayName("TC-RECOV-1-1 1차 정산이 있는 대회의 참가권이 환불되면 그 참가권의 회수가 20,000원으로 하나 생긴다")
	void refundCreatesRecovery() {
		createFirst();

		refundUseCase.recordRefund(refunded(108, PRICE), REFUNDED_AT);

		assertThat(recoveries())
			.extracting(PayoutRecovery::getOrderItemId, PayoutRecovery::getAmount, PayoutRecovery::getSellerId)
			.containsExactly(tuple(108L, 20_000L, SELLER_ID));
	}

	@Test
	@DisplayName("TC-RECOV-1-2 가격 50,001원 참가권이 환불되면 회수는 원 미만을 버린 20,000원이다")
	void refundAmountRoundsDown() {
		createFirst();

		refundUseCase.recordRefund(refunded(108, 50_001L), REFUNDED_AT);

		assertThat(recoveries()).extracting(PayoutRecovery::getAmount).containsExactly(20_000L);
	}

	@Test
	@DisplayName("TC-RECOV-1-3 가격 33,334원 참가권 3장이 환불되면 장마다 버린 뒤 더해 회수 합은 39,999원이다")
	void refundAmountsSumAfterRoundingEachTicket() {
		createFirst();

		for (long orderItemId = 101; orderItemId <= 103; orderItemId++) {
			refundUseCase.recordRefund(refunded(orderItemId, 33_334L), REFUNDED_AT);
		}

		assertThat(recoveries()).extracting(PayoutRecovery::getAmount).containsExactly(13_333L, 13_333L, 13_333L);
	}

	@Test
	@DisplayName("TC-RECOV-2-1 같은 참가권 환불 알림이 다시 와도 오류 없이 회수는 하나다")
	void sameRefundTwiceKeepsOneRecovery() {
		createFirst();

		refundUseCase.recordRefund(refunded(108, PRICE), REFUNDED_AT);
		refundUseCase.recordRefund(refunded(108, PRICE), REFUNDED_AT.plusMinutes(1));

		assertThat(recoveries()).hasSize(1)
			.extracting(PayoutRecovery::getStatus).containsExactly(PayoutRecoveryStatus.COMPLETED);
	}

	@Test
	@DisplayName("TC-RECOV-3-1 1차 정산이 없는 대회의 환불 알림은 회수와 원장 줄을 남기지 않는다")
	void refundWithoutFirstIsIgnored() {
		refundUseCase.recordRefund(refunded(108, PRICE), REFUNDED_AT);

		assertThat(recoveries()).isEmpty();
		assertThat(payoutItemRepository.findAll()).isEmpty();
	}
}
