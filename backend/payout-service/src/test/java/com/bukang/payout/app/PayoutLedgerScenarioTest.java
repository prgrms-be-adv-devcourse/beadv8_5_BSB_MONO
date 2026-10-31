package com.bukang.payout.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRecoveryRepository;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.out.SoldEntry;

import jakarta.persistence.EntityManager;

/**
 * 원장 방식 정산의 금액 시나리오 (PRO-214 완료 기준 1)
 * 대회 A, 판매자 1명, 참가권 5만 원 × 10장(101~110). 1장의 1차 몫 20,000, 수수료 2,750, 최종 몫 47,250
 * 환불 기한 2026-10-31 토 → 1차 생성 11/1, 지급 11/2 월. 대회일 12/13 일 → 최종 생성 12/14, 지급 12/15 화
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
	private static final LocalDate FIRST_PAY_DATE = LocalDate.of(2026, 11, 2);
	private static final LocalDate FINAL_PAY_DATE = LocalDate.of(2026, 12, 15);

	@Autowired
	private PayoutRepository payoutRepository;

	@Autowired
	private PayoutItemRepository payoutItemRepository;

	@Autowired
	private PayoutHoldRepository payoutHoldRepository;

	@Autowired
	private PayoutRecoveryRepository payoutRecoveryRepository;

	@Autowired
	private EntityManager entityManager;

	private PayoutLedgerUseCase ledgerUseCase;
	private PayoutCreateStepUseCase creator;
	private PayoutPayUseCase payUseCase;
	private PayoutRaceCancelUseCase raceCancelUseCase;

	@BeforeEach
	void setUp() {
		BusinessDayCalendar calendar = new BusinessDayCalendar();
		ledgerUseCase = new PayoutLedgerUseCase(payoutRepository, payoutItemRepository, payoutRecoveryRepository);
		creator = new PayoutCreateStepUseCase(payoutRepository, calendar, ledgerUseCase,
			new PayoutHoldUseCase(payoutRepository, payoutHoldRepository, calendar));
		payUseCase = new PayoutPayUseCase(new PayoutPayStepUseCase(payoutRepository), transfer -> {
		});
		raceCancelUseCase = new PayoutRaceCancelUseCase(payoutRepository, ledgerUseCase);
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
			.filter(payout -> payout.getStatus() == PayoutStatus.PAID)
			.mapToLong(Payout::getPayoutAmount)
			.sum();
		long deposited = payoutItemRepository.findAll().stream()
			.filter(item -> item.getRecordType() == PayoutRecordType.RECOVERY_RECEIVED)
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

	// 최종 정산 내역에는 1차 정산금 차감 줄이 하나만 있고, 금액은 만들 때 남은 1차 정산금(ADVANCE 잔고)을 뺀 값이다
	// 줄 순서는 보지 않는다
	private void assertAdvanceOffset(Payout last, long expected) {
		assertThat(last.getItems())
			.filteredOn(item -> item.getRecordType() == PayoutRecordType.ADVANCE_OFFSET)
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

		assertThat(lineSum(payout(PayoutStage.FIRST))).isEqualTo(200_000L);
		Payout last = payout(PayoutStage.FINAL);
		assertThat(lineSum(last)).isEqualTo(272_500L);
		assertThat(last.getAdvanceDeductionAmount()).isEqualTo(200_000L);
		assertAdvanceOffset(last, -200_000L);
		assertThat(sellerReceived()).isEqualTo(472_500L);
		assertThat(balance(PayoutAccount.ADVANCE)).isZero();
		assertThat(balance(PayoutAccount.EARNING)).isEqualTo(472_500L);
		assertEveryPayoutLineSumMatches();
	}

	@Test
	@DisplayName("1차 없이 최종을 만들면 남은 1차 정산금이 0이라 1차 정산금 차감 줄도 0원으로 하나 남는다")
	void finalWithoutFirst() {
		createFinal(range(101, 110));

		Payout last = payout(PayoutStage.FINAL);
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
		assertThat(payout(PayoutStage.FIRST).getItems())
			.extracting(PayoutItem::getRaceCourseId, PayoutItem::getAmount)
			.containsExactlyInAnyOrder(tuple(course10k, 120_000L), tuple(course5k, 48_000L));
		// 10K 6장 × (50,000 − 2,750) = 283,500, 5K 4장 × (30,000 − 1,650) = 113,400
		Payout last = payout(PayoutStage.FINAL);
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
	@DisplayName("1차 지급 뒤 3장 환불, 108 · 109는 최종 전 입금, 110은 최종 생성 때 상계: 최종 170,750, 받은 돈 330,750")
	void threeRefundsAfterFirstPay() {
		createFirst();
		pay(FIRST_PAY_DATE);
		PayoutRecovery r108 = requestRecovery(108, LocalDateTime.of(2026, 11, 26, 10, 0));
		PayoutRecovery r109 = requestRecovery(109, LocalDateTime.of(2026, 11, 27, 10, 0));
		PayoutRecovery r110 = requestRecovery(110, LocalDateTime.of(2026, 11, 28, 10, 0));
		assertThat(balance(PayoutAccount.RECEIVABLE)).isEqualTo(60_000L);

		ledgerUseCase.confirmDeposit(r108.getId(), ADMIN_ID, LocalDateTime.of(2026, 11, 30, 10, 0));
		ledgerUseCase.confirmDeposit(r109.getId(), ADMIN_ID, LocalDateTime.of(2026, 12, 1, 10, 0));
		createFinal(range(101, 107));

		Payout last = payout(PayoutStage.FINAL);
		assertThat(lineSum(last)).isEqualTo(170_750L);
		// 남은 1차 정산금 = 200,000 − 회수 요청 3건 × 20,000 = 140,000
		assertThat(last.getAdvanceDeductionAmount()).isEqualTo(140_000L);
		assertAdvanceOffset(last, -140_000L);
		assertThat(balance(PayoutAccount.RECEIVABLE)).isZero();
		assertThat(payoutRecoveryRepository.findById(r110.getId()).orElseThrow().getCompletion())
			.isEqualTo(PayoutRecoveryCompletion.OFFSET);
		// 108 · 109는 관리자가 입금을 확인했고, 110은 최종 정산 내역에서 빼서 갚을 돈이 0이 되어 시스템이 상계로 완료
		assertCompletions(
			tuple(108L, PayoutRecoveryCompletion.DEPOSIT, ADMIN_ID),
			tuple(109L, PayoutRecoveryCompletion.DEPOSIT, ADMIN_ID),
			tuple(110L, PayoutRecoveryCompletion.OFFSET, PayoutHold.SYSTEM_MEMBER_ID));
		pay(FINAL_PAY_DATE);
		assertThat(sellerReceived()).isEqualTo(330_750L);
		assertEveryPayoutLineSumMatches();
	}

	@Test
	@DisplayName("108번 1차 지급 전 환불: 요청 즉시 1차 정산 내역에서 상계해 지급 180,000, 회수는 바로 완료")
	void refundBeforeFirstPay() {
		createFirst();
		PayoutRecovery r108 = requestRecovery(108, LocalDateTime.of(2026, 11, 1, 10, 0));

		assertThat(r108.getStatus()).isEqualTo(PayoutRecoveryStatus.COMPLETED);
		assertThat(r108.getCompletion()).isEqualTo(PayoutRecoveryCompletion.OFFSET);
		assertThat(r108.getCompletedBy()).isEqualTo(PayoutHold.SYSTEM_MEMBER_ID);
		assertThat(lineSum(payout(PayoutStage.FIRST))).isEqualTo(180_000L);
		assertThat(balance(PayoutAccount.RECEIVABLE)).isZero();

		pay(FIRST_PAY_DATE);
		assertThat(sellerReceived()).isEqualTo(180_000L);
		// 최종: 9장 × 47,250 − 남은 1차 정산금 180,000 = 245,250
		createFinal(101, 102, 103, 104, 105, 106, 107, 109, 110);
		assertThat(lineSum(payout(PayoutStage.FINAL))).isEqualTo(245_250L);
		pay(FINAL_PAY_DATE);
		assertThat(sellerReceived()).isEqualTo(425_250L);
		assertEveryPayoutLineSumMatches();
	}

	@Test
	@DisplayName("1차 지급 뒤 8장 환불, 미입금: 최종에서 54,500 상계해 0원, 남은 105,500 입금하면 8건 모두 완료")
	void eightRefundsUnpaid() {
		createFirst();
		pay(FIRST_PAY_DATE);
		List<PayoutRecovery> recoveries = Arrays.stream(range(103, 110))
			.mapToObj(id -> requestRecovery(id, LocalDateTime.of(2026, 11, 26, 10, 0)))
			.toList();
		assertThat(balance(PayoutAccount.RECEIVABLE)).isEqualTo(160_000L);

		createFinal(101, 102);
		assertThat(lineSum(payout(PayoutStage.FINAL))).isZero();
		assertThat(balance(PayoutAccount.RECEIVABLE)).isEqualTo(105_500L);
		assertThat(payoutRecoveryRepository.findAll())
			.allSatisfy(recovery -> assertThat(recovery.getStatus()).isEqualTo(PayoutRecoveryStatus.REQUESTED));

		pay(FINAL_PAY_DATE);
		for (PayoutRecovery recovery : recoveries) {
			entityManager.flush();
			entityManager.clear();
			if (payoutRecoveryRepository.findById(recovery.getId()).orElseThrow().getStatus()
				== PayoutRecoveryStatus.REQUESTED) {
				ledgerUseCase.confirmDeposit(recovery.getId(), ADMIN_ID, LocalDateTime.of(2027, 1, 25, 10, 0));
			}
		}

		assertThat(balance(PayoutAccount.RECEIVABLE)).isZero();
		assertThat(payoutRecoveryRepository.findAll())
			.allSatisfy(recovery -> assertThat(recovery.getStatus()).isEqualTo(PayoutRecoveryStatus.COMPLETED));
		// 103~107은 20,000씩 입금, 108은 남은 5,500을 입금해 갚을 돈이 0이 된다
		// 그때 남은 109 · 110은 최종 정산 내역에서 상계로 갚은 몫이라 입금 없이 상계로 함께 완료
		assertCompletions(
			tuple(103L, PayoutRecoveryCompletion.DEPOSIT, ADMIN_ID),
			tuple(104L, PayoutRecoveryCompletion.DEPOSIT, ADMIN_ID),
			tuple(105L, PayoutRecoveryCompletion.DEPOSIT, ADMIN_ID),
			tuple(106L, PayoutRecoveryCompletion.DEPOSIT, ADMIN_ID),
			tuple(107L, PayoutRecoveryCompletion.DEPOSIT, ADMIN_ID),
			tuple(108L, PayoutRecoveryCompletion.DEPOSIT, ADMIN_ID),
			tuple(109L, PayoutRecoveryCompletion.OFFSET, PayoutHold.SYSTEM_MEMBER_ID),
			tuple(110L, PayoutRecoveryCompletion.OFFSET, PayoutHold.SYSTEM_MEMBER_ID));
		assertThat(payoutItemRepository.findAll())
			.filteredOn(item -> item.getRecordType() == PayoutRecordType.RECOVERY_RECEIVED)
			.extracting(item -> item.getRecovery().getOrderItemId(), PayoutItem::getAmount)
			.containsExactlyInAnyOrder(
				tuple(103L, -20_000L), tuple(104L, -20_000L), tuple(105L, -20_000L), tuple(106L, -20_000L),
				tuple(107L, -20_000L), tuple(108L, -5_500L));
		assertThat(sellerReceived()).isEqualTo(94_500L);
		assertEveryPayoutLineSumMatches();
	}

	@Test
	@DisplayName("갚을 돈이 0 아래일 때 입금을 확인하면 입금 줄 없이 그 회수를 끝내고, 남은 요청 중인 회수도 상계로 완료한다")
	void depositWhenReceivableBelowZero() {
		createFirst();
		pay(FIRST_PAY_DATE);
		PayoutRecovery r108 = requestRecovery(108, LocalDateTime.of(2026, 11, 26, 10, 0));
		requestRecovery(109, LocalDateTime.of(2026, 11, 27, 10, 0));
		assertThat(balance(PayoutAccount.RECEIVABLE)).isEqualTo(40_000L);
		// 상계와 입금이 같은 잔고를 동시에 읽어 두 번 뺀 상태를 원장 줄로 직접 만든다 (동시 실행은 이 테스트로 재현하지 않는다)
		payoutItemRepository.save(
			PayoutItem.ofRecovery(r108, PayoutRecordType.RECOVERY_RECEIVED, null, null, -60_000L));
		assertThat(balance(PayoutAccount.RECEIVABLE)).isEqualTo(-20_000L);

		ledgerUseCase.confirmDeposit(r108.getId(), ADMIN_ID, LocalDateTime.of(2026, 12, 1, 10, 0));

		// 받을 돈이 없으므로 입금액은 0 이하라 줄을 쓰지 않고, 잔고는 그대로 −20,000
		assertThat(balance(PayoutAccount.RECEIVABLE)).isEqualTo(-20_000L);
		assertThat(payoutItemRepository.findAll())
			.filteredOn(item -> item.getRecordType() == PayoutRecordType.RECOVERY_RECEIVED)
			.extracting(PayoutItem::getAmount)
			.containsExactly(-60_000L);
		assertCompletions(
			tuple(108L, PayoutRecoveryCompletion.DEPOSIT, ADMIN_ID),
			tuple(109L, PayoutRecoveryCompletion.OFFSET, PayoutHold.SYSTEM_MEMBER_ID));
	}

	@Test
	@DisplayName("대회 취소 (1차 지급 뒤): 회수 200,000, 요청 뒤 ADVANCE 0 · RECEIVABLE 200,000, 입금 뒤 0, 최종 없음")
	void raceCanceledAfterFirstPay() {
		createFirst();
		pay(FIRST_PAY_DATE);
		raceCancelUseCase.cancelRace(RACE_ID, LocalDateTime.of(2026, 12, 1, 10, 0));

		assertThat(balance(PayoutAccount.ADVANCE)).isZero();
		assertThat(balance(PayoutAccount.RECEIVABLE)).isEqualTo(200_000L);
		PayoutRecovery recovery = payoutRecoveryRepository.findAll().getFirst();
		assertThat(recovery.getAmount()).isEqualTo(200_000L);

		ledgerUseCase.confirmDeposit(recovery.getId(), ADMIN_ID, LocalDateTime.of(2026, 12, 5, 10, 0));
		assertThat(balance(PayoutAccount.RECEIVABLE)).isZero();
		assertCompletions(tuple(null, PayoutRecoveryCompletion.DEPOSIT, ADMIN_ID));
		assertThat(sellerReceived()).isZero();
		assertThat(payoutRepository.findFirstBySellerIdAndRaceIdAndPayoutStageOrderByIdAsc(SELLER_ID, RACE_ID,
			PayoutStage.FINAL)).isEmpty();
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

		// ADVANCE: 1차 +200,000, 최종 차감 −200,000 → 0
		// EARNING: 판매 10장 × 50,000 − 수수료 10장 × 2,750 = 472,500 (판매자가 받은 돈 200,000 + 272,500과 같다)
		PayoutRecovery recovery = payoutRecoveryRepository.findAll().getFirst();
		assertThat(payoutRecoveryRepository.findAll()).hasSize(1);
		assertThat(recovery.getReasonType()).isEqualTo(PayoutRecoveryReason.RACE_CANCELED);
		assertThat(recovery.getAmount()).isEqualTo(472_500L);
		assertThat(payoutItemRepository.findAll())
			.filteredOn(item -> item.getRecordType() == PayoutRecordType.RECOVERY_REQUESTED
				|| item.getRecordType() == PayoutRecordType.RECEIVABLE_ADDED)
			.extracting(PayoutItem::getRecordType, PayoutItem::getAmount)
			.containsExactlyInAnyOrder(
				tuple(PayoutRecordType.RECOVERY_REQUESTED, -472_500L),
				tuple(PayoutRecordType.RECEIVABLE_ADDED, 472_500L));
		assertThat(balance(PayoutAccount.RECEIVABLE)).isEqualTo(472_500L);
	}

	@Test
	@DisplayName("1차 지급 전 대회 취소: 반대 줄로 되돌려 ADVANCE 0, 1차 정산 내역 줄 합 0, 회수 없음")
	void raceCanceledBeforeFirstPay() {
		createFirst();
		raceCancelUseCase.cancelRace(RACE_ID, LocalDateTime.of(2026, 11, 1, 10, 0));

		Payout first = payout(PayoutStage.FIRST);
		assertThat(first.getStatus()).isEqualTo(PayoutStatus.CANCELED);
		assertThat(lineSum(first)).isZero();
		assertThat(first.getItems()).extracting(PayoutItem::getRecordType)
			.containsExactlyInAnyOrder(PayoutRecordType.ADVANCE_PAID, PayoutRecordType.ADVANCE_PAID_CANCELED);
		assertThat(balance(PayoutAccount.ADVANCE)).isZero();
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
			.containsExactlyInAnyOrder(PayoutRecordType.RECOVERY_REQUESTED, PayoutRecordType.RECEIVABLE_ADDED);
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

	@Test
	@DisplayName("TC-CANCEL-1-1 1차 지급 전 환불이 상계로 끝난 뒤 대회가 취소되면 갚을 돈은 0원이다")
	void raceCanceledAfterOffsetRefundLeavesNoReceivable() {
		refundBeforeFirstPayThenCancelRace();

		assertThat(balance(PayoutAccount.RECEIVABLE)).isZero();
	}

	@Test
	@DisplayName("TC-CANCEL-2-1 1차 지급 전 환불이 상계로 끝난 뒤 대회가 취소되면 1차 정산금 잔고는 0원이다")
	void raceCanceledAfterOffsetRefundLeavesNoAdvance() {
		refundBeforeFirstPayThenCancelRace();

		assertThat(balance(PayoutAccount.ADVANCE)).isZero();
	}

	@Test
	@DisplayName("TC-CANCEL-3-1 되돌린 108번 회수는 회수 취소 상태가 된다")
	void raceCanceledAfterOffsetRefundCancelsRecovery() {
		refundBeforeFirstPayThenCancelRace();

		assertThat(payoutRecoveryRepository.findAll())
			.filteredOn(recovery -> Long.valueOf(108L).equals(recovery.getOrderItemId()))
			.extracting(PayoutRecovery::getStatus)
			.containsExactly(PayoutRecoveryStatus.CANCELED);
	}

	@Test
	@DisplayName("TC-CANCEL-4-1 받은 돈이 없으면 대회 취소 회수를 만들지 않는다")
	void raceCanceledAfterOffsetRefundCreatesNoRaceCancelRecovery() {
		refundBeforeFirstPayThenCancelRace();

		assertThat(payoutRecoveryRepository.findAll())
			.filteredOn(recovery -> recovery.getReasonType() == PayoutRecoveryReason.RACE_CANCELED)
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
			.containsExactly(PayoutRecoveryStatus.REQUESTED);
	}
}
