package com.bukang.product.app;

import static com.bukang.product.app.RaceScheduleQueryUseCase.PAYOUT_TARGET_STATUSES;
import static com.bukang.product.domain.RaceSaleStatus.CANCELED;
import static com.bukang.product.domain.RaceSaleStatus.CLOSED;
import static com.bukang.product.domain.RaceSaleStatus.ON_SALE;
import static com.bukang.product.domain.RaceSaleStatus.SCHEDULED;
import static com.bukang.product.domain.RaceSaleStatus.STOPPED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.product.domain.exception.RacePeriodException;
import com.bukang.product.out.RaceRepository;

/**
 * 정산용 대상 대회 조회(RaceScheduleQueryUseCase)의 기간 검증과 대회 일시 범위를 확인한다 (PRO-230).
 * DB · ES 없이 리포지토리를 가짜로 바꿔, Docker가 없어도 돈다. 실제 조회 결과는 InternalV1RaceScheduleTest가 본다.
 */
class RaceScheduleQueryUseCaseTest {
	private static final LocalDate FROM = LocalDate.of(2026, 11, 1);

	private final RaceRepository raceRepository = mock(RaceRepository.class);
	private final RaceScheduleQueryUseCase useCase = new RaceScheduleQueryUseCase(raceRepository);

	@Test
	@DisplayName("정산 대상 판매 상태는 대회 취소(CANCELED)만 빼고 판매 예정 · 판매 중 · 판매 중지 · 판매 종료다")
	void payoutTargetStatusesExcludeOnlyCanceled() {
		assertThat(PAYOUT_TARGET_STATUSES)
			.containsExactlyInAnyOrder(SCHEDULED, ON_SALE, STOPPED, CLOSED)
			.doesNotContain(CANCELED);
	}

	@Test
	@DisplayName("시작일과 종료일이 같은 하루 기간은 조회한다")
	void sameDayPeriod() {
		assertThat(useCase.findByRefundDeadline(FROM, FROM)).isEmpty();

		verify(raceRepository).findPayoutTargetsByRefundDeadline(FROM, FROM, PAYOUT_TARGET_STATUSES);
	}

	@Test
	@DisplayName("31일(11/1 ~ 12/1)은 조회하고, 32일(11/1 ~ 12/2)은 리포지토리를 부르지 않고 RacePeriodException이다")
	void maxPeriodIs31Days() {
		LocalDate day31 = FROM.plusDays(30);
		assertThat(useCase.findByRefundDeadline(FROM, day31)).isEmpty();
		verify(raceRepository).findPayoutTargetsByRefundDeadline(FROM, day31, PAYOUT_TARGET_STATUSES);

		RaceRepository untouched = mock(RaceRepository.class);
		RaceScheduleQueryUseCase guarded = new RaceScheduleQueryUseCase(untouched);
		assertThatThrownBy(() -> guarded.findByRaceDate(FROM, FROM.plusDays(31)))
			.isInstanceOf(RacePeriodException.class)
			.hasMessage("대회는 한 번에 31일까지 조회할 수 있습니다.");
		verifyNoInteractions(untouched);
	}

	@Test
	@DisplayName("시작일이 종료일보다 늦으면 리포지토리를 부르지 않고 RacePeriodException이다")
	void fromAfterTo() {
		assertThatThrownBy(() -> useCase.findByRefundDeadline(FROM.plusDays(1), FROM))
			.isInstanceOf(RacePeriodException.class)
			.hasMessage("조회 시작일이 종료일보다 늦습니다.");
		verifyNoInteractions(raceRepository);
	}

	@Test
	@DisplayName("대회 날짜 11/1 ~ 11/7은 대회 일시 [11/1 0시, 11/8 0시)로 묻는다 (끝 날 하루 전체를 넣는다)")
	void raceDatePeriodCoversWholeLastDay() {
		useCase.findByRaceDate(FROM, FROM.plusDays(6));

		verify(raceRepository).findPayoutTargetsByRaceDate(FROM.atStartOfDay(), FROM.plusDays(7).atStartOfDay(),
			PAYOUT_TARGET_STATUSES);
	}
}
