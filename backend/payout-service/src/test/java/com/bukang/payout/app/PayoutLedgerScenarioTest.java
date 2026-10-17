package com.bukang.payout.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.payout.app.PayoutGroupCreator.CourseSale;
import com.bukang.payout.app.PayoutGroupCreator.FirstGroup;
import com.bukang.payout.app.PayoutGroupCreator.PayoutGroup;
import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutAccount;
import com.bukang.payout.domain.PayoutItem;
import com.bukang.payout.domain.PayoutRecordType;
import com.bukang.payout.domain.PayoutRecovery;
import com.bukang.payout.domain.PayoutRecoveryCompletion;
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
 * 환불 기한 2026-10-31 → 1차 생성 11/1, 지급 11/20. 대회일 12/13 → 최종 생성 12/14, 지급 2027-01-20
 * 모든 시나리오에서 정산 내역마다 원장 줄 합 = 지급액(payout_amount)인지도 확인한다
 */
@DataJpaTest
@ActiveProfiles("test")
class PayoutLedgerScenarioTest {
	private static final int SELLER_ID = 1;
	private static final int RACE_ID = 7;
	private static final int ADMIN_ID = 99;
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
	private EntityManager entityManager;

	private PayoutLedgerUseCase ledgerUseCase;
	private PayoutGroupCreator creator;
	private PayoutPayUseCase payUseCase;
	private PayoutRecoveryRequestUseCase recoveryRequestUseCase;
	private PayoutRecoveryUseCase recoveryUseCase;
	private PayoutRaceCancelUseCase raceCancelUseCase;

	@BeforeEach
	void setUp() {
		BusinessDayCalendar calendar = new BusinessDayCalendar();
		ledgerUseCase = new PayoutLedgerUseCase(payoutRepository, payoutItemRepository, payoutRecoveryRepository);
		creator = new PayoutGroupCreator(payoutRepository, payoutHoldRepository, calendar, ledgerUseCase);
		payUseCase = new PayoutPayUseCase(payoutRepository);
		recoveryRequestUseCase = new PayoutRecoveryRequestUseCase(payoutRepository, payoutItemRepository,
			payoutRecoveryRepository, ledgerUseCase);
		recoveryUseCase = new PayoutRecoveryUseCase(payoutRecoveryRepository, payoutItemRepository, ledgerUseCase);
		raceCancelUseCase = new PayoutRaceCancelUseCase(payoutRepository, payoutItemRepository,
			payoutRecoveryRepository, ledgerUseCase);
	}

	private PayoutGroup group(int... orderItemIds) {
		List<SoldEntry> entries = Arrays.stream(orderItemIds)
			.mapToObj(id -> new SoldEntry(id, SELLER_ID, RACE_ID, RACE_ID, RACE_DATE, PRICE))
			.toList();
		return new PayoutGroup(SELLER_ID, RACE_ID, "대회 A", true, entries);
	}

	private static int[] range(int from, int to) {
		return java.util.stream.IntStream.rangeClosed(from, to).toArray();
	}

	private void createFirst() {
		creator.createFirst(new FirstGroup(SELLER_ID, RACE_ID, "대회 A", true,
			List.of(new CourseSale(RACE_ID, PRICE * 10, 10))), REFUND_DEADLINE,
			REFUND_DEADLINE.plusDays(1).atTime(3, 0));
	}

	private void createFinal(int... orderItemIds) {
		creator.createFinal(group(orderItemIds), RACE_DATE, RACE_DATE.plusDays(1).atTime(3, 0));
	}

	private void pay(LocalDate payDate) {
		payUseCase.run(payDate, payDate.atTime(9, 0));
	}

	private PayoutRecovery requestRecovery(int orderItemId, LocalDateTime at) {
		return recoveryRequestUseCase.requestScheduleChange(SELLER_ID, RACE_ID, RACE_ID, orderItemId, PRICE, at)
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
		assertThat(sellerReceived()).isEqualTo(472_500L);
		assertThat(balance(PayoutAccount.ADVANCE)).isZero();
		assertThat(balance(PayoutAccount.EARNING)).isEqualTo(472_500L);
		assertEveryPayoutLineSumMatches();
	}

