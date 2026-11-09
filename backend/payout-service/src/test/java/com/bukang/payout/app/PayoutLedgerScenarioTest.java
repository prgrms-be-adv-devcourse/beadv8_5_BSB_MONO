package com.bukang.payout.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.payout.app.PayoutCreateStepUseCase.CourseSale;
import com.bukang.payout.app.PayoutCreateStepUseCase.FinalPayoutSource;
import com.bukang.payout.app.PayoutCreateStepUseCase.FirstPayoutSource;
import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutAccount;
import com.bukang.payout.domain.PayoutHold;
import com.bukang.payout.domain.PayoutItem;
import com.bukang.payout.domain.PayoutRecordType;
import com.bukang.payout.domain.PayoutRecovery;
import com.bukang.payout.domain.PayoutRecoveryCompletion;
import com.bukang.payout.domain.PayoutRecoveryReason;
import com.bukang.payout.domain.PayoutRecoveryStatus;
import com.bukang.payout.domain.PayoutStage;
import com.bukang.payout.domain.PayoutStatus;
import com.bukang.payout.domain.exception.PayoutRecoveryConflictException;
import com.bukang.payout.out.PayoutClaimRepository;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRecoveryRepository;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.out.SoldEntry;

import jakarta.persistence.EntityManager;

/**
 * 원장 방식 정산의 금액 시나리오 (PRO-214 완료 기준 1)
 * 대회 A, 판매자 1명, 참가권 5만 원 × 10장(101~110). 1장의 1차 몫 20,000, 수수료 2,750, 최종 몫 47,250
 * 환불 기한 2026-10-31 토 → 1차 생성 11/1, 지급 11/20 금. 대회일 12/13 일 → 최종 생성 12/14, 지급 2027-01-20 수
 * 모든 시나리오에서 정산 내역마다 원장 줄 합 = 지급액(payout_amount)인지도 확인한다
 */
@DataJpaTest
@ActiveProfiles("test")
class PayoutLedgerScenarioTest {
	private static final long SELLER_ID = 1;
	private static final long RACE_ID = 7;
	private static final long ADMIN_ID = 99;
	private static final long PRICE = 50_000L;
	private static final LocalDate REFUND_DEADLINE = LocalDate.of(2026, 10, 31);
	private static final LocalDate RACE_DATE = LocalDate.of(2026, 12, 13);
	private static final LocalDate FIRST_PAY_DATE = LocalDate.of(2026, 11, 20);
	private static final LocalDate FINAL_PAY_DATE = LocalDate.of(2027, 1, 20);

	@Autowired
	private PayoutRepository payoutRepository;

	@Autowired
	private PayoutItemRepository payoutItemRepository;

	@Autowired
	private PayoutHoldRepository payoutHoldRepository;

	@Autowired
	private PayoutRecoveryRepository payoutRecoveryRepository;

	@Autowired
	private PayoutClaimRepository payoutClaimRepository;

	@Autowired
	private EntityManager entityManager;

	private PayoutLedgerUseCase ledgerUseCase;
	private PayoutCreateStepUseCase creator;
	private PayoutPayUseCase payUseCase;
	private PayoutRaceCancelUseCase raceCancelUseCase;
	private PayoutClaimUseCase claimUseCase;

	@BeforeEach
	void setUp() {
		BusinessDayCalendar calendar = new BusinessDayCalendar();
		ledgerUseCase = new PayoutLedgerUseCase(payoutRepository, payoutItemRepository, payoutRecoveryRepository);
		creator = new PayoutCreateStepUseCase(payoutRepository, calendar, ledgerUseCase,
			new PayoutHoldUseCase(payoutRepository, payoutHoldRepository, calendar));
		raceCancelUseCase = new PayoutRaceCancelUseCase(payoutRepository, ledgerUseCase);
		claimUseCase = new PayoutClaimUseCase(payoutRepository, payoutItemRepository, payoutRecoveryRepository,
			payoutClaimRepository, (claimCode, amount, dueDate) -> Optional.empty());
		PayoutPayStepUseCase payStepUseCase = new PayoutPayStepUseCase(payoutRepository, raceCancelUseCase);
		payUseCase = new PayoutPayUseCase(payStepUseCase, transfer -> {
		});
	}

