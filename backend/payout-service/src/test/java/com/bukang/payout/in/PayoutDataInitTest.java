package com.bukang.payout.in;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.payout.app.PayoutPayStepUseCase;
import com.bukang.payout.app.PayoutPayUseCase;
import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutHold;
import com.bukang.payout.domain.PayoutHoldStatus;
import com.bukang.payout.domain.PayoutStage;
import com.bukang.payout.domain.PayoutStatus;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutRepository;

import jakarta.persistence.EntityManager;

/**
 * dev 정산 샘플 데이터: 실행한 날 기준의 상대 날짜로 만들어, 언제 띄워도 지급 완료 · 지급 예정 · 보류 · 해제된 보류가 모두 보인다
 * 지급 예정일은 도메인 규칙(기준일 + 2일, 해제는 max(원래 지급 예정일, 해제일 + 1일))이 계산한 값이어야 한다
 */
@DataJpaTest
@ActiveProfiles("test")
class PayoutDataInitTest {
	private static final long SELLER_ID = 7;
	// 2026-10-28 수. 어느 날이어도 같은 상대 날짜가 나와야 하므로 특별한 날을 고르지 않았다
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

	@Autowired
	private PayoutRepository payoutRepository;

	@Autowired
	private PayoutHoldRepository payoutHoldRepository;

	@Autowired
	private EntityManager entityManager;

	private PayoutDataInit dataInit() {
		return new PayoutDataInit(null, payoutRepository, payoutHoldRepository, new BusinessDayCalendar());
	}

	private Payout payout(long raceId, PayoutStage stage) {
		return payoutRepository.findFirstBySellerIdAndRaceIdAndPayoutStageOrderByIdAsc(SELLER_ID, raceId, stage)
			.orElseThrow();
	}

	private void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}

	@Test
	@DisplayName("지급 완료 대회: 1차는 마감일(대회 70일 전) + 2일, 최종은 대회일(60일 전) + 2일 04:00에 지급 완료")
	void paidRace() {
		dataInit().makeSamplePayouts(TODAY);
		flushAndClear();

		Payout first = payout(1, PayoutStage.FIRST);
		assertThat(first.getStatus()).isEqualTo(PayoutStatus.PAID);
		assertThat(first.getScheduledPayDate()).isEqualTo(TODAY.minusDays(68));
		assertThat(first.getPaidAt()).isEqualTo(TODAY.minusDays(68).atTime(4, 0));
		Payout last = payout(1, PayoutStage.FINAL);
		assertThat(last.getStatus()).isEqualTo(PayoutStatus.PAID);
		assertThat(last.getScheduledPayDate()).isEqualTo(TODAY.minusDays(58));
		assertThat(last.getPaidAt()).isEqualTo(TODAY.minusDays(58).atTime(4, 0));
	}

	@Test
	@DisplayName("해제된 보류: 계좌 미등록으로 자동 보류된 1차 · 최종을 30일 전에 풀면 해제일 + 1일에 지급 완료")
	void releasedHolds() {
		dataInit().makeSamplePayouts(TODAY);
		flushAndClear();

		for (PayoutStage stage : List.of(PayoutStage.FIRST, PayoutStage.FINAL)) {
			Payout payout = payout(6, stage);
			assertThat(payout.getStatus()).isEqualTo(PayoutStatus.PAID);
			assertThat(payout.getScheduledPayDate()).isEqualTo(TODAY.minusDays(29));
			assertThat(payout.getPaidAt()).isEqualTo(TODAY.minusDays(29).atTime(4, 0));
		}
		assertThat(payoutHoldRepository.findAll())
			.filteredOn(hold -> hold.getStatus() == PayoutHoldStatus.RELEASED)
			.hasSize(2);
	}

	@Test
	@DisplayName("지급 예정: 어제 열린 대회의 최종과 어제 환불 마감한 대회의 1차가 내일 지급 예정이다 (1차는 이미 지급 완료)")
	void scheduledPayouts() {
		dataInit().makeSamplePayouts(TODAY);
		flushAndClear();

		assertThat(payout(7, PayoutStage.FIRST).getStatus()).isEqualTo(PayoutStatus.PAID);
		Payout nightRunFinal = payout(7, PayoutStage.FINAL);
		assertThat(nightRunFinal.getStatus()).isEqualTo(PayoutStatus.CALCULATED);
		assertThat(nightRunFinal.getScheduledPayDate()).isEqualTo(TODAY.plusDays(1));

		Payout forestFirst = payout(9, PayoutStage.FIRST);
		assertThat(forestFirst.getStatus()).isEqualTo(PayoutStatus.CALCULATED);
		assertThat(forestFirst.getScheduledPayDate()).isEqualTo(TODAY.plusDays(1));
		// 대회 전이라 최종 정산 대상일이 오지 않았다
		assertThat(payoutRepository.existsBySellerIdAndRaceIdAndPayoutStage(SELLER_ID, 9, PayoutStage.FINAL))
			.isFalse();
	}

	@Test
	@DisplayName("지급보류: 관리자가 지급 예정일 전에 최종을 보류해 HELD로 남는다")
	void heldPayout() {
		dataInit().makeSamplePayouts(TODAY);
		flushAndClear();

		assertThat(payout(8, PayoutStage.FIRST).getStatus()).isEqualTo(PayoutStatus.PAID);
		Payout held = payout(8, PayoutStage.FINAL);
		assertThat(held.getStatus()).isEqualTo(PayoutStatus.HELD);
		assertThat(payoutHoldRepository.findAll())
			.filteredOn(hold -> hold.getStatus() == PayoutHoldStatus.HELD)
			.singleElement()
			.satisfies(hold -> assertThat(hold.getHeldAt()).isBefore(held.getScheduledPayDate().atStartOfDay()));
	}

	@Test
	@DisplayName("오늘 지급 배치가 돌아도 지급 예정 샘플은 지급되지 않는다 (띄운 직후 화면에서 지급 예정이 보인다)")
	void todayPayBatchKeepsScheduled() {
		dataInit().makeSamplePayouts(TODAY);
		flushAndClear();

		new PayoutPayUseCase(new PayoutPayStepUseCase(payoutRepository), transfer -> {
		}).run(TODAY, TODAY.atTime(4, 0));
		flushAndClear();

		assertThat(payout(7, PayoutStage.FINAL).getStatus()).isEqualTo(PayoutStatus.CALCULATED);
		assertThat(payout(9, PayoutStage.FIRST).getStatus()).isEqualTo(PayoutStatus.CALCULATED);
	}

	@Test
	@DisplayName("다음 날 다시 띄워도(정산 월이 달라져도) 판매자 · 대회 기준으로 이미 만든 것을 알아 다시 만들지 않는다")
	void runTwiceOnDifferentDays() {
		dataInit().makeSamplePayouts(TODAY);
		flushAndClear();
		long payouts = payoutRepository.count();
		long holds = payoutHoldRepository.count();

		dataInit().makeSamplePayouts(TODAY.plusMonths(1));
		flushAndClear();

		assertThat(payoutRepository.count()).isEqualTo(payouts);
		assertThat(payoutHoldRepository.count()).isEqualTo(holds);
		assertThat(payoutHoldRepository.findAll()).extracting(PayoutHold::getHoldCode).doesNotContainNull();
	}
}
