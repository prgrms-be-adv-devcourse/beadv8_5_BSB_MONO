package com.bukang.payout.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutHoldReason;
import com.bukang.payout.out.PayoutRepository;

/**
 * 판매자 미수령 정산금 조회 (회원 탈퇴 제한). 지급 완료 · 취소를 뺀 모든 상태를 미수령으로 센다
 */
@DataJpaTest
@ActiveProfiles("test")
class PayoutUnpaidQueryUseCaseTest {
	private static final LocalDate DEADLINE = LocalDate.of(2026, 11, 9);
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 3, 0);
	private static final BusinessDayCalendar CALENDAR = new BusinessDayCalendar();

	@Autowired
	private PayoutRepository payoutRepository;

	private PayoutUnpaidQueryUseCase useCase;

	@BeforeEach
	void setUp() {
		useCase = new PayoutUnpaidQueryUseCase(payoutRepository);
	}

	// 판매자마다 상태가 겹치지 않게 sellerId를 다르게 둔다 (seller_id · race_id · month · stage 유일 제약)
	private Payout scheduled(long sellerId) {
		return Payout.first(sellerId, 10, "대회 10", DEADLINE, NOW, CALENDAR);
	}

	@Test
	@DisplayName("지급 예정 정산이 있으면 미수령이다")
	void scheduledIsUnpaid() {
		payoutRepository.save(scheduled(1));

		assertThat(useCase.findUnpaid(1).isHasUnpaid()).isTrue();
	}

	@Test
	@DisplayName("지급보류 정산이 있으면 미수령이다")
	void heldIsUnpaid() {
		Payout payout = scheduled(2);
		payout.hold(PayoutHoldReason.정산_계좌_미등록, "계좌 미등록", 1, NOW);
		payoutRepository.save(payout);

		assertThat(useCase.findUnpaid(2).isHasUnpaid()).isTrue();
	}

	@Test
	@DisplayName("지급 중 정산이 있으면 미수령이다")
	void payingIsUnpaid() {
		Payout payout = scheduled(3);
		payout.startPaying(NOW);
		payoutRepository.save(payout);

		assertThat(useCase.findUnpaid(3).isHasUnpaid()).isTrue();
	}

	@Test
	@DisplayName("지급 완료만 있으면 미수령이 아니다")
	void paidIsNotUnpaid() {
		Payout payout = scheduled(4);
		payout.pay(NOW);
		payoutRepository.save(payout);

		assertThat(useCase.findUnpaid(4).isHasUnpaid()).isFalse();
	}

	@Test
	@DisplayName("취소만 있으면 미수령이 아니다")
	void canceledIsNotUnpaid() {
		Payout payout = scheduled(5);
		payout.cancel(1, NOW, "대회 취소");
		payoutRepository.save(payout);

		assertThat(useCase.findUnpaid(5).isHasUnpaid()).isFalse();
	}

	@Test
	@DisplayName("정산 내역이 없으면 미수령이 아니다")
	void noneIsNotUnpaid() {
		assertThat(useCase.findUnpaid(99).isHasUnpaid()).isFalse();
	}
}