	private FinalPayoutSource finalSource(int... orderItemIds) {
		List<SoldEntry> entries = Arrays.stream(orderItemIds)
			.mapToObj(id -> new SoldEntry(id, SELLER_ID, RACE_ID, RACE_ID, RACE_DATE, PRICE))
			.toList();
		return new FinalPayoutSource(SELLER_ID, RACE_ID, "대회 A", true, entries);
	}

	private static int[] range(int from, int to) {
		return java.util.stream.IntStream.rangeClosed(from, to).toArray();
	}

	private void createFirst() {
		creator.createFirst(new FirstPayoutSource(SELLER_ID, RACE_ID, "대회 A", true,
			List.of(new CourseSale(RACE_ID, PRICE * 10, 10))), REFUND_DEADLINE,
			REFUND_DEADLINE.plusDays(1).atTime(3, 0));
	}

	private void createFinal(int... orderItemIds) {
		creator.createFinal(finalSource(orderItemIds), RACE_DATE, RACE_DATE.plusDays(1).atTime(3, 0));
	}

	private void pay(LocalDate payDate) {
		payUseCase.run(payDate, payDate.atTime(9, 0));
	}

	private PayoutRecovery requestRecovery(long orderItemId, LocalDateTime at) {
		return ledgerUseCase.recoverScheduleChange(SELLER_ID, RACE_ID, RACE_ID, orderItemId, PRICE, at)
			.orElseThrow();
	}

	// 04:00 배치가 청구를 만들고, 관리자가 그 청구의 입금을 확인한다
	private void claimAndConfirm(LocalDate batchDate, LocalDateTime confirmedAt) {
		claimUseCase.run(batchDate, batchDate.atTime(4, 0));
		entityManager.flush();
		entityManager.clear();
		claimUseCase.confirm(payoutClaimRepository.findAll().getFirst().getId(), ADMIN_ID, confirmedAt);
		entityManager.flush();
	}

	private Payout payout(PayoutStage stage) {
		entityManager.flush();
		entityManager.clear();
		return payoutRepository.findFirstBySellerIdAndRaceIdAndPayoutStageOrderByIdAsc(SELLER_ID, RACE_ID, stage)
			.orElseThrow();
	}

	private long lineSum(Payout payout) {
		return payout.getItems().stream().mapToLong(PayoutItem::getAmount).sum();
	}

	private long balance(PayoutAccount account) {
		return ledgerUseCase.balance(SELLER_ID, RACE_ID, account);
	}

	// 판매자가 실제로 받은 돈 = 지급된 정산 내역 지급액 합 − 입금으로 돌려준 회수액
	private long sellerReceived() {
		entityManager.flush();
		long paid = payoutRepository.findByRaceId(RACE_ID).stream()
			.filter(payout -> payout.getStatus() == PayoutStatus.지급_완료)
			.mapToLong(Payout::getPayoutAmount)
			.sum();
		long deposited = payoutItemRepository.findAll().stream()
			.filter(item -> item.getRecordType() == PayoutRecordType.회수_입금)
			.mapToLong(PayoutItem::getAmount)
			.sum();
		return paid + deposited;
	}

	// 회수마다 (참가권, 완료 방식, 완료한 회원)
	private void assertCompletions(Tuple... expected) {
		entityManager.flush();
		assertThat(payoutRecoveryRepository.findAll())
			.extracting(PayoutRecovery::getOrderItemId, PayoutRecovery::getCompletion, PayoutRecovery::getCompletedBy)
			.containsExactlyInAnyOrder(expected);
	}

	// 최종 정산 내역에는 1차 정산금 차감 줄이 하나만 있고, 금액은 만들 때 남은 1차 정산금(선금 잔고)을 뺀 값이다
	// 줄 순서는 보지 않는다
	private void assertAdvanceOffset(Payout last, long expected) {
		assertThat(last.getItems())
			.filteredOn(item -> item.getRecordType() == PayoutRecordType.선지급_차감)
			.extracting(PayoutItem::getAmount)
			.containsExactly(expected);
	}

	private void assertEveryPayoutLineSumMatches() {
		entityManager.flush();
		entityManager.clear();
		assertThat(payoutRepository.findByRaceId(RACE_ID))
			.allSatisfy(payout -> assertThat(lineSum(payout)).isEqualTo(payout.getPayoutAmount()));
	}

