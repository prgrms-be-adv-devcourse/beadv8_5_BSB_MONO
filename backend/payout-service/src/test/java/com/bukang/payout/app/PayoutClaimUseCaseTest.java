package com.bukang.payout.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.payout.app.PayoutClaimUseCase.ClaimRunResult;
import com.bukang.payout.app.PayoutCreateStepUseCase.CourseSale;
import com.bukang.payout.app.PayoutCreateStepUseCase.FinalPayoutSource;
import com.bukang.payout.app.PayoutCreateStepUseCase.FirstPayoutSource;
import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.PayoutAccount;
import com.bukang.payout.domain.PayoutClaim;
import com.bukang.payout.domain.PayoutRecovery;
import com.bukang.payout.domain.PayoutRecoveryCompletion;
import com.bukang.payout.domain.PayoutRecoveryStatus;
import com.bukang.payout.domain.exception.PayoutClaimConflictException;
import com.bukang.payout.out.PayoutClaimRepository;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRecoveryRepository;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.out.SoldEntry;

import jakarta.persistence.EntityManager;

/**
 * 청구 만들기 · 알림 · 관리자 회수 확인 (선 환불 후 회수 AC-CLAIM · AC-CONFIRM)
 * 대회 7, 판매자 1, 5만 원 × 10장(101~110). 1장의 1차 몫 20,000, 수수료 2,750
 * 환불 기한 10/31 → 1차 생성 11/1, 지급 11/2. 대회일 12/13 → 최종 생성 12/14 03:00
 */
