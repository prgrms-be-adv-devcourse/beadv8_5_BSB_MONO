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
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutCreateDate;
import com.bukang.payout.domain.PayoutCreateFailure;
import com.bukang.payout.domain.PayoutCreateFailureType;
import com.bukang.payout.domain.PayoutHold;
import com.bukang.payout.domain.PayoutHoldReason;
import com.bukang.payout.domain.PayoutHoldStatus;
import com.bukang.payout.domain.PayoutItem;
import com.bukang.payout.domain.PayoutRecordType;
import com.bukang.payout.domain.PayoutStage;
import com.bukang.payout.domain.PayoutStatus;
import com.bukang.payout.domain.SetTicketDiffer;
import com.bukang.payout.domain.TwoStagePayoutCalculator;
import com.bukang.payout.out.PayoutCreateFailureRepository;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.out.RaceSchedule;
import com.bukang.payout.out.SoldEntry;
import com.bukang.payout.out.SoldEntryPort;
import com.bukang.payout.out.SoldSummary;
import com.bukang.payout.out.SoldSummaryPort;
import com.bukang.payout.support.StubRacePort;

/**
 * 정산 대사 (BUK-67). 기준 실행은 11/10 03:00이고, 대상은 1차 = 환불 마감일 11/9, 최종 = 대회일 11/9인 대회다
 * 정산 내역은 이번 실행에서 만들어진 것처럼 정산 시각(settledAt)을 실행 시작 시각으로 둔다
 */
