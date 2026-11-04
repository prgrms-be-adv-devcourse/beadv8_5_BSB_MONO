package com.bukang.payout.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.AfterTransaction;

import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutCreateFailure;
import com.bukang.payout.domain.PayoutCreateFailureType;
import com.bukang.payout.domain.PayoutStage;
import com.bukang.payout.domain.exception.PayoutOrderLookupUnavailableException;
import com.bukang.payout.domain.exception.PayoutSellerAccountLookupUnavailableException;
import com.bukang.payout.dto.PayoutCreateResultDto;
import com.bukang.payout.out.PayoutCreateFailureRepository;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRecoveryRepository;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.out.RacePort;
import com.bukang.payout.out.RaceSchedule;
import com.bukang.payout.out.SellerAccountPort;
import com.bukang.payout.out.SoldEntry;
import com.bukang.payout.out.SoldEntryPort;
import com.bukang.payout.support.StubRacePort;

/**
 * 정산 생성이 실패할 때마다 실패 기록표에 한 줄 쌓이는지 본다 (BUK-60, 지시서 6장 TC-60-*)
 * 기준 실행은 11/10 03:00이다. 최종은 대회일 11/3 ~ 11/9를 보고, 대회 10의 대회일은 11/5다
 * 1차 정산은 보지 않는다 (사용자: "1차 정산은 고려하지 않는다"). 그래서 상품 서비스의 환불 마감일은 1차 기간 밖에 둔다
 * 생성 시각은 BaseIdAndTime이 실제 시각으로 채우므로 값은 보지 않고 채워졌는지만 본다
 */
@DataJpaTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class PayoutCreateUseCaseFailureRecordTest {
	private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 3, 0);
	private static final LocalDate RACE_DATE = LocalDate.of(2026, 11, 5);
	private static final RaceSchedule RACE_10 = race(10, RACE_DATE);
	// 판매자 1의 참가권 대회일이 상품의 대회일(11/5)과 다르다 (checkRaceDate)
	private static final SoldEntry MISMATCHED = new SoldEntry(100, 1, 10, 10L, RACE_DATE.minusDays(1), 50_000L);
	private static final SoldEntry SELLER_1 = new SoldEntry(100, 1, 10, 10L, RACE_DATE, 50_000L);
	private static final SoldEntry SELLER_2 = new SoldEntry(200, 2, 10, 10L, RACE_DATE, 30_000L);
	private static final String RECORD_FAILED_LOG = "정산 생성 실패를 기록표에 남기지 못했습니다";

	@Autowired
	private PayoutRepository payoutRepository;

	@Autowired
	private PayoutHoldRepository payoutHoldRepository;

	@Autowired
	private PayoutItemRepository payoutItemRepository;

	@Autowired
	private PayoutRecoveryRepository payoutRecoveryRepository;

	@Autowired
	private PayoutCreateFailureRepository payoutCreateFailureRepository;

	// 기록 저장은 새 트랜잭션으로 커밋돼 테스트 롤백으로 지워지지 않는다. 다른 테스트에 남지 않게 지운다
	@AfterTransaction
	void deleteFailures() {
		payoutCreateFailureRepository.deleteAllInBatch();
	}

	// 환불 마감일은 1차 되돌아보기 기간에 걸리지 않게 1년 전으로 둔다
	private static RaceSchedule race(long raceId, LocalDate raceDate) {
		return new RaceSchedule(raceId, "대회 " + raceId, raceDate, raceDate.minusYears(1));
	}

	private static RacePort racesOf(RaceSchedule race) {
		return new StubRacePort(raceId -> Optional.empty(), List.of(race), List.of());
	}

	private static SoldEntryPort soldEntries(SoldEntry... entries) {
		return raceId -> List.of(entries).stream().filter(entry -> entry.raceId() == raceId).toList();
	}

	private PayoutCreateStepUseCase realStep() {
		return new PayoutCreateStepUseCase(payoutRepository, new BusinessDayCalendar(),
			new PayoutLedgerUseCase(payoutRepository, payoutItemRepository, payoutRecoveryRepository),
			new PayoutHoldUseCase(payoutRepository, payoutHoldRepository, new BusinessDayCalendar()));
	}

	// 최종 정산 내역 저장(PayoutCreateStepUseCase)이 exception으로 실패한다
	private static PayoutCreateStepUseCase stepThrowing(RuntimeException exception) {
		PayoutCreateStepUseCase step = mock(PayoutCreateStepUseCase.class);
		doThrow(exception).when(step).createFinal(any(), any(), any());
		return step;
	}

	private PayoutCreateUseCase useCase(PayoutRepository repository, PayoutCreateStepUseCase step,
		SoldEntryPort soldEntryPort, SellerAccountPort sellerAccountPort, RacePort racePort,
		PayoutCreateFailureRepository failureRepository) {
		return new PayoutCreateUseCase(repository, step, soldEntryPort, sellerAccountPort, racePort,
			raceId -> List.of(), new PayoutCreateFailureUseCase(failureRepository));
	}

	private PayoutCreateUseCase useCase(PayoutCreateStepUseCase step, SoldEntryPort soldEntryPort,
		SellerAccountPort sellerAccountPort, RacePort racePort) {
		return useCase(payoutRepository, step, soldEntryPort, sellerAccountPort, racePort,
			payoutCreateFailureRepository);
	}

	// 판매자 1 × 대회 10 최종 실패 한 줄의 칸 (실패 종류만 다르다). 코스 · 비고는 빈칸
	private static Tuple sellerRaceRow(PayoutCreateFailureType type) {
		return tuple(PayoutStage.FINAL, 1L, 10L, null, RACE_DATE, type, null, TODAY);
	}

	private List<Tuple> rows() {
		List<PayoutCreateFailure> failures = payoutCreateFailureRepository.findAll();
		assertThat(failures).allMatch(failure -> failure.getCreateDate() != null);
		return failures.stream()
			.map(failure -> tuple(failure.getPayoutStage(), failure.getSellerId(), failure.getRaceId(),
				failure.getRaceCourseId(), failure.getPayoutTargetDate(), failure.getFailureType(), failure.getNote(),
				failure.getRunDate()))
			.toList();
	}

	@Test
	@DisplayName("TC-60-1-1 참가권 대회일이 상품 대회일과 다르면 '주문_정산_대회일_불일치'로 한 줄 쌓인다")
	void raceDateMismatchIsRecorded() {
		useCase(realStep(), soldEntries(MISMATCHED), sellerId -> true, racesOf(RACE_10)).run(TODAY, NOW);

		assertThat(rows()).containsExactly(sellerRaceRow(PayoutCreateFailureType.주문_정산_대회일_불일치));
	}

	@Test
	@DisplayName("TC-60-1-2 정산 계좌 조회가 실패하면 '정산_계좌_조회_실패'로 쌓인다")
	void sellerAccountLookupFailureIsRecorded() {
		SellerAccountPort accountDown = sellerId -> {
			throw new PayoutSellerAccountLookupUnavailableException(sellerId, new IllegalStateException("응답 없음"));
		};

		useCase(realStep(), soldEntries(SELLER_1), accountDown, racesOf(RACE_10)).run(TODAY, NOW);

		assertThat(rows()).containsExactly(sellerRaceRow(PayoutCreateFailureType.정산_계좌_조회_실패));
	}

	@Test
	@DisplayName("TC-60-1-3 정산 내역 저장이 DB 예외로 실패하면 '정산_저장_실패'로 쌓인다")
	void saveFailureIsRecorded() {
		PayoutCreateStepUseCase step = stepThrowing(new DataIntegrityViolationException("race_name 칸보다 김"));

		useCase(step, soldEntries(SELLER_1), sellerId -> true, racesOf(RACE_10)).run(TODAY, NOW);

		assertThat(rows()).containsExactly(sellerRaceRow(PayoutCreateFailureType.정산_저장_실패));
	}

	@Test
	@DisplayName("TC-60-1-4 그 밖의 예외로 실패하면 '알_수_없는_실패'로 쌓인다")
	void unknownFailureIsRecorded() {
		PayoutCreateStepUseCase step = stepThrowing(new IllegalArgumentException("예상 못한 값"));

		useCase(step, soldEntries(SELLER_1), sellerId -> true, racesOf(RACE_10)).run(TODAY, NOW);

		assertThat(rows()).containsExactly(sellerRaceRow(PayoutCreateFailureType.알_수_없는_실패));
	}

	@Test
	@DisplayName("TC-60-2-1 주문 서비스에서 대회 판매 값을 받지 못하면 판매자 칸이 빈 '주문_조회_실패' 한 줄이 쌓인다")
	void orderLookupFailureIsRecorded() {
		SoldEntryPort orderDown = raceId -> {
			throw new PayoutOrderLookupUnavailableException(raceId, new IllegalStateException("응답 없음"));
		};

		useCase(realStep(), orderDown, sellerId -> true, racesOf(RACE_10)).run(TODAY, NOW);

		assertThat(rows()).containsExactly(
			tuple(PayoutStage.FINAL, null, 10L, null, RACE_DATE, PayoutCreateFailureType.주문_조회_실패, null, TODAY));
	}

	@Test
	@DisplayName("TC-60-3-1 최종 대상 대회 목록을 받지 못하면 판매자 · 대회 · 날짜 칸이 빈 줄에 본 기간을 남긴다")
	void raceLookupFailureIsRecorded() {
		RacePort finalLookupDown = new RacePort() {
			@Override
			public List<RaceSchedule> findRacesByRefundDeadline(LocalDate fromRefundDeadline,
				LocalDate toRefundDeadline) {
				return List.of();
			}

			@Override
			public List<RaceSchedule> findRacesByRaceDate(LocalDate fromRaceDate, LocalDate toRaceDate) {
				throw new IllegalStateException("상품 서비스 응답 없음");
			}
		};

		useCase(realStep(), soldEntries(), sellerId -> true, finalLookupDown).run(TODAY, NOW);

		assertThat(rows()).containsExactly(tuple(PayoutStage.FINAL, null, null, null, null,
			PayoutCreateFailureType.대회_조회_실패, "from~to: 2026/11/3~2026/11/9", TODAY));
	}

	@Test
	@DisplayName("TC-60-4-1 다음 날 같은 실패가 반복되면 덮어쓰지 않고 한 줄이 더 쌓인다")
	void repeatedFailureAddsRow() {
		PayoutCreateUseCase useCase = useCase(realStep(), soldEntries(MISMATCHED), sellerId -> true,
			racesOf(RACE_10));

		useCase.run(TODAY, NOW);
		useCase.run(TODAY.plusDays(1), NOW.plusDays(1));

		assertThat(rows()).containsExactlyInAnyOrder(
			sellerRaceRow(PayoutCreateFailureType.주문_정산_대회일_불일치),
			tuple(PayoutStage.FINAL, 1L, 10L, null, RACE_DATE, PayoutCreateFailureType.주문_정산_대회일_불일치, null,
				TODAY.plusDays(1)));
	}

	@Test
	@DisplayName("TC-60-5-1 기록표 저장이 실패해도 다음 판매자의 정산 내역을 만들고, 기록 실패는 ERROR 로그 한 줄로 남는다")
	void recordFailureDoesNotStopBatch(CapturedOutput output) {
		PayoutCreateFailureRepository brokenTable = mock(PayoutCreateFailureRepository.class);
		when(brokenTable.save(any())).thenThrow(new IllegalStateException("실패 기록표 DB 연결 끊김"));

		PayoutCreateResultDto result = useCase(payoutRepository, realStep(), soldEntries(MISMATCHED, SELLER_2),
			sellerId -> true, racesOf(RACE_10), brokenTable).run(TODAY, NOW);

		assertThat(result.failed()).isEqualTo(1);
		assertThat(payoutRepository.findAll()).extracting(Payout::getSellerId).containsExactly(2L);
		assertThat(output.getAll().lines().filter(line -> line.contains(RECORD_FAILED_LOG)))
			.hasSize(1)
			.allMatch(line -> line.contains("ERROR"));
	}

	@Test
	@DisplayName("TC-60-6-1 이미 만든 정산 내역이라 건너뛰면 기록표에 쌓이지 않는다")
	void skippedPayoutIsNotRecorded() {
		payoutRepository.save(Payout.finalStage(1, 10, "대회 10", RACE_DATE, NOW, new BusinessDayCalendar()));

		useCase(realStep(), soldEntries(SELLER_1), sellerId -> true, racesOf(RACE_10)).run(TODAY, NOW);

		assertThat(payoutCreateFailureRepository.count()).isZero();
	}

	@Test
	@DisplayName("TC-60-6-2 다른 서버가 같은 정산 내역을 먼저 저장해 유일 제약에 걸리면 기록표에 쌓이지 않는다")
	void savedByOtherIsNotRecorded() {
		// 다른 서버가 이 실행의 존재 확인과 저장 사이에 먼저 저장했다. 처음 확인만 '없음'으로 답한다
		payoutRepository.save(Payout.finalStage(1, 10, "대회 10", RACE_DATE, NOW, new BusinessDayCalendar()));
		PayoutRepository racing = mock(PayoutRepository.class, delegatesTo(payoutRepository));
		AtomicBoolean checked = new AtomicBoolean(false);
		doAnswer(invocation -> checked.getAndSet(true))
			.when(racing).existsBySellerIdAndRaceIdAndPayoutMonthAndPayoutStage(1, 10, "2026-11", PayoutStage.FINAL);
		PayoutCreateStepUseCase step = stepThrowing(new DataIntegrityViolationException("유일 제약 위반"));

		PayoutCreateResultDto result = useCase(racing, step, soldEntries(SELLER_1), sellerId -> true,
			racesOf(RACE_10), payoutCreateFailureRepository).run(TODAY, NOW);

		assertThat(result.skipped()).isEqualTo(1);
		assertThat(payoutCreateFailureRepository.count()).isZero();
	}

	@Test
	@DisplayName("TC-60-7-1 대회일 11/3은 11/10 실행의 되돌아보기 마지막 날이라 실패가 쌓인다")
	void lastLookbackDayIsRecorded() {
		LocalDate raceDate = LocalDate.of(2026, 11, 3);

		useCase(realStep(), soldEntries(new SoldEntry(100, 1, 10, 10L, raceDate.minusDays(1), 50_000L)),
			sellerId -> true, racesOf(race(10, raceDate))).run(TODAY, NOW);

		assertThat(payoutCreateFailureRepository.findAll())
			.extracting(PayoutCreateFailure::getPayoutTargetDate, PayoutCreateFailure::getFailureType)
			.containsExactly(tuple(raceDate, PayoutCreateFailureType.주문_정산_대회일_불일치));
	}

	@Test
	@DisplayName("TC-60-7-2 대회일 11/2는 11/10 실행이 보지 않아 새 줄이 생기지 않는다")
	void outsideLookbackIsNotRecorded() {
		LocalDate raceDate = LocalDate.of(2026, 11, 2);

		useCase(realStep(), soldEntries(new SoldEntry(100, 1, 10, 10L, raceDate.minusDays(1), 50_000L)),
			sellerId -> true, racesOf(race(10, raceDate))).run(TODAY, NOW);

		assertThat(payoutCreateFailureRepository.count()).isZero();
	}
}