@DataJpaTest
@ActiveProfiles("test")
class PayoutClaimUseCaseTest {
	private static final long SELLER_ID = 1;
	private static final long RACE_ID = 7;
	private static final long ADMIN_ID = 99;
	private static final long PRICE = 50_000L;
	private static final LocalDate REFUND_DEADLINE = LocalDate.of(2026, 10, 31);
	private static final LocalDate RACE_DATE = LocalDate.of(2026, 12, 13);
	private static final LocalDate FIRST_PAY_DATE = LocalDate.of(2026, 11, 2);
	private static final LocalDate FINAL_CREATED_DATE = RACE_DATE.plusDays(1);

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
		payUseCase = new PayoutPayUseCase(new PayoutPayStepUseCase(payoutRepository), transfer -> {
		});
		raceCancelUseCase = new PayoutRaceCancelUseCase(payoutRepository, ledgerUseCase);
		claimUseCase = new PayoutClaimUseCase(payoutRepository, payoutItemRepository, payoutRecoveryRepository,
			payoutClaimRepository);
	}

	private void createFirstAndPay() {
		creator.createFirst(new FirstPayoutSource(SELLER_ID, RACE_ID, "대회 A", true,
			List.of(new CourseSale(RACE_ID, PRICE * 10, 10))), REFUND_DEADLINE,
			REFUND_DEADLINE.plusDays(1).atTime(3, 0));
		payUseCase.run(FIRST_PAY_DATE, FIRST_PAY_DATE.atTime(4, 0));
	}

	private void refund(int... orderItemIds) {
		Arrays.stream(orderItemIds).forEach(id -> ledgerUseCase.recoverScheduleChange(SELLER_ID, RACE_ID, RACE_ID, id,
			PRICE, LocalDateTime.of(2026, 11, 26, 10, 0)));
	}

	private void createFinal(int... orderItemIds) {
		List<SoldEntry> entries = Arrays.stream(orderItemIds)
			.mapToObj(id -> new SoldEntry(id, SELLER_ID, RACE_ID, RACE_ID, RACE_DATE, PRICE))
			.toList();
		creator.createFinal(new FinalPayoutSource(SELLER_ID, RACE_ID, "대회 A", true, entries), RACE_DATE,
			FINAL_CREATED_DATE.atTime(3, 0));
	}

	// 1차 지급 뒤 8장(103~110) 환불, 남은 2장으로 최종 생성: 최종 0원, 갚을 돈 105,500원
	private void eightRefundsThenFinal() {
		createFirstAndPay();
		refund(IntStream.rangeClosed(103, 110).toArray());
		createFinal(101, 102);
	}

	private ClaimRunResult run(LocalDate today) {
		ClaimRunResult result = claimUseCase.run(today, today.atTime(4, 0));
		entityManager.flush();
		entityManager.clear();
		return result;
	}

	private List<PayoutClaim> claims() {
		entityManager.flush();
		entityManager.clear();
		return payoutClaimRepository.findAll();
	}

	private PayoutClaim openClaimStartingOn(LocalDate startDate) {
		return payoutClaimRepository.save(new PayoutClaim(SELLER_ID, RACE_ID, 105_500L, startDate));
	}

	@Test
	@DisplayName("TC-CLAIM-1-1 최종 정산을 만든 뒤 갚을 돈이 남으면 105,500원 청구가 하나 생긴다")
	void finalWithReceivableCreatesClaim() {
		eightRefundsThenFinal();

		run(FINAL_CREATED_DATE);

		assertThat(claims()).extracting(PayoutClaim::getAmount, PayoutClaim::getStartDate)
			.containsExactly(tuple(105_500L, FINAL_CREATED_DATE));
	}

	@Test
	@DisplayName("TC-CLAIM-2-1 최종 정산 전에는 갚을 돈이 있어도 청구가 생기지 않는다")
	void noClaimBeforeFinal() {
		createFirstAndPay();
		refund(108);

		run(LocalDate.of(2026, 11, 27));

		assertThat(claims()).isEmpty();
	}

	@Test
	@DisplayName("TC-CLAIM-3-1 최종 정산 상계로 갚을 돈이 0원이면 청구가 생기지 않는다")
	void noClaimWhenFinalOffsetClearsReceivable() {
		createFirstAndPay();
		refund(108);
		createFinal(101, 102, 103, 104, 105, 106, 107, 109, 110);

		run(FINAL_CREATED_DATE);

		assertThat(claims()).isEmpty();
	}

	@Test
	@DisplayName("TC-CLAIM-4-1 1차 지급 뒤 대회가 취소되면 취소 다음 날 배치에서 200,000원 청구가 생긴다")
	void raceCancelCreatesClaimNextDay() {
		createFirstAndPay();
		raceCancelUseCase.cancelRace(RACE_ID, LocalDateTime.of(2026, 12, 1, 0, 30));

		run(LocalDate.of(2026, 12, 2));

		assertThat(claims()).extracting(PayoutClaim::getAmount, PayoutClaim::getStartDate)
			.containsExactly(tuple(200_000L, LocalDate.of(2026, 12, 1)));
	}

	@Test
	@DisplayName("TC-CLAIM-4-2 새벽에 대회가 취소돼도 같은 날 배치에서는 청구가 생기지 않는다")
	void raceCancelNoClaimSameDay() {
		createFirstAndPay();
		raceCancelUseCase.cancelRace(RACE_ID, LocalDateTime.of(2026, 12, 1, 0, 30));

		run(LocalDate.of(2026, 12, 1));

		assertThat(claims()).isEmpty();
	}

	@Test
	@DisplayName("TC-CLAIM-5-1 청구가 끝나지 않았으면 배치마다 판매자 청구 알림이 하나 남는다")
	void openClaimNotifiesSeller() {
		openClaimStartingOn(FINAL_CREATED_DATE);

		assertThat(run(FINAL_CREATED_DATE.plusDays(1)).sellerNotices()).isEqualTo(1);
	}

	@Test
	@DisplayName("TC-CLAIM-5-2 같은 날 배치가 두 번 돌아도 청구는 하나, 그날 판매자 알림도 하나다")
	void runningTwiceSameDayIsIdempotent() {
		eightRefundsThenFinal();

		ClaimRunResult first = run(FINAL_CREATED_DATE);
		ClaimRunResult second = run(FINAL_CREATED_DATE);

		assertThat(claims()).hasSize(1);
		assertThat(first.sellerNotices() + second.sellerNotices()).isEqualTo(1);
	}

	@Test
	@DisplayName("TC-CLAIM-5-3 진행 중인 청구가 없으면 배치는 아무것도 남기지 않고 끝난다")
	void noClaimsNoNotices() {
		assertThat(run(FINAL_CREATED_DATE)).isEqualTo(new ClaimRunResult(0, 0, 0));
	}

	@Test
	@DisplayName("TC-CLAIM-6-1 상계 실패일 11/1이면 30일째인 11/30 배치에서 관리자 알림이 하나 남는다")
	void adminNoticeOnDay30() {
		openClaimStartingOn(LocalDate.of(2026, 11, 1));

		assertThat(run(LocalDate.of(2026, 11, 30)).adminNotices()).isEqualTo(1);
	}

	@Test
	@DisplayName("TC-CLAIM-6-2 상계 실패일 2027-01-31이면 관리자 알림은 2027-03-01에 남는다")
	void adminNoticeAcrossFebruary() {
		openClaimStartingOn(LocalDate.of(2027, 1, 31));

		assertThat(run(LocalDate.of(2027, 2, 28)).adminNotices()).isZero();
		assertThat(run(LocalDate.of(2027, 3, 1)).adminNotices()).isEqualTo(1);
	}

	@Test
	@DisplayName("TC-CLAIM-6-3 윤년: 상계 실패일 2028-01-31이면 관리자 알림은 2028-02-29에 남는다")
	void adminNoticeLeapYear() {
		openClaimStartingOn(LocalDate.of(2028, 1, 31));

		assertThat(run(LocalDate.of(2028, 2, 29)).adminNotices()).isEqualTo(1);
	}

	@Test
	@DisplayName("TC-CLAIM-6-4 30일째 하루 전(11/29)에는 관리자 알림이 남지 않는다")
	void noAdminNoticeOnDay29() {
		openClaimStartingOn(LocalDate.of(2026, 11, 1));

		assertThat(run(LocalDate.of(2026, 11, 29)).adminNotices()).isZero();
	}

	@Test
	@DisplayName("TC-CLAIM-7-1 관리자 알림은 한 번만 남는다")
	void adminNoticeOnlyOnce() {
		openClaimStartingOn(LocalDate.of(2026, 11, 1));
		run(LocalDate.of(2026, 11, 30));

		assertThat(run(LocalDate.of(2026, 12, 1)).adminNotices()).isZero();
	}

	@Test
	@DisplayName("TC-CONFIRM-1-1 관리자가 청구를 회수 확인하면 갚을 돈이 0원이 된다")
	void confirmClearsReceivable() {
		eightRefundsThenFinal();
		run(FINAL_CREATED_DATE);

		claimUseCase.confirm(claims().getFirst().getId(), ADMIN_ID, LocalDateTime.of(2026, 12, 20, 10, 0));
		entityManager.flush();

		assertThat(ledgerUseCase.balance(SELLER_ID, RACE_ID, PayoutAccount.RECEIVABLE)).isZero();
	}

	@Test
	@DisplayName("TC-CONFIRM-2-1 회수 확인하면 그 대회의 요청 중인 회수가 모두 입금으로 끝난다")
	void confirmCompletesRequestedRecoveries() {
		eightRefundsThenFinal();
		run(FINAL_CREATED_DATE);

		claimUseCase.confirm(claims().getFirst().getId(), ADMIN_ID, LocalDateTime.of(2026, 12, 20, 10, 0));
		entityManager.flush();
		entityManager.clear();

		assertThat(payoutRecoveryRepository.findAll())
			.extracting(PayoutRecovery::getStatus, PayoutRecovery::getCompletion)
			.containsOnly(tuple(PayoutRecoveryStatus.COMPLETED, PayoutRecoveryCompletion.DEPOSIT));
	}

	@Test
	@DisplayName("TC-CONFIRM-3-1 회수 확인한 청구는 다음 날부터 판매자 알림이 남지 않는다")
	void confirmedClaimIsNotNotified() {
		eightRefundsThenFinal();
		run(FINAL_CREATED_DATE);
		claimUseCase.confirm(claims().getFirst().getId(), ADMIN_ID, LocalDateTime.of(2026, 12, 20, 10, 0));

		assertThat(run(FINAL_CREATED_DATE.plusDays(7)).sellerNotices()).isZero();
	}

	@Test
	@DisplayName("TC-CONFIRM-4-1 이미 회수 확인한 청구를 다시 확인하면 거절하고 원장 줄은 그대로다")
	void confirmTwiceIsRejected() {
		eightRefundsThenFinal();
		run(FINAL_CREATED_DATE);
		long claimId = claims().getFirst().getId();
		claimUseCase.confirm(claimId, ADMIN_ID, LocalDateTime.of(2026, 12, 20, 10, 0));
		entityManager.flush();
		long lines = payoutItemRepository.count();

		assertThatThrownBy(() -> claimUseCase.confirm(claimId, ADMIN_ID, LocalDateTime.of(2026, 12, 21, 10, 0)))
			.isInstanceOf(PayoutClaimConflictException.class);
		assertThat(payoutItemRepository.count()).isEqualTo(lines);
	}

	@Test
	@DisplayName("TC-WITHDRAW-1-1 갚을 돈 105,500원이 남은 판매자는 갚을 돈이 있다고 답한다")
	void sellerWithReceivable() {
		eightRefundsThenFinal();
		entityManager.flush();

		assertThat(new PayoutReceivableQueryUseCase(payoutItemRepository).findReceivable(SELLER_ID))
			.extracting(r -> r.isHasReceivable(), r -> r.getRaceIds())
			.containsExactly(true, List.of(RACE_ID));
	}

	@Test
	@DisplayName("TC-WITHDRAW-2-1 회수 확인으로 갚을 돈이 0원이 된 판매자는 갚을 돈이 없다고 답한다")
	void sellerWithoutReceivable() {
		eightRefundsThenFinal();
		run(FINAL_CREATED_DATE);
		claimUseCase.confirm(claims().getFirst().getId(), ADMIN_ID, LocalDateTime.of(2026, 12, 20, 10, 0));
		entityManager.flush();

		assertThat(new PayoutReceivableQueryUseCase(payoutItemRepository).findReceivable(SELLER_ID)
			.isHasReceivable()).isFalse();
	}
}