	@Test
	@DisplayName("환불 없음: 1차 200,000, 최종 272,500, 판매자가 받은 돈 472,500")
	void noRefund() {
		createFirst();
		pay(FIRST_PAY_DATE);
		createFinal(range(101, 110));
		pay(FINAL_PAY_DATE);

		assertThat(lineSum(payout(PayoutStage.일차_정산))).isEqualTo(200_000L);
		Payout last = payout(PayoutStage.최종_정산);
		assertThat(lineSum(last)).isEqualTo(272_500L);
		assertThat(last.getAdvanceDeductionAmount()).isEqualTo(200_000L);
		assertAdvanceOffset(last, -200_000L);
		assertThat(sellerReceived()).isEqualTo(472_500L);
		assertThat(balance(PayoutAccount.선금)).isZero();
		assertThat(balance(PayoutAccount.번_돈)).isEqualTo(472_500L);
		assertEveryPayoutLineSumMatches();
	}

	@Test
	@DisplayName("1차 없이 최종을 만들면 남은 1차 정산금이 0이라 1차 정산금 차감 줄도 0원으로 하나 남는다")
	void finalWithoutFirst() {
		createFinal(range(101, 110));

		Payout last = payout(PayoutStage.최종_정산);
		assertThat(last.getAdvanceDeductionAmount()).isZero();
		assertAdvanceOffset(last, 0L);
		assertThat(lineSum(last)).isEqualTo(472_500L);
		assertEveryPayoutLineSumMatches();
	}

	@Test
	@DisplayName("코스가 둘인 대회: 1차 정산금은 코스마다 한 줄, 최종 판매 · 수수료 줄에도 코스가 남아 코스별로 더할 수 있다")
	void twoCourses() {
		long course10k = 71;
		long course5k = 72;
		creator.createFirst(new FirstPayoutSource(SELLER_ID, RACE_ID, "대회 A", true, List.of(
			new CourseSale(course10k, 50_000L * 6, 6),
			new CourseSale(course5k, 30_000L * 4, 4))), REFUND_DEADLINE, REFUND_DEADLINE.plusDays(1).atTime(3, 0));
		pay(FIRST_PAY_DATE);
		List<SoldEntry> entries = new ArrayList<>();
		for (long id = 101; id <= 106; id++) {
			entries.add(new SoldEntry(id, SELLER_ID, RACE_ID, course10k, RACE_DATE, 50_000L));
		}
		for (long id = 107; id <= 110; id++) {
			entries.add(new SoldEntry(id, SELLER_ID, RACE_ID, course5k, RACE_DATE, 30_000L));
		}
		creator.createFinal(new FinalPayoutSource(SELLER_ID, RACE_ID, "대회 A", true, entries), RACE_DATE,
			RACE_DATE.plusDays(1).atTime(3, 0));

		// 10K 6장 × 20,000 + 5K 4장 × 12,000
		assertThat(payout(PayoutStage.일차_정산).getItems())
			.extracting(PayoutItem::getRaceCourseId, PayoutItem::getAmount)
			.containsExactlyInAnyOrder(tuple(course10k, 120_000L), tuple(course5k, 48_000L));
		// 10K 6장 × (50,000 − 2,750) = 283,500, 5K 4장 × (30,000 − 1,650) = 113,400
		Payout last = payout(PayoutStage.최종_정산);
		assertThat(courseSum(last, course10k)).isEqualTo(283_500L);
		assertThat(courseSum(last, course5k)).isEqualTo(113_400L);
		assertThat(lineSum(last)).isEqualTo(283_500L + 113_400L - 168_000L);
		assertEveryPayoutLineSumMatches();
	}

	private long courseSum(Payout payout, long raceCourseId) {
		return payout.getItems().stream()
			.filter(item -> Long.valueOf(raceCourseId).equals(item.getRaceCourseId()))
			.mapToLong(PayoutItem::getAmount)
			.sum();
	}