@DataJpaTest
@ActiveProfiles("test")
class PayoutReconcileUseCaseTest {
	private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 3, 0);
	private static final LocalDate YESTERDAY = LocalDate.of(2026, 11, 9);
	private static final BusinessDayCalendar CALENDAR = new BusinessDayCalendar();

	// 1차 대상: 대회 10 (환불 마감일 11/9). 최종 대상: 대회 20 (대회일 11/9)
	private static final RaceSchedule FIRST_RACE = new RaceSchedule(10, "2026 한강 나이트런", LocalDate.of(2026, 11, 30),
		YESTERDAY);
	private static final RaceSchedule FINAL_RACE = new RaceSchedule(20, "2026 가을 하프마라톤", YESTERDAY,
		LocalDate.of(2026, 10, 30));

	private static final long COURSE_A = 3;
	private static final long COURSE_B = 4;

	@Autowired
	private PayoutRepository payoutRepository;

	@Autowired
	private PayoutHoldRepository payoutHoldRepository;

	@Autowired
	private PayoutCreateFailureRepository payoutCreateFailureRepository;

	// 기록 저장은 새 트랜잭션으로 커밋돼 테스트 롤백으로 지워지지 않는다. 다른 테스트에 남지 않게 지운다
	@AfterTransaction
	void deleteFailures() {
		payoutCreateFailureRepository.deleteAllInBatch();
	}

	private PayoutReconcileUseCase useCase(List<SoldSummary> summaries, List<SoldEntry> entries) {
		return useCase(raceId -> summaries.stream().filter(summary -> summary.raceId() == raceId).toList(),
			raceId -> entries.stream().filter(entry -> entry.raceId() == raceId).toList());
	}

	private PayoutReconcileUseCase useCase(SoldSummaryPort soldSummaryPort, SoldEntryPort soldEntryPort) {
		PayoutHoldUseCase payoutHoldUseCase = new PayoutHoldUseCase(payoutRepository, payoutHoldRepository, CALENDAR);
		return new PayoutReconcileUseCase(payoutRepository,
			new StubRacePort(raceId -> Optional.empty(), List.of(FIRST_RACE, FINAL_RACE), List.of()),
			soldSummaryPort, soldEntryPort,
			new PayoutReconcileStepUseCase(payoutRepository, payoutHoldUseCase, new SetTicketDiffer()),
			new PayoutCreateFailureUseCase(payoutCreateFailureRepository));
	}

	private Payout saveFirst(long sellerId, long priceSum, int ticketCount) {
		Payout payout = Payout.first(sellerId, FIRST_RACE.raceId(), FIRST_RACE.raceName(), YESTERDAY, NOW, CALENDAR);
		payout.recordAdvance(COURSE_A, priceSum, ticketCount, TODAY);
		return payoutRepository.save(payout);
	}

	// 참가권 ID마다 [orderItemId, 코스, 가격]
	private Payout saveFinal(long sellerId, long[]... tickets) {
		Payout payout = Payout.finalStage(sellerId, FINAL_RACE.raceId(), FINAL_RACE.raceName(), YESTERDAY, NOW,
			CALENDAR);
		for (long[] ticket : tickets) {
			payout.addItem(ticket[0], ticket[1], ticket[2], TwoStagePayoutCalculator.FEE_RATE,
				PayoutCreateDate.of(YESTERDAY));
		}
		return payoutRepository.save(payout);
	}

	private static long[] ticket(long orderItemId, long courseId, long price) {
		return new long[] {orderItemId, courseId, price};
	}

	private static SoldEntry sold(long orderItemId, long sellerId, long courseId, long price) {
		return new SoldEntry(orderItemId, sellerId, FINAL_RACE.raceId(), courseId, YESTERDAY, price);
	}

	// 판매자 2가 코스 a(1만 원) 참가권 101~105를 팔았다
	private static List<SoldEntry> soldFiveOfSeller2() {
		return LongStream.rangeClosed(101, 105).mapToObj(id -> sold(id, 2, COURSE_A, 10_000)).toList();
	}

	private List<PayoutCreateFailure> mismatches() {
		return payoutCreateFailureRepository.findAll();
	}

	private List<PayoutHold> reconcileHoldsOf(Payout payout) {
		return payoutHoldRepository.findAll().stream()
			.filter(hold -> hold.getPayout().getId() == payout.getId())
			.filter(hold -> hold.getReasonType() == PayoutHoldReason.대사_불일치)
			.toList();
	}

	@Test
	@DisplayName("TC-67-1-1 1차 판매 합과 1차 정산 판매액 합이 같으면 기록하지 않는다")
	void firstMatches() {
		saveFirst(1, 70_000, 7);

		useCase(List.of(new SoldSummary(1, 10, COURSE_A, 70_000, 7)), List.of()).run(TODAY, NOW);

		assertThat(mismatches()).isEmpty();
	}

	@Test
	@DisplayName("TC-67-3-1 1차 판매 합(7만 원)보다 정산 합(5만 원)이 적으면 기록 한 줄, 보류는 걸지 않는다")
	void firstLess() {
		Payout payout = saveFirst(1, 50_000, 5);

		useCase(List.of(new SoldSummary(1, 10, COURSE_A, 30_000, 3), new SoldSummary(1, 10, COURSE_B, 40_000, 2)),
			List.of()).run(TODAY, NOW);

		assertThat(mismatches())
			.extracting(PayoutCreateFailure::getPayoutStage, PayoutCreateFailure::getSellerId,
				PayoutCreateFailure::getRaceId, PayoutCreateFailure::getRaceCourseId,
				PayoutCreateFailure::getPayoutTargetDate, PayoutCreateFailure::getFailureType,
				PayoutCreateFailure::getNote, PayoutCreateFailure::getRunDate)
			.containsExactly(tuple(PayoutStage.FIRST, 1L, 10L, null, YESTERDAY, PayoutCreateFailureType.대사_불일치,
				"판매 합 = 70,000, 정산 합 = 50,000", TODAY));
		assertThat(payout.getStatus()).isEqualTo(PayoutStatus.CALCULATED);
	}

	@Test
	@DisplayName("TC-67-2-1 판매는 있는데 1차 정산 내역이 없으면 정산 합 0으로 기록한다")
	void firstMissing() {
		useCase(List.of(new SoldSummary(1, 10, COURSE_A, 70_000, 7)), List.of()).run(TODAY, NOW);

		assertThat(mismatches()).extracting(PayoutCreateFailure::getSellerId, PayoutCreateFailure::getNote)
			.containsExactly(tuple(1L, "판매 합 = 70,000, 정산 합 = 0"));
	}

	@Test
	@DisplayName("TC-67-13-2 1차 정산 합이 판매 합보다 크면 기록하고 대사 불일치로 보류한다")
	void firstMoreHolds() {
		Payout payout = saveFirst(1, 90_000, 9);

		useCase(List.of(new SoldSummary(1, 10, COURSE_A, 70_000, 7)), List.of()).run(TODAY, NOW);

		assertThat(mismatches()).extracting(PayoutCreateFailure::getNote)
			.containsExactly("판매 합 = 70,000, 정산 합 = 90,000");
		assertThat(payout.getStatus()).isEqualTo(PayoutStatus.HELD);
		assertThat(reconcileHoldsOf(payout)).hasSize(1);
	}

	@Test
	@DisplayName("TC-67-7-1 최종 판매 내역(취소 중 포함)과 정산 판매 줄이 같으면 기록하지 않는다")
	void finalMatches() {
		saveFinal(2, LongStream.rangeClosed(101, 105).mapToObj(id -> ticket(id, COURSE_A, 10_000))
			.toArray(long[][]::new));

		useCase(List.of(), soldFiveOfSeller2()).run(TODAY, NOW);

		assertThat(mismatches()).isEmpty();
	}

	@Test
	@DisplayName("TC-67-4-1 · 14-1 지급 전 최종 정산에 103번이 빠지면 판매 줄 · 수수료 줄을 추가하고 기록한다")
	void finalMissingAdds() {
		Payout payout = saveFinal(2, ticket(101, COURSE_A, 10_000), ticket(102, COURSE_A, 10_000),
			ticket(104, COURSE_A, 10_000), ticket(105, COURSE_A, 10_000));

		useCase(List.of(), soldFiveOfSeller2()).run(TODAY, NOW);

		assertThat(mismatches())
			.extracting(PayoutCreateFailure::getPayoutStage, PayoutCreateFailure::getSellerId,
				PayoutCreateFailure::getRaceId, PayoutCreateFailure::getRaceCourseId,
				PayoutCreateFailure::getPayoutTargetDate, PayoutCreateFailure::getNote)
			.containsExactly(tuple(PayoutStage.FINAL, 2L, 20L, COURSE_A, YESTERDAY, "빠진 1장 추가 (코스 3: 1장)"));
		assertThat(payout.getItems())
			.filteredOn(item -> Long.valueOf(103).equals(item.getOrderItemId()))
			.extracting(PayoutItem::getRecordType, PayoutItem::getAmount)
			.containsExactlyInAnyOrder(tuple(PayoutRecordType.SALE, 10_000L), tuple(PayoutRecordType.FEE, -550L));
		assertThat(payout.getSalesAmount()).isEqualTo(50_000);
		assertThat(payout.getStatus()).isEqualTo(PayoutStatus.CALCULATED);
	}

	@Test
	@DisplayName("TC-67-4-2 판매자의 최종 정산 내역이 아예 없으면 추가하지 않고 기록만 한다")
	void finalPayoutMissing() {
		useCase(List.of(), soldFiveOfSeller2()).run(TODAY, NOW);

		assertThat(mismatches()).extracting(PayoutCreateFailure::getRaceCourseId, PayoutCreateFailure::getNote)
			.containsExactly(tuple(COURSE_A, "빠진 5장 (코스 3: 5장)"));
		assertThat(payoutRepository.findAll()).isEmpty();
	}

	@Test
	@DisplayName("TC-67-5-1 정산 판매 줄에만 106번이 있으면 기록하고 대사 불일치로 보류한다")
	void finalExtraHolds() {
		Payout payout = saveFinal(2, LongStream.rangeClosed(101, 106).mapToObj(id -> ticket(id, COURSE_A, 10_000))
			.toArray(long[][]::new));

		useCase(List.of(), soldFiveOfSeller2()).run(TODAY, NOW);

		assertThat(mismatches()).extracting(PayoutCreateFailure::getNote)
			.containsExactly("더 들어감 1장 (코스 3: 1장)");
		assertThat(payout.getStatus()).isEqualTo(PayoutStatus.HELD);
		assertThat(reconcileHoldsOf(payout)).hasSize(1);
	}

	@Test
	@DisplayName("TC-67-5-2 빠짐과 더 들어감이 함께 있으면 한 줄에 쓰고, 코스가 여럿이면 코스 칸은 비운다")
	void finalMissingAndExtra() {
		Payout payout = saveFinal(2, ticket(101, COURSE_A, 10_000), ticket(102, COURSE_A, 10_000),
			ticket(104, COURSE_A, 10_000), ticket(105, COURSE_A, 10_000), ticket(106, COURSE_B, 20_000));

		useCase(List.of(), soldFiveOfSeller2()).run(TODAY, NOW);

		assertThat(mismatches()).extracting(PayoutCreateFailure::getRaceCourseId, PayoutCreateFailure::getNote)
			.containsExactly(tuple(null, "빠진 1장 추가 (코스 3: 1장), 더 들어감 1장 (코스 4: 1장)"));
		assertThat(payout.getItems()).anyMatch(item -> Long.valueOf(103).equals(item.getOrderItemId()));
		assertThat(payout.getStatus()).isEqualTo(PayoutStatus.HELD);
	}

	@Test
	@DisplayName("TC-67-6-1 판매 줄 가격이 주문 판매 내역 가격과 다르면 기록한다. 보류는 걸지 않는다")
	void finalPriceDiffers() {
		Payout payout = saveFinal(2, ticket(101, COURSE_A, 10_000), ticket(102, COURSE_A, 9_000),
			ticket(103, COURSE_A, 10_000), ticket(104, COURSE_A, 10_000), ticket(105, COURSE_A, 10_000));

		useCase(List.of(), soldFiveOfSeller2()).run(TODAY, NOW);

		assertThat(mismatches()).extracting(PayoutCreateFailure::getNote)
			.containsExactly("가격 다름 1장 (코스 3: 1장)");
		assertThat(payout.getStatus()).isEqualTo(PayoutStatus.CALCULATED);
	}

	@Test
	@DisplayName("TC-67-13-1 판매가 없는 판매자의 최종 정산 내역이 있으면 기록하고 보류한다")
	void finalSellerOnlyInPayout() {
		saveFinal(2, LongStream.rangeClosed(101, 105).mapToObj(id -> ticket(id, COURSE_A, 10_000))
			.toArray(long[][]::new));
		Payout stray = saveFinal(9, ticket(901, COURSE_A, 10_000), ticket(902, COURSE_A, 10_000));

		useCase(List.of(), soldFiveOfSeller2()).run(TODAY, NOW);

		assertThat(mismatches()).extracting(PayoutCreateFailure::getSellerId, PayoutCreateFailure::getNote)
			.containsExactly(tuple(9L, "더 들어감 2장 (코스 3: 2장)"));
		assertThat(stray.getStatus()).isEqualTo(PayoutStatus.HELD);
	}

	@Test
	@DisplayName("이미 계좌 미등록으로 보류 중이어도 대사 불일치 보류를 더 건다 (H1)")
	void holdsAlongsideAccountHold() {
		Payout payout = saveFinal(2, LongStream.rangeClosed(101, 106).mapToObj(id -> ticket(id, COURSE_A, 10_000))
			.toArray(long[][]::new));
		payoutHoldRepository.save(payout.hold(PayoutHoldReason.ACCOUNT_NOT_REGISTERED, null,
			PayoutHold.SYSTEM_MEMBER_ID, NOW));

		useCase(List.of(), soldFiveOfSeller2()).run(TODAY, NOW);

		assertThat(payoutHoldRepository.findAll()).extracting(PayoutHold::getReasonType, PayoutHold::getStatus)
			.containsExactlyInAnyOrder(tuple(PayoutHoldReason.ACCOUNT_NOT_REGISTERED, PayoutHoldStatus.HELD),
				tuple(PayoutHoldReason.대사_불일치, PayoutHoldStatus.HELD));
	}

	@Test
	@DisplayName("이미 지급 완료된 정산이 더 많으면 기록만 하고 보류는 걸지 않는다 (AC-67-13)")
	void paidRecordsOnly() {
		Payout payout = saveFinal(2, LongStream.rangeClosed(101, 106).mapToObj(id -> ticket(id, COURSE_A, 10_000))
			.toArray(long[][]::new));
		payout.pay(NOW);

		useCase(List.of(), soldFiveOfSeller2()).run(TODAY, NOW);

		assertThat(mismatches()).hasSize(1);
		assertThat(payout.getStatus()).isEqualTo(PayoutStatus.PAID);
		assertThat(reconcileHoldsOf(payout)).isEmpty();
	}

	@Test
	@DisplayName("취소된 정산 내역은 비교하지 않는다")
	void skipsCanceled() {
		Payout canceled = saveFinal(9, ticket(901, COURSE_A, 10_000));
		canceled.cancel(PayoutHold.SYSTEM_MEMBER_ID, NOW, "대회 취소");

		useCase(List.of(), List.of()).run(TODAY, NOW);

		assertThat(mismatches()).isEmpty();
	}

	@Test
	@DisplayName("TC-67-11-1 대사 중 주문 판매 내역을 받지 못하면 주문_조회_실패로 기록하고 그 대회만 건너뛴다")
	void orderLookupFails() {
		Payout first = saveFirst(1, 90_000, 9);

		useCase(raceId -> List.of(new SoldSummary(1, 10, COURSE_A, 70_000, 7)), raceId -> {
			throw new IllegalStateException("주문 서비스 응답 없음");
		}).run(TODAY, NOW);

		assertThat(mismatches())
			.extracting(PayoutCreateFailure::getPayoutStage, PayoutCreateFailure::getRaceId,
				PayoutCreateFailure::getFailureType)
			.containsExactlyInAnyOrder(tuple(PayoutStage.FIRST, 10L, PayoutCreateFailureType.대사_불일치),
				tuple(PayoutStage.FINAL, 20L, PayoutCreateFailureType.주문_조회_실패));
		assertThat(first.getStatus()).isEqualTo(PayoutStatus.HELD);
	}

	@Test
	@DisplayName("TC-67-12-1 500장이 빠져도 비고에는 장수만 쓴다")
	void manyMissingNoteHasCountOnly() {
		List<SoldEntry> entries = LongStream.rangeClosed(1, 500).mapToObj(id -> sold(id, 2, COURSE_A, 10_000))
			.toList();

		useCase(List.of(), entries).run(TODAY, NOW);

		assertThat(mismatches()).extracting(PayoutCreateFailure::getNote)
			.containsExactly("빠진 500장 (코스 3: 500장)");
	}
}