	@Test
	@DisplayName("코스가 둘인 대회: 1차 정산금은 코스마다 한 줄, 최종 판매 · 수수료 줄에도 코스가 남아 코스별로 더할 수 있다")
	void twoCourses() {
		int course10k = 71;
		int course5k = 72;
		creator.createFirst(new FirstGroup(SELLER_ID, RACE_ID, "대회 A", true, List.of(
			new CourseSale(course10k, 50_000L * 6, 6),
			new CourseSale(course5k, 30_000L * 4, 4))), REFUND_DEADLINE, REFUND_DEADLINE.plusDays(1).atTime(3, 0));
		pay(FIRST_PAY_DATE);
		List<SoldEntry> entries = new ArrayList<>();
		for (int id = 101; id <= 106; id++) {
			entries.add(new SoldEntry(id, SELLER_ID, RACE_ID, course10k, RACE_DATE, 50_000L));
		}
		for (int id = 107; id <= 110; id++) {
			entries.add(new SoldEntry(id, SELLER_ID, RACE_ID, course5k, RACE_DATE, 30_000L));
		}
		creator.createFinal(new PayoutGroup(SELLER_ID, RACE_ID, "대회 A", true, entries), RACE_DATE,
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

	private long courseSum(Payout payout, int raceCourseId) {
		return payout.getItems().stream()
			.filter(item -> Integer.valueOf(raceCourseId).equals(item.getRaceCourseId()))
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

		recoveryUseCase.complete(r108.getId(), ADMIN_ID, LocalDateTime.of(2026, 11, 30, 10, 0));
		recoveryUseCase.complete(r109.getId(), ADMIN_ID, LocalDateTime.of(2026, 12, 1, 10, 0));
		createFinal(range(101, 107));

		Payout last = payout(PayoutStage.FINAL);
		assertThat(lineSum(last)).isEqualTo(170_750L);
		assertThat(balance(PayoutAccount.RECEIVABLE)).isZero();
		assertThat(payoutRecoveryRepository.findById(r110.getId()).orElseThrow().getCompletion())
			.isEqualTo(PayoutRecoveryCompletion.OFFSET);
		pay(FINAL_PAY_DATE);
		assertThat(sellerReceived()).isEqualTo(330_750L);
		assertEveryPayoutLineSumMatches();
	}

	@Test
	@DisplayName("108번 1차 지급 전 환불: 요청 즉시 1차 정산 내역에서 상계해 지급 180,000, 회수는 바로 완료")
	void refundBeforeFirstPay() {
		createFirst();
		PayoutRecovery r108 = requestRecovery(108, LocalDateTime.of(2026, 11, 5, 10, 0));

		assertThat(r108.getStatus()).isEqualTo(PayoutRecoveryStatus.COMPLETED);
		assertThat(r108.getCompletion()).isEqualTo(PayoutRecoveryCompletion.OFFSET);
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
				recoveryUseCase.complete(recovery.getId(), ADMIN_ID, LocalDateTime.of(2027, 1, 25, 10, 0));
			}
		}

		assertThat(balance(PayoutAccount.RECEIVABLE)).isZero();
		assertThat(payoutRecoveryRepository.findAll())
			.allSatisfy(recovery -> assertThat(recovery.getStatus()).isEqualTo(PayoutRecoveryStatus.COMPLETED));
		assertThat(sellerReceived()).isEqualTo(94_500L);
		assertEveryPayoutLineSumMatches();
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

		recoveryUseCase.complete(recovery.getId(), ADMIN_ID, LocalDateTime.of(2026, 12, 5, 10, 0));
		assertThat(balance(PayoutAccount.RECEIVABLE)).isZero();
		assertThat(sellerReceived()).isZero();
		assertThat(payoutRepository.findFirstBySellerIdAndRaceIdAndPayoutStageOrderByIdAsc(SELLER_ID, RACE_ID,
			PayoutStage.FINAL)).isEmpty();
		assertEveryPayoutLineSumMatches();
	}

	@Test
	@DisplayName("1차 지급 전 대회 취소: 반대 줄로 되돌려 ADVANCE 0, 1차 정산 내역 줄 합 0, 회수 없음")
	void raceCanceledBeforeFirstPay() {
		createFirst();
		raceCancelUseCase.cancelRace(RACE_ID, LocalDateTime.of(2026, 11, 10, 10, 0));

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
	@DisplayName("같은 참가권으로 회수를 두 번 요청하면 두 번째는 충돌(409)이고, 원장 줄은 한 번만 남는다")
	void duplicateRecoveryRequest() {
		createFirst();
		pay(FIRST_PAY_DATE);
		requestRecovery(108, LocalDateTime.of(2026, 11, 26, 10, 0));

		assertThatThrownBy(() -> requestRecovery(108, LocalDateTime.of(2026, 11, 26, 11, 0)))
			.isInstanceOf(PayoutRecoveryConflictException.class);
		entityManager.flush();
		assertThat(payoutItemRepository.findAll())
			.filteredOn(item -> Integer.valueOf(108).equals(item.getOrderItemId()))
			.extracting(PayoutItem::getRecordType)
			.containsExactlyInAnyOrder(PayoutRecordType.RECOVERY_REQUESTED, PayoutRecordType.RECEIVABLE_ADDED);
	}
}