	@Test
	@DisplayName("1차 지급 뒤 3장 환불, 최종 생성 때 모두 상계: 최종 130,750, 받은 돈 330,750")
	void threeRefundsAfterFirstPay() {
		createFirst();
		pay(FIRST_PAY_DATE);
		requestRecovery(108, LocalDateTime.of(2026, 11, 26, 10, 0));
		requestRecovery(109, LocalDateTime.of(2026, 11, 27, 10, 0));
		requestRecovery(110, LocalDateTime.of(2026, 11, 28, 10, 0));
		assertThat(balance(PayoutAccount.갚을_돈)).isEqualTo(60_000L);

		createFinal(range(101, 107));

		Payout last = payout(PayoutStage.최종_정산);
		// 7장 × 47,250 = 330,750 − 남은 1차 정산금 140,000 − 상계 60,000 = 130,750
		assertThat(lineSum(last)).isEqualTo(130_750L);
		// 남은 1차 정산금 = 200,000 − 회수 요청 3건 × 20,000 = 140,000
		assertThat(last.getAdvanceDeductionAmount()).isEqualTo(140_000L);
		assertAdvanceOffset(last, -140_000L);
		assertThat(balance(PayoutAccount.갚을_돈)).isZero();
		// 최종 전 입금 경로(예전 회수 한 건 완료 API)는 없앴다. 세 건 모두 최종 정산 내역에서 빼서 상계로 완료
		assertCompletions(
			tuple(108L, PayoutRecoveryCompletion.상계, PayoutHold.SYSTEM_MEMBER_ID),
			tuple(109L, PayoutRecoveryCompletion.상계, PayoutHold.SYSTEM_MEMBER_ID),
			tuple(110L, PayoutRecoveryCompletion.상계, PayoutHold.SYSTEM_MEMBER_ID));
		pay(FINAL_PAY_DATE);
		assertThat(sellerReceived()).isEqualTo(330_750L);
		assertEveryPayoutLineSumMatches();
	}

	@Test
	@DisplayName("108번 1차 지급 전 환불: 요청 즉시 1차 정산 내역에서 상계해 지급 180,000, 회수는 바로 완료")
	void refundBeforeFirstPay() {
		createFirst();
		PayoutRecovery r108 = requestRecovery(108, LocalDateTime.of(2026, 11, 1, 10, 0));

		assertThat(r108.getStatus()).isEqualTo(PayoutRecoveryStatus.회수_완료);
		assertThat(r108.getCompletion()).isEqualTo(PayoutRecoveryCompletion.상계);
		assertThat(r108.getCompletedBy()).isEqualTo(PayoutHold.SYSTEM_MEMBER_ID);
		assertThat(lineSum(payout(PayoutStage.일차_정산))).isEqualTo(180_000L);
		assertThat(balance(PayoutAccount.갚을_돈)).isZero();

		pay(FIRST_PAY_DATE);
		assertThat(sellerReceived()).isEqualTo(180_000L);
		// 최종: 9장 × 47,250 − 남은 1차 정산금 180,000 = 245,250
		createFinal(101, 102, 103, 104, 105, 106, 107, 109, 110);
		assertThat(lineSum(payout(PayoutStage.최종_정산))).isEqualTo(245_250L);
		pay(FINAL_PAY_DATE);
		assertThat(sellerReceived()).isEqualTo(425_250L);
		assertEveryPayoutLineSumMatches();
	}

