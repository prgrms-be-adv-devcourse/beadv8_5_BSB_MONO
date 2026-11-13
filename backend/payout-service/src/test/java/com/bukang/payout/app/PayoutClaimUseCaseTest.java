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
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.payout.app.PayoutClaimUseCase.ClaimRunResult;
import com.bukang.payout.app.PayoutCreateStepUseCase.CourseSale;
import com.bukang.payout.app.PayoutCreateStepUseCase.FinalPayoutSource;
import com.bukang.payout.app.PayoutCreateStepUseCase.FirstPayoutSource;
import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.PayoutAccount;
import com.bukang.payout.domain.PayoutClaim;
import com.bukang.payout.domain.PayoutClaimStatus;
import com.bukang.payout.domain.PayoutHold;
import com.bukang.payout.domain.PayoutItem;
import com.bukang.payout.domain.PayoutRecordType;
import com.bukang.payout.domain.PayoutRecovery;
import com.bukang.payout.domain.PayoutRecoveryCompletion;
import com.bukang.payout.domain.PayoutRecoveryReason;
import com.bukang.payout.domain.PayoutRecoveryStatus;
import com.bukang.payout.domain.exception.PayoutClaimConflictException;
import com.bukang.payout.domain.exception.PayoutClaimNotFoundException;
import com.bukang.payout.out.ClaimVirtualAccount;
import com.bukang.payout.out.ClaimVirtualAccountPort;
import com.bukang.payout.out.PayoutClaimRepository;
import com.bukang.payout.out.PayoutCreateFailureRepository;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRecoveryRepository;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.out.SoldEntry;

import jakarta.persistence.EntityManager;

/**
 * 청구 만들기 · 알림 · 관리자 회수 확인 (선 환불 후 회수 AC-CLAIM · AC-CONFIRM)
 * 대회 7, 판매자 1, 5만 원 × 10장(101~110). 1장의 1차 몫 20,000, 수수료 2,750
 * 환불 기한 10/31 → 1차 생성 11/1, 지급 11/20(정산 월 10월의 다음 달 20일). 대회일 12/13 → 최종 생성 12/14 03:00
 */
@DataJpaTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class PayoutClaimUseCaseTest {
	private static final long SELLER_ID = 1;
	private static final long RACE_ID = 7;
	private static final long ADMIN_ID = 99;
	private static final long PRICE = 50_000L;
	private static final LocalDate REFUND_DEADLINE = LocalDate.of(2026, 10, 31);
	private static final LocalDate RACE_DATE = LocalDate.of(2026, 12, 13);
	private static final LocalDate FIRST_PAY_DATE = LocalDate.of(2026, 11, 20);
	private static final LocalDate FINAL_CREATED_DATE = RACE_DATE.plusDays(1);
	private static final LocalDateTime DEPOSITED_AT = LocalDateTime.of(2026, 12, 18, 14, 30);

	@Autowired
	private PayoutRepository payoutRepository;

	@Autowired
	private PayoutCreateFailureRepository payoutCreateFailureRepository;

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
	// 캐시의 가상계좌 발급 API 대신. 테스트마다 바꿔 끼운다 (기본: 발급 전이라 빈 값)
	private ClaimVirtualAccountPort virtualAccountPort;

	@BeforeEach
	void setUp() {
		BusinessDayCalendar calendar = new BusinessDayCalendar();
		ledgerUseCase = new PayoutLedgerUseCase(payoutRepository, payoutItemRepository, payoutRecoveryRepository);
		creator = new PayoutCreateStepUseCase(payoutRepository, calendar, ledgerUseCase,
			new PayoutHoldUseCase(payoutRepository, payoutHoldRepository, calendar));
		raceCancelUseCase = new PayoutRaceCancelUseCase(payoutRepository, ledgerUseCase);
		payUseCase = new PayoutPayUseCase(new PayoutPayStepUseCase(payoutRepository, raceCancelUseCase,
				payoutCreateFailureRepository), transfer -> {
		});
		virtualAccountPort = (claimCode, amount, dueDate) -> Optional.empty();
		claimUseCase = new PayoutClaimUseCase(payoutRepository, payoutItemRepository, payoutRecoveryRepository,
			payoutClaimRepository,
			(claimCode, amount, dueDate) -> virtualAccountPort.issue(claimCode, amount, dueDate));
	}

	private void createFirstAndPay() {
		creator.createFirst(new FirstPayoutSource(SELLER_ID, RACE_ID, "대회 A", true,
			List.of(new CourseSale(RACE_ID, PRICE * 10, 10))), REFUND_DEADLINE,
			REFUND_DEADLINE.plusDays(1).atTime(3, 0));
		payUseCase.run(FIRST_PAY_DATE, FIRST_PAY_DATE.atTime(4, 0));
	}

	private void refund(int... orderItemIds) {
		Arrays.stream(orderItemIds).forEach(id -> ledgerUseCase.recoverRefund(SELLER_ID, RACE_ID, RACE_ID, id,
			PRICE, PayoutRecoveryReason.일정_변경, LocalDateTime.of(2026, 11, 26, 10, 0)));
	}

	// 대회 취소: 정산이 대회 취소 메시지를 처리하고, 주문이 환불한 101~110번마다 회수를 요청한다 (BUK-76)
	private void cancelRaceAndRefundAll(LocalDateTime at) {
		raceCancelUseCase.cancelRace(RACE_ID, at);
		for (int id = 101; id <= 110; id++) {
			ledgerUseCase.recoverRefund(SELLER_ID, RACE_ID, RACE_ID, id, PRICE, PayoutRecoveryReason.대회_취소, at);
		}
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

	// 청구를 만든 뒤 갚을 돈이 바뀐 상황을 만든다. 양수면 회수가 더 생긴 것, 음수면 예전 회수 완료 API로 일부를 받은 것
	private void changeReceivableAfterClaim(long amount) {
		if (amount > 0) {
			PayoutRecovery more = payoutRecoveryRepository.save(PayoutRecovery.scheduleChanged(SELLER_ID, RACE_ID, 999,
				amount, LocalDateTime.of(2026, 12, 15, 10, 0)));
			payoutItemRepository.save(PayoutItem.ofRecovery(more, PayoutRecordType.갚을_돈_추가, 999L, RACE_ID,
				amount));
			return;
		}
		PayoutRecovery any = payoutRecoveryRepository.findAll().getFirst();
		payoutItemRepository.save(PayoutItem.ofRecovery(any, PayoutRecordType.회수_입금, null, null, amount));
	}

	private void assertConfirmRejectedAndNothingChanged(long expectedReceivable) {
		long claimId = claims().getFirst().getId();
		long lines = payoutItemRepository.count();

		assertThatThrownBy(() -> claimUseCase.confirm(claimId, ADMIN_ID, LocalDateTime.of(2026, 12, 20, 10, 0)))
			.isInstanceOf(PayoutClaimConflictException.class)
			.hasMessageContaining("예상치 못한");
		entityManager.flush();
		entityManager.clear();

		assertThat(payoutItemRepository.count()).isEqualTo(lines);
		assertThat(ledgerUseCase.balance(SELLER_ID, RACE_ID, PayoutAccount.갚을_돈)).isEqualTo(expectedReceivable);
		assertThat(claims()).extracting(PayoutClaim::getStatus).containsExactly(PayoutClaimStatus.청구_중);
		assertThat(payoutRecoveryRepository.findAll()).extracting(PayoutRecovery::getStatus)
			.containsOnly(PayoutRecoveryStatus.회수_요청);
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
		PayoutClaim claim = claims().getFirst();
		assertThat(claim.getClaimCode())
			.isEqualTo("RC-%s-%04X".formatted(FINAL_CREATED_DATE.toString().replace("-", ""), claim.getId() & 0xFFFF));
		assertThat(claim.getDueDate()).isEqualTo(FINAL_CREATED_DATE.plusDays(30));
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
		cancelRaceAndRefundAll(LocalDateTime.of(2026, 12, 1, 0, 30));

		run(LocalDate.of(2026, 12, 2));

		assertThat(claims()).extracting(PayoutClaim::getAmount, PayoutClaim::getStartDate)
			.containsExactly(tuple(200_000L, LocalDate.of(2026, 12, 1)));
	}

	@Test
	@DisplayName("TC-CLAIM-4-2 새벽에 대회가 취소돼도 같은 날 배치에서는 청구가 생기지 않는다")
	void raceCancelNoClaimSameDay() {
		createFirstAndPay();
		cancelRaceAndRefundAll(LocalDateTime.of(2026, 12, 1, 0, 30));

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

		assertThat(ledgerUseCase.balance(SELLER_ID, RACE_ID, PayoutAccount.갚을_돈)).isZero();
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
			.containsOnly(tuple(PayoutRecoveryStatus.회수_완료, PayoutRecoveryCompletion.입금));
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
	@DisplayName("TC-REV2-2 청구 105,500원 뒤 회수가 더 생겨 갚을 돈이 185,500원이면 확인을 409로 거부하고 아무것도 바꾸지 않으며 금액 차이를 ERROR 로그로 남긴다")
	void confirmRejectedWhenReceivableGrew(CapturedOutput output) {
		eightRefundsThenFinal();
		run(FINAL_CREATED_DATE);
		changeReceivableAfterClaim(80_000);

		assertConfirmRejectedAndNothingChanged(185_500);
		// 응답에는 "예상치 못한 일"만, 로그에는 청구 · 판매자 · 대회와 금액 차이를 자세히 남긴다
		List<String> logs = output.getAll().lines().filter(line -> line.contains("회수 확인을 거부합니다")).toList();
		assertThat(logs).hasSize(1);
		assertThat(logs.getFirst()).contains("ERROR", "sellerId=1", "raceId=7", "청구 금액=105500", "지금 갚을 돈=185500",
			"차이=80000");
	}

	@Test
	@DisplayName("TC-REV2-3 갚을 돈이 청구액보다 1원 많으면(105,501원) 확인을 409로 거부한다")
	void confirmRejectedWhenReceivableIsOneMore() {
		eightRefundsThenFinal();
		run(FINAL_CREATED_DATE);
		changeReceivableAfterClaim(1);

		assertConfirmRejectedAndNothingChanged(105_501);
	}

	@Test
	@DisplayName("TC-REV2-4 갚을 돈이 청구액보다 1원 적으면(105,499원) 확인을 409로 거부한다")
	void confirmRejectedWhenReceivableIsOneLess() {
		eightRefundsThenFinal();
		run(FINAL_CREATED_DATE);
		changeReceivableAfterClaim(-1);

		assertConfirmRejectedAndNothingChanged(105_499);
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

	// 최종 정산 뒤 105,500원 청구를 만들고 그 청구 ID를 돌려준다
	private String openClaimCode() {
		eightRefundsThenFinal();
		run(FINAL_CREATED_DATE);
		return claims().getFirst().getClaimCode();
	}

	private void deposit(String claimCode, long amount) {
		claimUseCase.confirmByDeposit(claimCode, amount, DEPOSITED_AT);
		entityManager.flush();
		entityManager.clear();
	}

	@Test
	@DisplayName("TC-VA-1-1 청구가 생기면 청구 ID · 금액 · 입금 기한으로 가상계좌를 발급받아 청구에 저장한다")
	void issuesVirtualAccountForNewClaim() {
		List<String> requests = new ArrayList<>();
		virtualAccountPort = (claimCode, amount, dueDate) -> {
			requests.add(claimCode + "/" + amount + "/" + dueDate);
			return Optional.of(new ClaimVirtualAccount("신한은행", "56211234567890"));
		};

		eightRefundsThenFinal();
		run(FINAL_CREATED_DATE);

		PayoutClaim claim = claims().getFirst();
		assertThat(requests).containsExactly(claim.getClaimCode() + "/105500/" + FINAL_CREATED_DATE.plusDays(30));
		assertThat(claim).extracting(PayoutClaim::getVirtualBankName, PayoutClaim::getVirtualAccountNumber)
			.containsExactly("신한은행", "56211234567890");
	}

	@Test
	@DisplayName("TC-VA-1-2 이미 가상계좌가 있는 청구는 다음 배치에서 다시 발급하지 않는다")
	void doesNotReissueVirtualAccount() {
		List<String> requests = new ArrayList<>();
		virtualAccountPort = (claimCode, amount, dueDate) -> {
			requests.add(claimCode);
			return Optional.of(new ClaimVirtualAccount("신한은행", "56211234567890"));
		};
		eightRefundsThenFinal();
		run(FINAL_CREATED_DATE);

		run(FINAL_CREATED_DATE.plusDays(1));

		assertThat(requests).hasSize(1);
	}

	@Test
	@DisplayName("TC-VA-2-1 발급 API가 아직 없어 빈 값이면 계좌 없이 두고, 다음 배치에서 다시 요청한다")
	void retriesWhenNotIssued() {
		List<String> requests = new ArrayList<>();
		virtualAccountPort = (claimCode, amount, dueDate) -> {
			requests.add(claimCode);
			return Optional.empty();
		};
		eightRefundsThenFinal();
		run(FINAL_CREATED_DATE);
		run(FINAL_CREATED_DATE.plusDays(1));

		assertThat(requests).hasSize(2);
		assertThat(claims().getFirst().getVirtualAccountNumber()).isNull();
	}

	@Test
	@DisplayName("TC-VA-2-3 발급 응답이 시간 초과로 끊기면 다음 배치에서 같은 청구 ID로 다시 요청해 받은 계좌를 저장한다")
	void retriesWithSameClaimCodeAfterTimeout() {
		List<String> requests = new ArrayList<>();
		virtualAccountPort = (claimCode, amount, dueDate) -> {
			requests.add(claimCode);
			if (requests.size() == 1) {
				throw new IllegalStateException("캐시 응답 시간 초과");
			}
			return Optional.of(new ClaimVirtualAccount("신한은행", "56211234567890"));
		};
		eightRefundsThenFinal();
		run(FINAL_CREATED_DATE);

		run(FINAL_CREATED_DATE.plusDays(1));

		PayoutClaim claim = claims().getFirst();
		assertThat(requests).containsExactly(claim.getClaimCode(), claim.getClaimCode());
		assertThat(claim.getVirtualAccountNumber()).isEqualTo("56211234567890");
	}

	@Test
	@DisplayName("TC-VA-2-2 발급 요청이 실패해도 청구와 판매자 알림은 남고 배치는 끝난다")
	void issueFailureDoesNotStopBatch() {
		virtualAccountPort = (claimCode, amount, dueDate) -> {
			throw new IllegalStateException("캐시 응답 없음");
		};
		eightRefundsThenFinal();

		ClaimRunResult result = run(FINAL_CREATED_DATE);

		assertThat(result.created()).isEqualTo(1);
		assertThat(result.sellerNotices()).isEqualTo(1);
		assertThat(claims().getFirst().getVirtualAccountNumber()).isNull();
	}

	@Test
	@DisplayName("TC-DEPOSIT-1-1 입금 메시지가 오면 관리자 확인 없이 청구가 입금 확인이 되고 갚을 돈이 0원, 회수가 입금으로 끝난다")
	void depositConfirmsClaim() {
		String claimCode = openClaimCode();

		deposit(claimCode, 105_500L);

		assertThat(ledgerUseCase.balance(SELLER_ID, RACE_ID, PayoutAccount.갚을_돈)).isZero();
		assertThat(claims()).extracting(PayoutClaim::getStatus, PayoutClaim::getConfirmedBy,
				PayoutClaim::getConfirmedAt)
			.containsExactly(tuple(PayoutClaimStatus.입금_확인, PayoutHold.SYSTEM_MEMBER_ID, DEPOSITED_AT));
		assertThat(payoutRecoveryRepository.findAll())
			.extracting(PayoutRecovery::getStatus, PayoutRecovery::getCompletion)
			.containsOnly(tuple(PayoutRecoveryStatus.회수_완료, PayoutRecoveryCompletion.입금));
	}

	@Test
	@DisplayName("TC-DEPOSIT-2-1 같은 입금 메시지가 두 번 오면 두 번째는 INFO 로그만 남기고 건너뛰며 회수 입금 줄은 하나다")
	void sameDepositTwiceIsSkipped(CapturedOutput output) {
		String claimCode = openClaimCode();
		deposit(claimCode, 105_500L);
		long lines = payoutItemRepository.count();

		deposit(claimCode, 105_500L);

		assertThat(payoutItemRepository.count()).isEqualTo(lines);
		assertThat(ledgerUseCase.balance(SELLER_ID, RACE_ID, PayoutAccount.갚을_돈)).isZero();
		assertThat(output.getAll().lines().filter(line -> line.contains(claimCode) && line.contains("건너뜁니다")))
			.singleElement().asString().contains("INFO");
	}

	@Test
	@DisplayName("TC-DEPOSIT-2-2 관리자가 먼저 확인했으면 입금 메시지는 건너뛰되, 두 번 받았을 수 있어 WARN 로그를 남긴다")
	void depositAfterAdminConfirmIsSkippedWithWarn(CapturedOutput output) {
		String claimCode = openClaimCode();
		claimUseCase.confirm(claims().getFirst().getId(), ADMIN_ID, LocalDateTime.of(2026, 12, 20, 10, 0));
		entityManager.flush();
		long lines = payoutItemRepository.count();

		deposit(claimCode, 105_500L);

		assertThat(payoutItemRepository.count()).isEqualTo(lines);
		assertThat(claims().getFirst().getConfirmedBy()).isEqualTo(ADMIN_ID);
		assertThat(output.getAll().lines().filter(line -> line.contains("관리자가 이미 입금을 확인한 청구")))
			.singleElement().asString()
			.contains("WARN", claimCode, "입금액=105500", "confirmedBy=" + ADMIN_ID);
	}

	@Test
	@DisplayName("TC-DEPOSIT-3-1 입금액이 청구 금액과 다르면 회수를 끝내지 않고 거부하며 ERROR 로그를 남긴다")
	void depositAmountMismatchIsRejected(CapturedOutput output) {
		String claimCode = openClaimCode();
		long lines = payoutItemRepository.count();

		assertThatThrownBy(() -> claimUseCase.confirmByDeposit(claimCode, 100_000L, DEPOSITED_AT))
			.isInstanceOf(PayoutClaimConflictException.class);
		entityManager.flush();
		entityManager.clear();

		assertThat(payoutItemRepository.count()).isEqualTo(lines);
		assertThat(claims()).extracting(PayoutClaim::getStatus).containsExactly(PayoutClaimStatus.청구_중);
		assertThat(output.getAll().lines().filter(line -> line.contains("입금액이 청구 금액과 달라")))
			.singleElement().asString().contains("ERROR", claimCode, "청구 금액=105500", "입금액=100000");
	}

	@Test
	@DisplayName("TC-DEPOSIT-4-1 없는 청구 ID로 입금 메시지가 오면 거부한다")
	void depositForUnknownClaimIsRejected() {
		assertThatThrownBy(() -> claimUseCase.confirmByDeposit("RC-20261214-FFFF", 105_500L, DEPOSITED_AT))
			.isInstanceOf(PayoutClaimNotFoundException.class);
	}
}
