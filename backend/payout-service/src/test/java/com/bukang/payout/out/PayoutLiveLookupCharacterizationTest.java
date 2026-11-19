package com.bukang.payout.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutStage;
import com.bukang.payout.domain.PayoutStatus;

/**
 * CRU-10 특성 테스트: 리팩터링 전에 지금 동작을 못박는다.
 * (1) "대회 취소로 닫히지 않은 그 단계의 정산" 조회 — 6곳이 쓰는 findLive 기준.
 * (2) 판매자 요약(findPayableForSummary)의 "1차를 기다리는 최종" 제외 기준.
 * 지급 배치 쪽(findWaitingFirst)의 같은 기준은 PayoutPayUseCaseTest(1차 보류·없음·다른 대회)가 이미 덮는다.
 */
@DataJpaTest
@ActiveProfiles("test")
class PayoutLiveLookupCharacterizationTest {
	private static final BusinessDayCalendar CALENDAR = new BusinessDayCalendar();
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 3, 0);
	private static final LocalDate RACE_DATE = LocalDate.of(2026, 10, 17);
	private static final LocalDate REFUND_DEADLINE = LocalDate.of(2026, 10, 11);

	@Autowired
	private PayoutRepository payoutRepository;

	// 살아 있는 정산 조회(취소 제외)의 한곳 (CRU-10). 기대값은 리팩터링 전후 그대로다
	private Optional<Payout> live(long sellerId, long raceId, PayoutStage stage) {
		return payoutRepository.findLiveStage(sellerId, raceId, stage);
	}

	private Payout first(long raceId) {
		return payoutRepository.saveAndFlush(
			Payout.first(1, raceId, "테스트 대회", REFUND_DEADLINE, NOW, CALENDAR));
	}

	private Payout finalStage(long raceId) {
		return payoutRepository.saveAndFlush(
			Payout.finalStage(1, raceId, "테스트 대회", RACE_DATE, NOW, CALENDAR));
	}

	@Nested
	@DisplayName("살아 있는 정산 조회 (findLive 기준)")
	class LiveLookup {
		@Test
		@DisplayName("취소가 아닌 그 단계의 정산을 돌려준다")
		void returnsLivePayout() {
			Payout payout = first(10);

			assertThat(live(1, 10, PayoutStage.일차_정산)).map(Payout::getId).contains(payout.getId());
		}

		@Test
		@DisplayName("그 단계의 정산이 취소면 빈 값이다")
		void excludesCanceled() {
			Payout payout = first(11);
			payout.cancel(0, NOW, "대회 취소");
			payoutRepository.saveAndFlush(payout);

			assertThat(live(1, 11, PayoutStage.일차_정산)).isEmpty();
		}

		@Test
		@DisplayName("최종이 먼저 생겨 ID가 작아도 1차 조회는 1차만 돌려준다 (단계로 나뉜다)")
		void separatesByStage() {
			Payout finalPayout = finalStage(12);
			Payout firstPayout = first(12);

			assertThat(live(1, 12, PayoutStage.일차_정산)).map(Payout::getId).contains(firstPayout.getId());
			assertThat(live(1, 12, PayoutStage.최종_정산)).map(Payout::getId).contains(finalPayout.getId());
		}

		@Test
		@DisplayName("그 단계의 정산이 없으면 빈 값이다")
		void emptyWhenAbsent() {
			assertThat(live(1, 13, PayoutStage.최종_정산)).isEmpty();
		}
	}

	@Nested
	@DisplayName("판매자 요약: 1차를 기다리는 최종 제외 (findPayableForSummary)")
	class PayableForSummary {
		private java.util.List<Payout> summary() {
			return payoutRepository.findPayableForSummary(1, PayoutStatus.지급_예정,
				PayoutStage.최종_정산, PayoutStage.일차_정산, PayoutStatus.지급_완료, PayoutStatus.취소);
		}

		@Test
		@DisplayName("1차가 지급 완료가 아니면(지급 예정) 그 최종은 빠진다")
		void excludesFinalWaitingOnUnpaidFirst() {
			first(20);
			Payout finalPayout = finalStage(20);

			assertThat(summary()).extracting(Payout::getId).doesNotContain(finalPayout.getId());
		}

		@Test
		@DisplayName("1차가 지급 완료면 그 최종은 들어간다")
		void includesFinalWhenFirstPaid() {
			Payout firstPayout = first(21);
			firstPayout.pay(NOW);
			payoutRepository.saveAndFlush(firstPayout);
			Payout finalPayout = finalStage(21);

			assertThat(summary()).extracting(Payout::getId).contains(finalPayout.getId());
		}

		@Test
		@DisplayName("1차가 취소면(살아 있지 않음) 그 최종은 기다리지 않고 들어간다")
		void includesFinalWhenFirstCanceled() {
			Payout firstPayout = first(22);
			firstPayout.cancel(0, NOW, "대회 취소");
			payoutRepository.saveAndFlush(firstPayout);
			Payout finalPayout = finalStage(22);

			assertThat(summary()).extracting(Payout::getId).contains(finalPayout.getId());
		}

		@Test
		@DisplayName("1차가 아예 없으면 그 최종은 기다리지 않고 들어간다")
		void includesFinalWithoutFirst() {
			Payout finalPayout = finalStage(23);

			assertThat(summary()).extracting(Payout::getId).contains(finalPayout.getId());
		}

		@Test
		@DisplayName("1차 자신은 최종 대기 규칙과 무관하게 지급 예정이면 들어간다")
		void includesFirstItself() {
			Payout firstPayout = first(24);

			assertThat(summary()).extracting(Payout::getId).contains(firstPayout.getId());
		}
	}
}