	@Test
	@DisplayName("1차 지급 뒤 8장 환불: 최종에서 54,500 상계해 0원, 남은 105,500 청구를 입금 확인하면 8건 모두 완료")
	void eightRefundsUnpaid() {
		createFirst();
		pay(FIRST_PAY_DATE);
		List<PayoutRecovery> recoveries = Arrays.stream(range(103, 110))
			.mapToObj(id -> requestRecovery(id, LocalDateTime.of(2026, 11, 26, 10, 0)))
			.toList();
		assertThat(balance(PayoutAccount.갚을_돈)).isEqualTo(160_000L);

		createFinal(101, 102);
		assertThat(lineSum(payout(PayoutStage.최종_정산))).isZero();
		assertThat(balance(PayoutAccount.갚을_돈)).isEqualTo(105_500L);
		assertThat(payoutRecoveryRepository.findAll())
			.allSatisfy(recovery -> assertThat(recovery.getStatus()).isEqualTo(PayoutRecoveryStatus.회수_요청));

		pay(FINAL_PAY_DATE);
		claimAndConfirm(FINAL_PAY_DATE, LocalDateTime.of(2027, 1, 25, 10, 0));

		assertThat(balance(PayoutAccount.갚을_돈)).isZero();
		// 청구 하나(105,500)를 확인하면 그 대회의 요청 중인 회수 8건이 모두 입금으로 끝난다
		assertThat(payoutRecoveryRepository.findAll())
			.extracting(PayoutRecovery::getStatus, PayoutRecovery::getCompletion, PayoutRecovery::getCompletedBy)
			.hasSize(recoveries.size())
			.containsOnly(tuple(PayoutRecoveryStatus.회수_완료, PayoutRecoveryCompletion.입금, ADMIN_ID));
		// 회수 입금 줄은 청구 하나에 한 줄이고, 회수가 아니라 판매자 × 대회에 붙는다
		assertThat(payoutItemRepository.findAll())
			.filteredOn(item -> item.getRecordType() == PayoutRecordType.회수_입금)
			.extracting(PayoutItem::getAmount)
			.containsExactly(-105_500L);
		assertThat(sellerReceived()).isEqualTo(94_500L);
		assertEveryPayoutLineSumMatches();
	}

	@Test
	@DisplayName("대회 취소 (1차 지급 뒤): 회수 200,000, 요청 뒤 선금 0 · 갚을_돈 200,000, 청구 입금 확인 뒤 0, 최종 없음")
	void raceCanceledAfterFirstPay() {
		createFirst();
		pay(FIRST_PAY_DATE);
		raceCancelUseCase.cancelRace(RACE_ID, LocalDateTime.of(2026, 12, 1, 10, 0));

		assertThat(balance(PayoutAccount.선금)).isZero();
		assertThat(balance(PayoutAccount.갚을_돈)).isEqualTo(200_000L);
		PayoutRecovery recovery = payoutRecoveryRepository.findAll().getFirst();
		assertThat(recovery.getAmount()).isEqualTo(200_000L);

		// 대회 취소 회수의 청구는 취소 다음 날 배치에서 만든다
		claimAndConfirm(LocalDate.of(2026, 12, 2), LocalDateTime.of(2026, 12, 5, 10, 0));
		assertThat(balance(PayoutAccount.갚을_돈)).isZero();
		assertCompletions(tuple(null, PayoutRecoveryCompletion.입금, ADMIN_ID));
		assertThat(sellerReceived()).isZero();
		assertThat(payoutRepository.findFirstBySellerIdAndRaceIdAndPayoutStageOrderByIdAsc(SELLER_ID, RACE_ID,
			PayoutStage.최종_정산)).isEmpty();
		assertEveryPayoutLineSumMatches();
	}

	@Test
	@DisplayName("최종 지급 뒤 대회 취소: 회수 472,500(남은 1차 정산금 0 + 번 돈 472,500), 회수 두 줄이 생긴다")
	void raceCanceledAfterFinalPay() {
		createFirst();
		pay(FIRST_PAY_DATE);
		createFinal(range(101, 110));
		pay(FINAL_PAY_DATE);
		raceCancelUseCase.cancelRace(RACE_ID, LocalDateTime.of(2027, 1, 25, 10, 0));

		// 선금: 1차 +200,000, 최종 차감 −200,000 → 0
		// 번_돈: 판매 10장 × 50,000 − 수수료 10장 × 2,750 = 472,500 (판매자가 받은 돈 200,000 + 272,500과 같다)
		PayoutRecovery recovery = payoutRecoveryRepository.findAll().getFirst();
		assertThat(payoutRecoveryRepository.findAll()).hasSize(1);
		assertThat(recovery.getReasonType()).isEqualTo(PayoutRecoveryReason.대회_취소);
		assertThat(recovery.getAmount()).isEqualTo(472_500L);
		assertThat(payoutItemRepository.findAll())
			.filteredOn(item -> item.getRecordType() == PayoutRecordType.회수_요청
				|| item.getRecordType() == PayoutRecordType.갚을_돈_추가)
			.extracting(PayoutItem::getRecordType, PayoutItem::getAmount)
			.containsExactlyInAnyOrder(
				tuple(PayoutRecordType.회수_요청, -472_500L),
				tuple(PayoutRecordType.갚을_돈_추가, 472_500L));
		assertThat(balance(PayoutAccount.갚을_돈)).isEqualTo(472_500L);
	}

