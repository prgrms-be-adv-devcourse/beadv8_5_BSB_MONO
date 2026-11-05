package com.bukang.payout.in;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.payout.app.PayoutLedgerUseCase;
import com.bukang.payout.app.PayoutPayStepUseCase;
import com.bukang.payout.app.PayoutPayUseCase;
import com.bukang.payout.app.PayoutRaceCancelUseCase;
import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutHold;
import com.bukang.payout.domain.PayoutHoldStatus;
import com.bukang.payout.domain.PayoutStage;
import com.bukang.payout.domain.PayoutStatus;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRecoveryRepository;
import com.bukang.payout.out.PayoutRepository;

import jakarta.persistence.EntityManager;

/**
 * dev 정산 샘플 데이터: 실행한 날 기준의 상대 날짜로 만들어, 언제 띄워도 지급 완료 · 지급 예정 · 보류 · 해제된 보류가 모두 보인다
 * 지급 예정일은 도메인 규칙(정산 월의 다음 달 20일, 주말이어도 그날. 해제는 max(원래 지급 예정일, 해제일 + 1일))이
 * 계산한 값이어야 한다
 */
@DataJpaTest
@ActiveProfiles("test")
class PayoutDataInitTest {
	private static final long SELLER_ID = 7;
	// 2026-10-28 수. 이번 달 지급일(10/20 화)이 지났으므로 최근 지급일의 달은 10월이다
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

	@Autowired
	private PayoutRepository payoutRepository;

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
	@DisplayName("지급 완료 대회: 석 달 전(7/12) 대회 · 마감(7/2) → 1차 · 최종 모두 다음 달 20일(8/20) 04:00에 지급 완료")
	void paidRace() {
		dataInit().makeSamplePayouts(TODAY);
		flushAndClear();

		for (PayoutStage stage : List.of(PayoutStage.일차_정산, PayoutStage.최종_정산)) {
			Payout payout = payout(1, stage);
			assertThat(payout.getStatus()).isEqualTo(PayoutStatus.지급_완료);
			assertThat(payout.getScheduledPayDate()).isEqualTo(LocalDate.of(2026, 8, 20));
			assertThat(payout.getPaidAt()).isEqualTo(LocalDate.of(2026, 8, 20).atTime(4, 0));
		}
	}

	@Test
	@DisplayName("해제된 보류: 계좌 미등록으로 자동 보류된 1차 · 최종(9/21 지급 예정)을 9/24에 풀면 해제일 + 1일(9/25)에 지급 완료")
	void releasedHolds() {
		dataInit().makeSamplePayouts(TODAY);
		flushAndClear();

		for (PayoutStage stage : List.of(PayoutStage.일차_정산, PayoutStage.최종_정산)) {
			Payout payout = payout(6, stage);
			assertThat(payout.getStatus()).isEqualTo(PayoutStatus.지급_완료);
			assertThat(payout.getScheduledPayDate()).isEqualTo(LocalDate.of(2026, 9, 25));
			assertThat(payout.getPaidAt()).isEqualTo(LocalDate.of(2026, 9, 25).atTime(4, 0));
		}
		assertThat(payoutHoldRepository.findAll())
			.filteredOn(hold -> hold.getStatus() == PayoutHoldStatus.해제)
			.hasSize(2);
	}

	@Test
	@DisplayName("지급 예정: 10/5 대회의 최종과 어제 환불 마감한 대회의 1차가 다음 달 20일(11/20) 지급 예정이다 (10/5 대회의 1차는 10/20에 지급 완료)")
	void scheduledPayouts() {
		dataInit().makeSamplePayouts(TODAY);
		flushAndClear();

		Payout nightRunFirst = payout(7, PayoutStage.일차_정산);
		assertThat(nightRunFirst.getStatus()).isEqualTo(PayoutStatus.지급_완료);
		assertThat(nightRunFirst.getPaidAt()).isEqualTo(LocalDate.of(2026, 10, 20).atTime(4, 0));
		Payout nightRunFinal = payout(7, PayoutStage.최종_정산);
		assertThat(nightRunFinal.getStatus()).isEqualTo(PayoutStatus.정산_예정);
		assertThat(nightRunFinal.getScheduledPayDate()).isEqualTo(LocalDate.of(2026, 11, 20));

		Payout forestFirst = payout(9, PayoutStage.일차_정산);
		assertThat(forestFirst.getStatus()).isEqualTo(PayoutStatus.정산_예정);
		assertThat(forestFirst.getScheduledPayDate()).isEqualTo(LocalDate.of(2026, 11, 20));
		// 대회 전이라 최종 정산 생성일이 오지 않았다
		assertThat(payoutRepository.existsBySellerIdAndRaceIdAndPayoutStage(SELLER_ID, 9, PayoutStage.최종_정산))
			.isFalse();
	}

	@Test
	@DisplayName("지급보류: 관리자가 지급 예정일 전에 최종을 보류해 지급보류로 남는다")
	void heldPayout() {
		dataInit().makeSamplePayouts(TODAY);
		flushAndClear();

		assertThat(payout(8, PayoutStage.일차_정산).getStatus()).isEqualTo(PayoutStatus.지급_완료);
		Payout held = payout(8, PayoutStage.최종_정산);
		assertThat(held.getStatus()).isEqualTo(PayoutStatus.지급보류);
		assertThat(payoutHoldRepository.findAll())
			.filteredOn(hold -> hold.getStatus() == PayoutHoldStatus.지급보류)
			.singleElement()
			.satisfies(hold -> assertThat(hold.getHeldAt()).isBefore(held.getScheduledPayDate().atStartOfDay()));
	}

	// 지급일이 20일로 모이므로 띄운 날이 지급일 전 · 지급일 당일 · 지급일 뒤 · 월초 · 20일이 주말인 달이어도 같아야 한다
	// 2026-12-20은 일요일이지만 12월 지급일은 그대로 12/20이다 (주말이어도 그날)
	@ParameterizedTest(name = "{0}에 띄움")
	@ValueSource(strings = {"2026-10-01", "2026-10-19", "2026-10-20", "2026-10-28", "2026-12-20", "2026-12-21"})
	@DisplayName("어느 날 띄워도 오늘 지급 배치가 지급 예정 샘플을 지급하지 않고, 지급 완료 샘플은 오늘 04:00까지 지급된 것이다")
	void todayPayBatchKeepsScheduled(LocalDate today) {
		dataInit().makeSamplePayouts(today);
		flushAndClear();

		new PayoutPayUseCase(new PayoutPayStepUseCase(payoutRepository, raceCancelUseCase()), transfer -> {
		}).run(today, today.atTime(4, 0));
		flushAndClear();

		assertThat(payout(7, PayoutStage.최종_정산).getStatus()).isEqualTo(PayoutStatus.정산_예정);
		assertThat(payout(9, PayoutStage.일차_정산).getStatus()).isEqualTo(PayoutStatus.정산_예정);
		assertThat(payout(8, PayoutStage.최종_정산).getStatus()).isEqualTo(PayoutStatus.지급보류);
		assertThat(payoutRepository.findAll())
			.filteredOn(payout -> payout.getStatus() == PayoutStatus.지급_완료)
			.hasSize(6)
			.allSatisfy(payout -> assertThat(payout.getPaidAt()).isBeforeOrEqualTo(today.atTime(4, 0)));
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
