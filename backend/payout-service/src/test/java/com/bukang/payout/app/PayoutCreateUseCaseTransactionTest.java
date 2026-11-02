package com.bukang.payout.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongFunction;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutItem;
import com.bukang.payout.domain.PayoutStage;
import com.bukang.payout.dto.PayoutCreateResultDto;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.out.PayoutStatusHistoryRepository;
import com.bukang.payout.out.RacePort;
import com.bukang.payout.out.RaceSchedule;
import com.bukang.payout.out.SellerAccountPort;
import com.bukang.payout.out.SoldEntry;
import com.bukang.payout.out.SoldEntryPort;
import com.bukang.payout.out.SoldSummary;
import com.bukang.payout.support.StubRacePort;

/**
 * 정산 대상일 배치가 판매자 × 대회 정산 내역마다 트랜잭션을 나누는지 확인한다 (PRO-60)
 * 테스트 전체를 감싸는 트랜잭션이 있으면 정산 내역별 커밋 · 롤백이 보이지 않으므로 끄고, 진짜 프록시(PayoutCreateStepUseCase 빈)를 쓴다
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({PayoutCreateStepUseCase.class, BusinessDayCalendar.class, PayoutLedgerUseCase.class, PayoutHoldUseCase.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PayoutCreateUseCaseTransactionTest {
	private static final LocalDate RACE_DATE = LocalDate.of(2026, 9, 25);
	private static final LocalDate TARGET_DATE = LocalDate.of(2026, 9, 26);
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 26, 3, 0);

	// 대회 30의 이름은 PAYOUT_PAYOUT.race_name(200자)보다 길어 정산 내역 저장이 DB에서 실패한다
	private static final LongFunction<Optional<String>> RACE_NAMES = raceId -> raceId == 30
		? Optional.of("가".repeat(201))
		: Optional.of("대회 " + raceId);

	@Autowired
	private PayoutRepository payoutRepository;

	@Autowired
	private PayoutItemRepository payoutItemRepository;

	@Autowired
	private PayoutHoldRepository payoutHoldRepository;

	@Autowired
	private PayoutCreateStepUseCase payoutCreateStepUseCase;

	@Autowired
	private PayoutStatusHistoryRepository payoutStatusHistoryRepository;

	@Autowired
	private PayoutHoldUseCase payoutHoldUseCase;

	// deleteAllInBatch는 SQL을 바로 보내 cascade가 돌지 않으므로, 정산 내역을 가리키는 자식 테이블부터 지운다
	@AfterEach
	void cleanUp() {
		payoutHoldRepository.deleteAllInBatch();
		payoutItemRepository.deleteAllInBatch();
		payoutStatusHistoryRepository.deleteAllInBatch();
		payoutRepository.deleteAllInBatch();
	}

	private PayoutCreateUseCase useCaseSelling(List<SoldEntry> entries, SellerAccountPort sellerAccountPort) {
		SoldEntryPort soldEntryPort = raceId -> entries.stream().filter(entry -> entry.raceId() == raceId).toList();
		return new PayoutCreateUseCase(payoutRepository, payoutCreateStepUseCase, soldEntryPort, sellerAccountPort,
			new StubRacePort(RACE_NAMES, List.of(), entries), raceId -> List.of());
	}

	@Test
	@DisplayName("정산 내역 하나의 저장이 DB에서 실패해도 같은 날 다른 정산 내역은 저장된다")
	void failedGroupDoesNotRollBackOthers() {
		PayoutCreateUseCase useCase = useCaseSelling(List.of(
			new SoldEntry(100, 1, 10, 10L, RACE_DATE, 50_000L),
			new SoldEntry(300, 3, 30, 30L, RACE_DATE, 40_000L),
			new SoldEntry(200, 2, 20, 20L, RACE_DATE, 30_000L)
		), sellerId -> true);

		PayoutCreateResultDto result = useCase.run(TARGET_DATE, NOW);

		// 이름이 너무 긴 저장 실패는 DB 제약 위반이어도 정산 내역이 없으므로 이미 있음이 아니라 실패로 센다 (PRO-120)
		assertThat(result.failed()).isEqualTo(1);
		assertThat(result.skipped()).isZero();

		assertThat(payoutRepository.findAll())
			.extracting(Payout::getSellerId)
			.containsExactlyInAnyOrder(1L, 2L);
		// 최종 정산 내역 2개 × (1차 정산금 차감 1줄 + 참가권 판매 · 수수료 2줄) (PRO-214)
		assertThat(payoutItemRepository.count()).isEqualTo(6);
	}

	@Test
	@DisplayName("존재 확인 뒤 다른 서버가 같은 최종 정산 내역을 먼저 저장하면, 유일 제약 위반을 실패가 아니라 이미 있음으로 센다")
	void finalSavedByOtherServerIsSkipped() {
		// 다른 서버가 이 실행의 존재 확인과 저장 사이에 판매자 1의 최종 정산 내역을 먼저 저장했다
		payoutRepository.save(Payout.finalStage(1, 10, "대회 10", RACE_DATE, NOW, new BusinessDayCalendar()));
		PayoutRepository racingRepository = notYetSavedOnFirstCheck(repository ->
			repository.existsBySellerIdAndRaceIdAndPayoutMonthAndPayoutStage(1, 10, "2026-09", PayoutStage.FINAL));
		SoldEntryPort soldEntryPort = raceId -> List.of(new SoldEntry(100, 1, 10, 10L, RACE_DATE, 50_000L));
		PayoutCreateUseCase useCase = new PayoutCreateUseCase(racingRepository, payoutCreateStepUseCase,
			soldEntryPort, sellerId -> true,
			new StubRacePort(RACE_NAMES, List.of(), soldEntryPort.findSoldEntries(10)), raceId -> List.of());

		PayoutCreateResultDto result = useCase.run(TARGET_DATE, NOW);

		assertThat(result.failed()).isZero();
		assertThat(result.skipped()).isEqualTo(1);
		assertThat(payoutRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("존재 확인 뒤 다른 서버가 같은 1차 정산 내역을 먼저 저장하면, 유일 제약 위반을 실패가 아니라 이미 있음으로 센다")
	void firstSavedByOtherServerIsSkipped() {
		LocalDate refundDeadline = TARGET_DATE.minusDays(1);
		payoutRepository.save(Payout.first(1, 10, "대회 10", refundDeadline, NOW, new BusinessDayCalendar()));
		PayoutRepository racingRepository = notYetSavedOnFirstCheck(repository ->
			repository.existsBySellerIdAndRaceIdAndPayoutStage(1, 10, PayoutStage.FIRST));
		// 대회일은 되돌아보기 기간 밖이라 최종은 만들지 않고 1차만 만든다
		RaceSchedule race = new RaceSchedule(10, "대회 10", TARGET_DATE.plusDays(30), refundDeadline);
		PayoutCreateUseCase useCase = new PayoutCreateUseCase(racingRepository, payoutCreateStepUseCase,
			raceId -> List.of(), sellerId -> true, new StubRacePort(RACE_NAMES, List.of(race), List.of()),
			raceId -> List.of(new SoldSummary(1, 10, 10, 50_000L, 1)));

		PayoutCreateResultDto result = useCase.run(TARGET_DATE, NOW);

		assertThat(result.failed()).isZero();
		assertThat(result.skipped()).isEqualTo(1);
		assertThat(payoutRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("유일 제약 위반 뒤 다시 확인하는 조회가 실패하면, 그 정산 내역만 실패로 세고 다음 정산 내역을 계속 만든다")
	void recheckFailureCountsAsFailedAndContinues() {
		// 판매자 1의 최종 정산 내역은 다른 서버가 먼저 저장했고, 제약 위반 직후 다시 확인하는 조회에서 DB 연결이 끊긴다
		payoutRepository.save(Payout.finalStage(1, 10, "대회 10", RACE_DATE, NOW, new BusinessDayCalendar()));
		PayoutRepository racingRepository = racingRepository(repository ->
				repository.existsBySellerIdAndRaceIdAndPayoutMonthAndPayoutStage(1, 10, "2026-09", PayoutStage.FINAL),
			invocation -> {
				throw new IllegalStateException("DB 연결 끊김");
			});
		SoldEntryPort soldEntryPort = raceId -> List.of(
			new SoldEntry(100, 1, 10, 10L, RACE_DATE, 50_000L),
			new SoldEntry(200, 2, 10, 10L, RACE_DATE, 30_000L));
		PayoutCreateUseCase useCase = new PayoutCreateUseCase(racingRepository, payoutCreateStepUseCase,
			soldEntryPort, sellerId -> true,
			new StubRacePort(RACE_NAMES, List.of(), soldEntryPort.findSoldEntries(10)), raceId -> List.of());

		PayoutCreateResultDto result = useCase.run(TARGET_DATE, NOW);

		assertThat(result.failed()).isEqualTo(1);
		assertThat(result.created()).isEqualTo(1);
		assertThat(payoutRepository.findAll())
			.extracting(Payout::getSellerId)
			.containsExactlyInAnyOrder(1L, 2L);
	}

	// 실제 저장소에 그대로 넘기되, existsCheck가 고른 존재 확인만 처음 한 번은 '없음'으로 답하는 저장소
	// 이 실행이 확인한 뒤에 다른 서버가 저장한 상황이다. 두 번째부터는 실제 DB 값을 돌려준다
	private PayoutRepository notYetSavedOnFirstCheck(Predicate<PayoutRepository> existsCheck) {
		return racingRepository(existsCheck,
			invocation -> invocation.getMethod().invoke(payoutRepository, invocation.getArguments()));
	}

	// existsCheck가 고른 존재 확인은 처음 한 번 '없음'으로 답하고, 두 번째부터(제약 위반 뒤 다시 확인)는 recheck로 답한다
	// existsCheck는 존재 확인 메서드를 고르는 데만 쓴다. doAnswer(...).when(racing)이 돌려준 스텁 대상에서
	// 그 메서드를 실제 인자로 한 번 불러, 이름과 인자가 모두 같은 호출 하나만 가로챈다. 반환값은 쓰지 않는다
	private PayoutRepository racingRepository(Predicate<PayoutRepository> existsCheck, Answer<?> recheck) {
		PayoutRepository racing = mock(PayoutRepository.class, delegatesTo(payoutRepository));
		AtomicBoolean checked = new AtomicBoolean(false);
		existsCheck.test(doAnswer(invocation -> checked.getAndSet(true) ? recheck.answer(invocation) : false)
			.when(racing));
		return racing;
	}

	@Test
	@DisplayName("판매자 정보를 묻다 실패한 정산 내역은 건너뛰고, 다른 정산 내역은 저장된다")
	void failedLookupSkipsOnlyThatGroup() {
		// 판매자 2의 계좌 확인이 실패한다 (회원 서비스 장애 등)
		SellerAccountPort sellerAccountPort = sellerId -> {
			if (sellerId == 2) {
				throw new IllegalStateException("회원 서비스 응답 없음");
			}
			return true;
		};
		PayoutCreateUseCase useCase = useCaseSelling(List.of(
			new SoldEntry(100, 1, 10, 10L, RACE_DATE, 50_000L),
			new SoldEntry(200, 2, 20, 20L, RACE_DATE, 30_000L)
		), sellerAccountPort);

		useCase.run(TARGET_DATE, NOW);

		assertThat(payoutRepository.findAll())
			.extracting(Payout::getSellerId)
			.containsExactly(1L);
		assertThat(payoutHoldRepository.count()).isZero();
	}

	@Test
	@DisplayName("어제 실패한 정산 내역은 오늘 실행이 되돌아보기로 다시 만들고, 정산 대상일과 정산 월은 원래 날짜로 남긴다")
	void failedGroupIsCreatedOnNextRun() {
		// 9/26에는 회원 서비스가 멈춰 판매자 2의 계좌 확인이 실패하고, 9/27에는 돌아온다
		AtomicBoolean memberServiceDown = new AtomicBoolean(true);
		SellerAccountPort sellerAccountPort = sellerId -> {
			if (sellerId == 2 && memberServiceDown.get()) {
				throw new IllegalStateException("회원 서비스 응답 없음");
			}
			return true;
		};
		PayoutCreateUseCase useCase = useCaseSelling(List.of(
			new SoldEntry(100, 1, 10, 10L, RACE_DATE, 50_000L),
			new SoldEntry(200, 2, 20, 20L, RACE_DATE, 30_000L)
		), sellerAccountPort);

		useCase.run(TARGET_DATE, NOW);
		memberServiceDown.set(false);
		useCase.run(TARGET_DATE.plusDays(1), NOW.plusDays(1));

		List<Payout> payouts = payoutRepository.findAll();
		assertThat(payouts)
			.extracting(Payout::getSellerId)
			.containsExactlyInAnyOrder(1L, 2L);
		Payout retried = payouts.stream().filter(p -> p.getSellerId() == 2).findFirst().orElseThrow();
		assertThat(retried.getPayoutMonth()).isEqualTo("2026-09");
		assertThat(payoutItemRepository.findAll())
			.filteredOn(item -> Long.valueOf(200L).equals(item.getOrderItemId()))
			.extracting(PayoutItem::getTargetDate)
			.containsOnly(TARGET_DATE);
	}

	@Test
	@DisplayName("자동 보류는 정산 생성 트랜잭션 안에서만 건다. 트랜잭션 밖에서 부르면 예외가 나고 보류가 남지 않는다")
	void autoHoldRequiresTransaction() {
		Payout first = payoutRepository.save(Payout.first(1, 10, "대회 10", LocalDate.of(2026, 9, 30), NOW,
			new BusinessDayCalendar()));
		Payout last = payoutRepository.save(Payout.finalStage(1, 10, "대회 10", RACE_DATE, NOW,
			new BusinessDayCalendar()));

		assertThatThrownBy(() -> payoutHoldUseCase.holdNewFirst(first, false, NOW))
			.isInstanceOf(IllegalTransactionStateException.class);
		assertThatThrownBy(() -> payoutHoldUseCase.holdNewFinal(last, Optional.empty(), false, NOW))
			.isInstanceOf(IllegalTransactionStateException.class);
		assertThat(payoutHoldRepository.count()).isZero();
	}
}