	@Test
	@DisplayName("1차 지급 전 대회 취소: 반대 줄로 되돌려 선금 0, 1차 정산 내역 줄 합 0, 회수 없음")
	void raceCanceledBeforeFirstPay() {
		createFirst();
		raceCancelUseCase.cancelRace(RACE_ID, LocalDateTime.of(2026, 11, 1, 10, 0));

		Payout first = payout(PayoutStage.일차_정산);
		assertThat(first.getStatus()).isEqualTo(PayoutStatus.취소);
		assertThat(lineSum(first)).isZero();
		assertThat(first.getItems()).extracting(PayoutItem::getRecordType)
			.containsExactlyInAnyOrder(PayoutRecordType.선금, PayoutRecordType.선금_취소);
		assertThat(balance(PayoutAccount.선금)).isZero();
		assertThat(payoutRecoveryRepository.findAll()).isEmpty();
		assertEveryPayoutLineSumMatches();
	}

	@Test
	@DisplayName("TC-RECOV-2-1 같은 참가권으로 회수를 두 번 요청하면 두 번째도 같은 회수를 돌려주고, 원장 줄은 한 번만 남는다")
	void duplicateRecoveryRequest() {
		createFirst();
		pay(FIRST_PAY_DATE);
		PayoutRecovery first = requestRecovery(108, LocalDateTime.of(2026, 11, 26, 10, 0));

		PayoutRecovery again = requestRecovery(108, LocalDateTime.of(2026, 11, 26, 11, 0));

		assertThat(again.getId()).isEqualTo(first.getId());
		entityManager.flush();
		assertThat(payoutItemRepository.findAll())
			.filteredOn(item -> Long.valueOf(108L).equals(item.getOrderItemId()))
			.extracting(PayoutItem::getRecordType)
			.containsExactlyInAnyOrder(PayoutRecordType.회수_요청, PayoutRecordType.갚을_돈_추가);
	}

	@Test
	@DisplayName("최종 정산을 만든 뒤 일정 변경 회수를 요청하면 충돌(409)이다")
	void recoveryAfterFinalConflicts() {
		createFirst();
		pay(FIRST_PAY_DATE);
		createFinal(range(101, 110));

		assertThatThrownBy(() -> requestRecovery(108, LocalDateTime.of(2026, 12, 15, 10, 0)))
			.isInstanceOf(PayoutRecoveryConflictException.class);
	}

	@Test
	@DisplayName("1차 정산이 없으면 일정 변경 회수는 빈 값이고, 원장 줄 · 회수가 생기지 않는다")
	void recoveryWithoutFirstIsEmpty() {
		assertThat(ledgerUseCase.recoverScheduleChange(SELLER_ID, RACE_ID, RACE_ID, 108, PRICE,
			LocalDateTime.of(2026, 10, 20, 10, 0))).isEmpty();

		entityManager.flush();
		assertThat(payoutItemRepository.findAll()).isEmpty();
		assertThat(payoutRecoveryRepository.findAll()).isEmpty();
	}

	// 1차 지급 전 108번 환불(상계로 완료) 뒤 대회 취소. 판매자가 받은 돈이 없으므로 갚을 돈도 없어야 한다 (R9)
	private void refundBeforeFirstPayThenCancelRace() {
		createFirst();
		requestRecovery(108, LocalDateTime.of(2026, 11, 5, 10, 0));
		raceCancelUseCase.cancelRace(RACE_ID, LocalDateTime.of(2026, 11, 10, 10, 0));
		entityManager.flush();
		entityManager.clear();
	}

	// 108번 환불(상계로 완료) 뒤 1차를 지급하는 중에 대회가 취소되고, 캐시 서비스가 그 지급을 거절한다 (PR #284 리뷰)
	// 거절로 1차를 취소로 닫으면 지급 전 취소와 같으므로, 상계로 끝난 회수도 되돌려야 한다 (R9)
	private void refundThenFirstPayRejectedAfterRaceCancel() {
		createFirst();
		requestRecovery(108, LocalDateTime.of(2026, 11, 5, 10, 0));
		Payout first = payout(PayoutStage.일차_정산);
		first.startPaying(FIRST_PAY_DATE.atTime(4, 0));
		raceCancelUseCase.cancelRace(RACE_ID, FIRST_PAY_DATE.atTime(4, 1));
		new PayoutPayStepUseCase(payoutRepository, raceCancelUseCase)
			.revert(first.getId(), FIRST_PAY_DATE.atTime(4, 2), "캐시 서비스 거절");
		entityManager.flush();
		entityManager.clear();
	}

