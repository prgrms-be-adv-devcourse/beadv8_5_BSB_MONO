package com.bukang.payout.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutHold;
import com.bukang.payout.domain.PayoutHoldReason;
import com.bukang.payout.domain.PayoutHoldStatus;
import com.bukang.payout.domain.PayoutItem;
import com.bukang.payout.domain.PayoutRecordType;
import com.bukang.payout.domain.PayoutStage;
import com.bukang.payout.domain.PayoutStatus;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRecoveryRepository;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.out.RaceSchedule;
import com.bukang.payout.out.SellerAccountPort;
import com.bukang.payout.out.SoldEntry;
import com.bukang.payout.out.SoldEntryPort;
import com.bukang.payout.out.SoldSummary;
import com.bukang.payout.out.SoldSummaryPort;
import com.bukang.payout.support.StubRacePort;

@DataJpaTest
@ActiveProfiles("test")
class PayoutCreateUseCaseTest {
	private static final LocalDate RACE_DATE = LocalDate.of(2026, 9, 25);
	private static final LocalDate TARGET_DATE = LocalDate.of(2026, 9, 26);
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 26, 3, 0);

	// 상품 서비스에 대회 10, 20, 40, 50만 있다 (30은 상품 서비스가 모르는 대회)
	private static final IntFunction<Optional<String>> RACE_NAMES = raceId -> switch (raceId) {
		case 10 -> Optional.of("2026 한강 나이트런");
		case 20 -> Optional.of("2026 가을 하프마라톤");
		case 40 -> Optional.of("2026 가을 5K");
		case 50 -> Optional.of("2026 가을 트레일런");
		default -> Optional.empty();
	};

	@Autowired
	private PayoutRepository payoutRepository;

	@Autowired
	private PayoutHoldRepository payoutHoldRepository;

	@Autowired
	private PayoutItemRepository payoutItemRepository;

	@Autowired
	private PayoutRecoveryRepository payoutRecoveryRepository;

	// 주문 서비스처럼, 물어본 기간(양 끝 포함)에 열린 대회의 참가권만 돌려주는 가짜 포트
	private SoldEntryPort soldEntriesReturning(List<SoldEntry> entries) {
		return (from, to) -> entries.stream()
			.filter(entry -> !entry.raceDate().isBefore(from) && !entry.raceDate().isAfter(to))
			.toList();
	}

	// 모든 판매자가 정산 계좌를 등록했다
	private PayoutCreateUseCase useCaseSelling(List<SoldEntry> entries) {
		return useCaseSelling(entries, sellerId -> true);
	}

	// 환불 마감일을 모르는 상품 서비스 (1차 대상이 없다). 최종 정산만 만들어진다
	private PayoutCreateUseCase useCaseSelling(List<SoldEntry> entries, SellerAccountPort sellerAccountPort) {
		return useCaseSelling(entries, sellerAccountPort, List.of());
	}

	private PayoutCreateUseCase useCaseSelling(List<SoldEntry> entries, SellerAccountPort sellerAccountPort,
		List<RaceSchedule> schedules) {
		// 테스트 전체가 트랜잭션 하나라 묶음별 트랜잭션은 보이지 않는다 (묶음별 트랜잭션은 PayoutCreateUseCaseTransactionTest)
		PayoutGroupCreator payoutGroupCreator =
			new PayoutGroupCreator(payoutRepository, payoutHoldRepository, new BusinessDayCalendar(),
				new PayoutLedgerUseCase(payoutRepository, payoutItemRepository, payoutRecoveryRepository));
		return new PayoutCreateUseCase(payoutRepository, payoutGroupCreator, soldEntriesReturning(entries),
			sellerAccountPort, new StubRacePort(RACE_NAMES, schedules, entries), summariesOf(entries));
	}

	// 주문 서비스처럼, 대회의 참가권을 판매자별로 묶어 가격 합과 장수를 돌려주는 가짜 포트 (1차 정산용)
	private SoldSummaryPort summariesOf(List<SoldEntry> entries) {
		return raceId -> entries.stream()
			.filter(entry -> entry.raceId() == raceId)
			.collect(java.util.stream.Collectors.groupingBy(SoldEntry::sellerId))
			.entrySet().stream()
			.map(seller -> new SoldSummary(seller.getKey(), raceId, raceId,
				seller.getValue().stream().mapToLong(SoldEntry::price).sum(), seller.getValue().size()))
			.toList();
	}

	private Payout payoutOf(int sellerId, PayoutStage stage) {
		return payoutRepository.findAll().stream()
			.filter(p -> p.getSellerId() == sellerId && p.getPayoutStage() == stage)
			.findFirst()
			.orElseThrow();
	}

	@Test
	@DisplayName("정산 내역에 상품 서비스에서 받은 대회 이름을 저장하고, 상품 서비스가 모르는 대회는 정산하지 않는다")
	void savesRaceName() {
		PayoutCreateUseCase useCase = useCaseSelling(List.of(
			new SoldEntry(100, 1, 10, 10, RACE_DATE, 50_000L),
			new SoldEntry(300, 3, 30, 30, RACE_DATE, 40_000L)
		));

		useCase.run(TARGET_DATE, NOW);

		assertThat(payoutOf(1).getRaceName()).isEqualTo("2026 한강 나이트런");
		// 대상 대회는 상품 서비스가 정한다 (PRO-214). 모르는 대회의 참가권은 실패로 남기고 정산하지 않는다
		assertThat(payoutRepository.findAll()).extracting(Payout::getSellerId).containsExactly(1);
	}

	private Payout payoutOf(int sellerId) {
		return payoutRepository.findAll().stream()
			.filter(p -> p.getSellerId() == sellerId)
			.findFirst()
			.orElseThrow();
	}

	@Test
	@DisplayName("정산 대상일에 판매자 × 대회별로 정산 내역을 만들고, 참가권마다 정산 항목을 만든다")
	void createsPayoutPerSellerAndRace() {
		PayoutCreateUseCase useCase = useCaseSelling(List.of(
			new SoldEntry(100, 1, 10, 10, RACE_DATE, 50_000L),
			new SoldEntry(101, 1, 10, 10, RACE_DATE, 50_000L),
			new SoldEntry(200, 2, 20, 20, RACE_DATE, 30_000L)
		));

		useCase.run(TARGET_DATE, NOW);

		List<Payout> payouts = payoutRepository.findAll();
		assertThat(payouts).hasSize(2);

		Payout seller1 = payouts.stream().filter(p -> p.getSellerId() == 1).findFirst().orElseThrow();
		assertThat(seller1.getRaceId()).isEqualTo(10);
		assertThat(seller1.getPayoutMonth()).isEqualTo("2026-09");
		assertThat(seller1.getStatus()).isEqualTo(PayoutStatus.CALCULATED);
		assertThat(seller1.getScheduledPayDate()).isEqualTo(LocalDate.of(2026, 10, 20));
		assertThat(seller1.getPayoutAmount()).isEqualTo(94_500L);
		// 최종은 참가권마다 판매 · 수수료 두 줄과 묶음째 선지급 차감 한 줄 (PRO-214)
		assertThat(seller1.getItems())
			.filteredOn(item -> item.getRecordType() == PayoutRecordType.SALE)
			.extracting(PayoutItem::getOrderItemId)
			.containsExactlyInAnyOrder(100, 101);
		assertThat(seller1.getItems())
			.filteredOn(item -> item.getOrderItemId() != null)
			.extracting(PayoutItem::getTargetDate)
			.containsOnly(TARGET_DATE);
	}

	@Test
	@DisplayName("정산 ID는 'ST-만든 날-16진수 4자리' 형식이다")
	void assignsPayoutCode() {
		PayoutCreateUseCase useCase = useCaseSelling(List.of(
			new SoldEntry(100, 1, 10, 10, RACE_DATE, 50_000L)
		));

		useCase.run(TARGET_DATE, NOW);

		Payout payout = payoutRepository.findAll().getFirst();
		assertThat(payout.getPayoutCode()).matches("ST-20260926-[0-9A-F]{4}");
	}

	@Test
	@DisplayName("같은 날 배치를 두 번 돌려도 정산 내역이 중복으로 만들어지지 않는다")
	void runTwiceDoesNotDuplicate() {
		PayoutCreateUseCase useCase = useCaseSelling(List.of(
			new SoldEntry(100, 1, 10, 10, RACE_DATE, 50_000L)
		));

		useCase.run(TARGET_DATE, NOW);
		useCase.run(TARGET_DATE, NOW.plusHours(1));

		assertThat(payoutRepository.findAll()).hasSize(1);
	}

	@Test
	@DisplayName("정산 계좌가 없는 판매자의 정산 내역은 만들어질 때 HELD이고, '정산 계좌 미등록' 자동 보류가 걸린다")
	void holdsPayoutOfSellerWithoutAccount() {
		// 판매자 2만 계좌가 없다
		PayoutCreateUseCase useCase = useCaseSelling(List.of(
			new SoldEntry(100, 1, 10, 10, RACE_DATE, 50_000L),
			new SoldEntry(200, 2, 20, 20, RACE_DATE, 30_000L)
		), sellerId -> sellerId != 2);

		useCase.run(TARGET_DATE, NOW);

		assertThat(payoutOf(1).getStatus()).isEqualTo(PayoutStatus.CALCULATED);
		assertThat(payoutOf(2).getStatus()).isEqualTo(PayoutStatus.HELD);

		List<PayoutHold> holds = payoutHoldRepository.findAll();
		assertThat(holds).hasSize(1);
		PayoutHold hold = holds.getFirst();
		assertThat(hold.getPayout().getId()).isEqualTo(payoutOf(2).getId());
		assertThat(hold.getReasonType()).isEqualTo(PayoutHoldReason.ACCOUNT_NOT_REGISTERED);
		assertThat(hold.getReason()).isEqualTo("정산 계좌 미등록으로 자동 보류");
		assertThat(hold.getStatus()).isEqualTo(PayoutHoldStatus.HELD);
		assertThat(hold.getHeldBy()).isEqualTo(PayoutHold.SYSTEM_MEMBER_ID);
		assertThat(hold.getHeldAt()).isEqualTo(NOW);
		assertThat(hold.getHoldCode()).matches("HD-20260926-[0-9A-F]{4}");
	}

	@Test
	@DisplayName("9/29 실행을 놓쳐도 9/30 실행에서 9/28 대회의 정산 내역을 만들고, 정산 대상일은 9/29로 남긴다")
	void catchesUpMissedDay() {
		LocalDate raceDate = LocalDate.of(2026, 9, 28);
		PayoutCreateUseCase useCase = useCaseSelling(List.of(
			new SoldEntry(100, 1, 10, 10, raceDate, 50_000L)
		));

		// 9/29 03:00 실행은 서버가 없어 돌지 않았다
		useCase.run(LocalDate.of(2026, 9, 30), LocalDateTime.of(2026, 9, 30, 3, 0));

		Payout payout = payoutOf(1);
		assertThat(payout.getPayoutMonth()).isEqualTo("2026-09");
		assertThat(payout.getScheduledPayDate()).isEqualTo(LocalDate.of(2026, 10, 20));
		assertThat(payout.getItems())
			.filteredOn(item -> item.getOrderItemId() != null)
			.extracting(PayoutItem::getTargetDate)
			.containsOnly(LocalDate.of(2026, 9, 29));
	}

	@Test
	@DisplayName("월 경계: 9/29 대회는 9/30 · 10/1 실행이 모두 봐도 9월 정산 내역 하나만 만든다 (정산 월은 실행일 · 대상일이 아니라 대회일로 정한다)")
	void monthBoundaryDoesNotDuplicate() {
		PayoutCreateUseCase useCase = useCaseSelling(List.of(
			new SoldEntry(100, 1, 10, 10, LocalDate.of(2026, 9, 29), 50_000L),
			new SoldEntry(200, 2, 20, 20, LocalDate.of(2026, 9, 30), 30_000L)
		));

		useCase.run(LocalDate.of(2026, 9, 30), LocalDateTime.of(2026, 9, 30, 3, 0));
		useCase.run(LocalDate.of(2026, 10, 1), LocalDateTime.of(2026, 10, 1, 3, 0));

		assertThat(payoutRepository.findAll()).hasSize(2);
		// 대회 9/29 → 정산 대상일 9/30 → 10/20 지급
		assertThat(payoutOf(1).getPayoutMonth()).isEqualTo("2026-09");
		assertThat(payoutOf(1).getScheduledPayDate()).isEqualTo(LocalDate.of(2026, 10, 20));
		// 대회 9/30 → 정산 대상일은 10/1이지만 정산 월은 대회일의 달 9월 → 10/20 지급 (PRO-32에서 대상일의 달에서 바꿈)
		assertThat(payoutOf(2).getPayoutMonth()).isEqualTo("2026-09");
		assertThat(payoutOf(2).getScheduledPayDate()).isEqualTo(LocalDate.of(2026, 10, 20));
	}

	@Test
	@DisplayName("되돌아보기는 어제부터 7일치 대회다. 그보다 오래된 대회와 오늘 열린 대회는 만들지 않는다")
	void looksBackSevenDays() {
		PayoutCreateUseCase useCase = useCaseSelling(List.of(
			new SoldEntry(100, 1, 10, 10, LocalDate.of(2026, 9, 22), 50_000L),
			new SoldEntry(200, 2, 20, 20, LocalDate.of(2026, 9, 23), 30_000L),
			new SoldEntry(300, 3, 50, 50, LocalDate.of(2026, 9, 29), 40_000L),
			new SoldEntry(400, 4, 40, 40, LocalDate.of(2026, 9, 30), 20_000L)
		));

		useCase.run(LocalDate.of(2026, 9, 30), LocalDateTime.of(2026, 9, 30, 3, 0));

		// 9/30 실행은 9/23 ~ 9/29 대회를 본다
		assertThat(payoutRepository.findAll())
			.extracting(Payout::getSellerId)
			.containsExactlyInAnyOrder(2, 3);
	}

	@Test
	@DisplayName("한 대회의 참가권 대회일이 서로 다르면 어느 날로 정산할지 고르지 않고 그 묶음을 만들지 않는다")
	void skipsGroupWithMixedRaceDates() {
		PayoutCreateUseCase useCase = useCaseSelling(List.of(
			new SoldEntry(100, 1, 10, 10, LocalDate.of(2026, 9, 27), 50_000L),
			new SoldEntry(101, 1, 10, 10, LocalDate.of(2026, 9, 28), 50_000L),
			new SoldEntry(200, 2, 20, 20, LocalDate.of(2026, 9, 28), 30_000L)
		));

		useCase.run(LocalDate.of(2026, 9, 30), LocalDateTime.of(2026, 9, 30, 3, 0));

		assertThat(payoutRepository.findAll())
			.extracting(Payout::getSellerId)
			.containsExactly(2);
	}

	@Test
	@DisplayName("1차: 환불 마감 9/30이면 10/1 실행이 1차 정산을 만든다. 정산 월 2026-09, 장마다 40%, 수수료 없음, 10/20 지급 예정")
	void createsFirstPayoutDayAfterRefundDeadline() {
		LocalDate refundDeadline = LocalDate.of(2026, 9, 30);
		LocalDate raceDate = LocalDate.of(2026, 10, 10);
		PayoutCreateUseCase useCase = useCaseSelling(List.of(
			new SoldEntry(100, 1, 10, 10, raceDate, 50_000L),
			new SoldEntry(101, 1, 10, 10, raceDate, 50_000L)
		), sellerId -> true, List.of(new RaceSchedule(10, "2026 한강 나이트런", raceDate, refundDeadline)));

		// 9/30(마감일 당일) 실행은 아직 1차를 만들지 않는다
		useCase.run(LocalDate.of(2026, 9, 30), LocalDateTime.of(2026, 9, 30, 3, 0));
		assertThat(payoutRepository.findAll()).isEmpty();

		useCase.run(LocalDate.of(2026, 10, 1), LocalDateTime.of(2026, 10, 1, 3, 0));

		Payout first = payoutOf(1, PayoutStage.FIRST);
		assertThat(first.getPayoutMonth()).isEqualTo("2026-09");
		assertThat(first.getScheduledPayDate()).isEqualTo(LocalDate.of(2026, 10, 20));
		assertThat(first.getSettledAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 3, 0));
		assertThat(first.getSalesAmount()).isEqualTo(100_000L);
		assertThat(first.getFeeAmount()).isZero();
		assertThat(first.getAdvanceDeductionAmount()).isZero();
		assertThat(first.getPayoutAmount()).isEqualTo(40_000L);
		assertThat(first.getStatus()).isEqualTo(PayoutStatus.CALCULATED);
		assertThat(first.getItems())
			.extracting(PayoutItem::getTargetDate)
			.containsOnly(LocalDate.of(2026, 10, 1));
		// 대회(10/10)가 아직 끝나지 않아 최종은 없다
		assertThat(payoutRepository.findAll()).hasSize(1);
	}

	@Test
	@DisplayName("R0: 환불 마감일까지 환불된 참가권은 판매 내역에서 빠져 1차가 1장분 20,000이다")
	void firstPayoutExcludesRefundBeforeDeadline() {
		LocalDate raceDate = LocalDate.of(2026, 10, 10);
		// 2장 중 1장은 마감 전에 환불되어 주문 서비스가 돌려주지 않는다
		PayoutCreateUseCase useCase = useCaseSelling(List.of(
			new SoldEntry(100, 1, 10, 10, raceDate, 50_000L)
		), sellerId -> true, List.of(new RaceSchedule(10, "2026 한강 나이트런", raceDate, LocalDate.of(2026, 9, 30))));

		useCase.run(LocalDate.of(2026, 10, 1), LocalDateTime.of(2026, 10, 1, 3, 0));

		assertThat(payoutOf(1, PayoutStage.FIRST).getPayoutAmount()).isEqualTo(20_000L);
	}

	@Test
	@DisplayName("1차는 같은 날 열리는 다른 대회의 참가권을 섞지 않는다 (판매 집계는 대회 ID로 묻는다)")
	void firstPayoutOnlyForScheduledRace() {
		LocalDate raceDate = LocalDate.of(2026, 10, 10);
		PayoutCreateUseCase useCase = useCaseSelling(List.of(
			new SoldEntry(100, 1, 10, 10, raceDate, 50_000L),
			new SoldEntry(200, 1, 20, 20, raceDate, 30_000L)
		), sellerId -> true, List.of(new RaceSchedule(10, "2026 한강 나이트런", raceDate, LocalDate.of(2026, 9, 30))));

		useCase.run(LocalDate.of(2026, 10, 1), LocalDateTime.of(2026, 10, 1, 3, 0));

		assertThat(payoutRepository.findAll())
			.extracting(Payout::getRaceId, Payout::getPayoutStage)
			.containsExactly(tuple(10, PayoutStage.FIRST));
	}

	@Test
	@DisplayName("최종: 대회 9/30이면 10/1 실행이 최종을 만든다. 정산 월 2026-09, 1차 40,000을 빼고 54,500")
	void createsFinalPayoutDeductingFirst() {
		LocalDate sameDay = LocalDate.of(2026, 9, 30);
		PayoutCreateUseCase useCase = useCaseSelling(List.of(
			new SoldEntry(100, 1, 10, 10, sameDay, 50_000L),
			new SoldEntry(101, 1, 10, 10, sameDay, 50_000L)
		), sellerId -> true, List.of(new RaceSchedule(10, "2026 한강 나이트런", sameDay, sameDay)));

		// 마감일과 대회일이 같아 한 실행에서 1차를 먼저, 최종을 나중에 만든다
		useCase.run(LocalDate.of(2026, 10, 1), LocalDateTime.of(2026, 10, 1, 3, 0));

		Payout first = payoutOf(1, PayoutStage.FIRST);
		Payout last = payoutOf(1, PayoutStage.FINAL);
		assertThat(first.getPayoutMonth()).isEqualTo("2026-09");
		assertThat(last.getPayoutMonth()).isEqualTo("2026-09");
		assertThat(last.getScheduledPayDate()).isEqualTo(LocalDate.of(2026, 10, 20));
		assertThat(last.getSalesAmount()).isEqualTo(100_000L);
		assertThat(last.getFeeAmount()).isEqualTo(5_500L);
		assertThat(last.getAdvanceDeductionAmount()).isEqualTo(40_000L);
		assertThat(last.getPayoutAmount()).isEqualTo(54_500L);
		assertThat(first.getPayoutAmount() + last.getPayoutAmount()).isEqualTo(94_500L);
	}

	@Test
	@DisplayName("1차가 보류 중이면 최종을 만들고 같은 사유로 보류한다")
	void holdsFinalWhenFirstIsHeld() {
		LocalDate raceDate = LocalDate.of(2026, 9, 30);
		List<SoldEntry> entries = List.of(new SoldEntry(100, 1, 10, 10, raceDate, 50_000L));
		List<RaceSchedule> schedules =
			List.of(new RaceSchedule(10, "2026 한강 나이트런", raceDate, LocalDate.of(2026, 9, 20)));

		// 1차를 만들 때는 계좌가 없어 자동 보류가 걸렸다
		useCaseSelling(entries, sellerId -> false, schedules)
			.run(LocalDate.of(2026, 9, 21), LocalDateTime.of(2026, 9, 21, 3, 0));
		// 최종을 만들 때는 계좌가 있지만 1차가 아직 보류 중이다
		useCaseSelling(entries, sellerId -> true, schedules)
			.run(LocalDate.of(2026, 10, 1), LocalDateTime.of(2026, 10, 1, 3, 0));

		Payout last = payoutOf(1, PayoutStage.FINAL);
		assertThat(last.getStatus()).isEqualTo(PayoutStatus.HELD);
		PayoutHold finalHold = payoutHoldRepository.findAll().stream()
			.filter(hold -> hold.getPayout().getId() == last.getId())
			.findFirst()
			.orElseThrow();
		assertThat(finalHold.getReasonType()).isEqualTo(PayoutHoldReason.ACCOUNT_NOT_REGISTERED);
		assertThat(finalHold.getReason()).isEqualTo("1차 정산 보류 중이라 최종 정산도 자동 보류");
	}

	@Test
	@DisplayName("1차가 실패한 실행에서 최종이 먼저 만들어지면, 다음 실행은 1차를 만들지 않아 선금이 두 번 나가지 않는다")
	void skipsFirstWhenFinalAlreadyExists() {
		// 환불 마감 9/28, 대회 10/1: 10/2 실행의 되돌아보기 기간에 1차와 최종이 함께 들어온다
		LocalDate raceDate = LocalDate.of(2026, 10, 1);
		List<SoldEntry> entries = List.of(new SoldEntry(100, 1, 10, 10, raceDate, 50_000L));
		List<RaceSchedule> schedules =
			List.of(new RaceSchedule(10, "2026 한강 나이트런", raceDate, LocalDate.of(2026, 9, 28)));
		boolean[] summaryDown = {true};
		SoldSummaryPort working = summariesOf(entries);
		SoldSummaryPort flaky = raceId -> {
			if (summaryDown[0]) {
				throw new IllegalStateException("주문 서비스 응답 없음");
			}
			return working.summarizeSold(raceId);
		};
		PayoutGroupCreator payoutGroupCreator =
			new PayoutGroupCreator(payoutRepository, payoutHoldRepository, new BusinessDayCalendar(),
				new PayoutLedgerUseCase(payoutRepository, payoutItemRepository, payoutRecoveryRepository));
		PayoutCreateUseCase useCase = new PayoutCreateUseCase(payoutRepository, payoutGroupCreator,
			soldEntriesReturning(entries), sellerId -> true, new StubRacePort(RACE_NAMES, schedules, entries), flaky);

		// 10/2: 1차는 판매 집계를 못 받아 실패, 최종은 선지급 차감 없이 판매자 정산액 전부로 만들어진다
		useCase.run(LocalDate.of(2026, 10, 2), LocalDateTime.of(2026, 10, 2, 3, 0));
		assertThat(payoutOf(1, PayoutStage.FINAL).getPayoutAmount()).isEqualTo(47_250L);

		// 10/3: 판매 집계가 돌아와도 최종이 이미 있으므로 1차를 만들지 않는다
		summaryDown[0] = false;
		useCase.run(LocalDate.of(2026, 10, 3), LocalDateTime.of(2026, 10, 3, 3, 0));

		assertThat(payoutRepository.findAll())
			.extracting(Payout::getPayoutStage)
			.containsExactly(PayoutStage.FINAL);
	}

	@Test
	@DisplayName("1차 배치도 두 번 돌려도 중복이 없고, 놓친 날은 다음 실행이 따라잡는다 (마감 9/28, 9/29 실행을 놓침)")
	void firstPayoutRunTwiceAndCatchesUp() {
		LocalDate raceDate = LocalDate.of(2026, 10, 10);
		PayoutCreateUseCase useCase = useCaseSelling(List.of(
			new SoldEntry(100, 1, 10, 10, raceDate, 50_000L)
		), sellerId -> true, List.of(new RaceSchedule(10, "2026 한강 나이트런", raceDate, LocalDate.of(2026, 9, 28))));

		useCase.run(LocalDate.of(2026, 9, 30), LocalDateTime.of(2026, 9, 30, 3, 0));
		useCase.run(LocalDate.of(2026, 10, 1), LocalDateTime.of(2026, 10, 1, 3, 0));

		assertThat(payoutRepository.findAll()).hasSize(1);
		Payout first = payoutOf(1, PayoutStage.FIRST);
		assertThat(first.getPayoutMonth()).isEqualTo("2026-09");
		assertThat(first.getItems())
			.extracting(PayoutItem::getTargetDate)
			.containsOnly(LocalDate.of(2026, 9, 29));
	}
}
