package com.bukang.payout.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.AfterTransaction;

import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.PayoutCreateFailure;
import com.bukang.payout.domain.PayoutCreateFailureType;
import com.bukang.payout.domain.SetEntryDiffer;
import com.bukang.payout.out.PayoutCreateFailureRepository;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRecoveryRepository;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.out.RaceSchedule;
import com.bukang.payout.out.SoldEntry;
import com.bukang.payout.out.SoldEntryPort;
import com.bukang.payout.out.SoldSummaryPort;
import com.bukang.payout.support.StubRacePort;

/**
 * 대사 대상 범위 (AC-67-8): 정산 생성과 대사를 11/9 · 11/10 두 번 돌린다. 실행 시작 시각만 바꾸고 시계는 고정하지 않는다
 * 대회 30(대회일 11/8)은 11/10 실행에서 ①(새로 대상이 된 대회, 대회일 11/9)에 들지 않는다
 */
@DataJpaTest
@ActiveProfiles("test")
class PayoutReconcileTargetTest {
	private static final LocalDate RACE_DATE = LocalDate.of(2026, 11, 8);
	private static final LocalDate DAY1 = LocalDate.of(2026, 11, 9);
	private static final LocalDate DAY2 = LocalDate.of(2026, 11, 10);
	private static final LocalDateTime NOW1 = DAY1.atTime(3, 0);
	private static final LocalDateTime NOW2 = DAY2.atTime(3, 0);
	private static final RaceSchedule RACE = new RaceSchedule(30, "2026 가을 10K", RACE_DATE, RACE_DATE.minusYears(1));
	private static final long COURSE = 3;

	@Autowired
	private PayoutRepository payoutRepository;

	@Autowired
	private PayoutItemRepository payoutItemRepository;

	@Autowired
	private PayoutRecoveryRepository payoutRecoveryRepository;

	@Autowired
	private PayoutHoldRepository payoutHoldRepository;

	@Autowired
	private PayoutCreateFailureRepository payoutCreateFailureRepository;

	// 주문 서비스가 지금 돌려줄 참가권. null이면 응답하지 않는다
	private List<SoldEntry> orderEntries;

	@AfterTransaction
	void deleteFailures() {
		payoutCreateFailureRepository.deleteAllInBatch();
	}

	private static List<SoldEntry> entries(long fromId, long toId) {
		return LongStream.rangeClosed(fromId, toId)
			.mapToObj(id -> new SoldEntry(id, 2, RACE.raceId(), COURSE, RACE_DATE, 10_000)).toList();
	}

	// 생성 배치 뒤 대사를 돌린다 (PayoutCreateScheduler와 같은 순서)
	private void runBatch(LocalDate today, LocalDateTime now) {
		SoldEntryPort soldEntryPort = raceId -> {
			if (orderEntries == null) {
				throw new IllegalStateException("주문 서비스 응답 없음");
			}
			return orderEntries;
		};
		SoldSummaryPort noFirst = raceId -> List.of();
		StubRacePort racePort = new StubRacePort(raceId -> Optional.empty(), List.of(RACE), List.of());
		BusinessDayCalendar calendar = new BusinessDayCalendar();
		PayoutHoldUseCase payoutHoldUseCase = new PayoutHoldUseCase(payoutRepository, payoutHoldRepository, calendar);
		PayoutCreateFailureUseCase failureUseCase = new PayoutCreateFailureUseCase(payoutCreateFailureRepository);
		PayoutCreateStepUseCase stepUseCase = new PayoutCreateStepUseCase(payoutRepository, calendar,
			new PayoutLedgerUseCase(payoutRepository, payoutItemRepository, payoutRecoveryRepository),
			payoutHoldUseCase);

		new PayoutCreateUseCase(payoutRepository, stepUseCase, soldEntryPort, sellerId -> true, racePort, noFirst,
			failureUseCase).run(today, now);
		new PayoutReconcileUseCase(payoutRepository, racePort, noFirst, soldEntryPort,
			new PayoutReconcileStepUseCase(payoutRepository, payoutHoldUseCase,
				new PayoutLedgerUseCase(payoutRepository, payoutItemRepository, payoutRecoveryRepository),
				new SetEntryDiffer()),
			failureUseCase).run(today, now);
	}

	private List<PayoutCreateFailure> mismatches() {
		return payoutCreateFailureRepository.findAll().stream()
			.filter(failure -> failure.getFailureType() == PayoutCreateFailureType.대사_불일치)
			.toList();
	}

	@Test
	@DisplayName("TC-67-8-1 이틀 전 대상이 된 대회는 이번 실행에서 새로 만든 정산이 없으면 비교하지 않는다")
	void skipsRaceSettledEarlier() {
		// 11/9 실행이 참가권 4장으로 정산을 만들고 그날 대사도 4장으로 맞음. 그 뒤 주문 쪽에 5장이 보인다
		orderEntries = entries(301, 304);
		runBatch(DAY1, NOW1);
		orderEntries = entries(301, 305);

		runBatch(DAY2, NOW2);

		assertThat(mismatches()).isEmpty();
	}

	@Test
	@DisplayName("TC-67-8-2 11/9에 실패해 11/10 실행에서 늦게 만든 정산은 비교해 빠진 참가권을 채운다")
	void reconcilesRaceSettledLate() {
		orderEntries = null;
		runBatch(DAY1, NOW1);

		// 11/10 생성 때는 4장, 대사 때는 5장을 받은 것처럼 만든다: 정산을 만들고 나서 대사 전에 응답이 바뀐다
		orderEntries = entries(301, 304);
		runBatch(DAY2, NOW2);
		orderEntries = entries(301, 305);
		new PayoutReconcileUseCase(payoutRepository,
			new StubRacePort(raceId -> Optional.empty(), List.of(RACE), List.of()), raceId -> List.of(),
			raceId -> orderEntries,
			new PayoutReconcileStepUseCase(payoutRepository,
				new PayoutHoldUseCase(payoutRepository, payoutHoldRepository, new BusinessDayCalendar()),
				new PayoutLedgerUseCase(payoutRepository, payoutItemRepository, payoutRecoveryRepository),
				new SetEntryDiffer()),
			new PayoutCreateFailureUseCase(payoutCreateFailureRepository)).run(DAY2, NOW2);

		assertThat(mismatches())
			.extracting(PayoutCreateFailure::getRaceId, PayoutCreateFailure::getPayoutTargetDate,
				PayoutCreateFailure::getNote, PayoutCreateFailure::getRunDate)
			.containsExactly(tuple(30L, RACE_DATE, "빠진 1장 추가 (코스 3: 1장)", DAY2));
		assertThat(payoutRepository.findAll()).singleElement()
			.satisfies(payout -> assertThat(payout.getSalesAmount()).isEqualTo(50_000));
	}
}