	@Test
	@DisplayName("TC-CANCEL-6-1 1차 지급이 대회 취소 뒤 거절돼 취소로 닫히면, 상계로 끝난 회수를 되돌려 갚을 돈이 0원이다")
	void rejectedFirstAfterRaceCancelRevertsOffsetRecovery() {
		refundThenFirstPayRejectedAfterRaceCancel();

		assertThat(payout(PayoutStage.일차_정산).getStatus()).isEqualTo(PayoutStatus.취소);
		assertThat(balance(PayoutAccount.갚을_돈)).isZero();
		assertThat(balance(PayoutAccount.선금)).isZero();
		assertThat(payoutRecoveryRepository.findAll())
			.filteredOn(recovery -> Long.valueOf(108L).equals(recovery.getOrderItemId()))
			.extracting(PayoutRecovery::getStatus)
			.containsExactly(PayoutRecoveryStatus.회수_취소);
		assertThat(payoutRecoveryRepository.findAll())
			.filteredOn(recovery -> recovery.getReasonType() == PayoutRecoveryReason.대회_취소)
			.isEmpty();
	}

	@Test
	@DisplayName("TC-CANCEL-1-1 1차 지급 전 환불이 상계로 끝난 뒤 대회가 취소되면 갚을 돈은 0원이다")
	void raceCanceledAfterOffsetRefundLeavesNoReceivable() {
		refundBeforeFirstPayThenCancelRace();

		assertThat(balance(PayoutAccount.갚을_돈)).isZero();
	}

	@Test
	@DisplayName("TC-CANCEL-2-1 1차 지급 전 환불이 상계로 끝난 뒤 대회가 취소되면 1차 정산금 잔고는 0원이다")
	void raceCanceledAfterOffsetRefundLeavesNoAdvance() {
		refundBeforeFirstPayThenCancelRace();

		assertThat(balance(PayoutAccount.선금)).isZero();
	}

	@Test
	@DisplayName("TC-CANCEL-3-1 되돌린 108번 회수는 회수 취소 상태가 된다")
	void raceCanceledAfterOffsetRefundCancelsRecovery() {
		refundBeforeFirstPayThenCancelRace();

		assertThat(payoutRecoveryRepository.findAll())
			.filteredOn(recovery -> Long.valueOf(108L).equals(recovery.getOrderItemId()))
			.extracting(PayoutRecovery::getStatus)
			.containsExactly(PayoutRecoveryStatus.회수_취소);
	}

	@Test
	@DisplayName("TC-CANCEL-4-1 받은 돈이 없으면 대회 취소 회수를 만들지 않는다")
	void raceCanceledAfterOffsetRefundCreatesNoRaceCancelRecovery() {
		refundBeforeFirstPayThenCancelRace();

		assertThat(payoutRecoveryRepository.findAll())
			.filteredOn(recovery -> recovery.getReasonType() == PayoutRecoveryReason.대회_취소)
			.isEmpty();
	}

	@Test
	@DisplayName("TC-CANCEL-5-1 1차 지급 뒤 요청 중인 회수는 대회 취소로 되돌리지 않는다")
	void raceCanceledKeepsRecoveryRequestedAfterFirstPay() {
		createFirst();
		pay(FIRST_PAY_DATE);
		requestRecovery(108, LocalDateTime.of(2026, 11, 26, 10, 0));
		raceCancelUseCase.cancelRace(RACE_ID, LocalDateTime.of(2026, 12, 1, 10, 0));
		entityManager.flush();
		entityManager.clear();

		assertThat(payoutRecoveryRepository.findAll())
			.filteredOn(recovery -> Long.valueOf(108L).equals(recovery.getOrderItemId()))
			.extracting(PayoutRecovery::getStatus)
			.containsExactly(PayoutRecoveryStatus.회수_요청);
	}
}
