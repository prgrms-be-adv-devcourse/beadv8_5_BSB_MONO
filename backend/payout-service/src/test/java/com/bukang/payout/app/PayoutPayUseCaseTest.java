package com.bukang.payout.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutHoldReason;
import com.bukang.payout.domain.PayoutStage;
import com.bukang.payout.domain.PayoutStatus;
import com.bukang.payout.domain.exception.PayoutHoldConflictException;
import com.bukang.payout.dto.PayoutPayResultDto;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRecoveryRepository;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.out.PayoutTransfer;
import com.bukang.payout.out.PayoutTransferMismatchException;
import com.bukang.payout.out.PayoutTransferRejectedException;
import com.bukang.payout.out.PayoutTransferUnknownException;

import jakarta.persistence.EntityManager;

@DataJpaTest
@ActiveProfiles("test")
class PayoutPayUseCaseTest {
	private static final LocalDate PAY_DATE = LocalDate.of(2026, 11, 20);
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 4, 0);

	@Autowired
	private PayoutRepository payoutRepository;

	@Autowired
	private PayoutHoldRepository payoutHoldRepository;

	@Autowired
	private PayoutItemRepository payoutItemRepository;

	@Autowired
	private PayoutRecoveryRepository payoutRecoveryRepository;

	@Autowired
	private EntityManager entityManager;

	// 대회 취소를 표시한 지급 중이 없으면 부르지 않는다. 지급 단계를 만들려고 실제 저장소로 묶어 둔다
	private PayoutRaceCancelUseCase raceCancelUseCase() {
		return new PayoutRaceCancelUseCase(payoutRepository,
			new PayoutLedgerUseCase(payoutRepository, payoutItemRepository, payoutRecoveryRepository));
	}

	// 캐시 서비스에 보낸 요청. 기본은 성공이고, 테스트마다 정산 ID별로 거절 · 결과 모름을 정한다
	private final List<PayoutTransfer> transfers = new ArrayList<>();
	private Consumer<PayoutTransfer> cashService;

	@BeforeEach
	void setUp() {
		transfers.clear();
		cashService = transfer -> {
		};
	}

	private PayoutPayUseCase useCase() {
		return new PayoutPayUseCase(new PayoutPayStepUseCase(payoutRepository, raceCancelUseCase()), transfer -> {
			transfers.add(transfer);
			cashService.accept(transfer);
		});
	}

	// 지급 예정일은 정산 월(대회일의 달)의 다음 달 20일이다. 10/17 토 대회는 11/20 금, 11/17 화 대회는 12/20 일 (주말이어도 그날)
	private Payout savePayout(long sellerId, LocalDate raceDate) {
		LocalDateTime settledAt = raceDate.plusDays(1).atTime(3, 0);
		return payoutRepository.save(Payout.finalStage(sellerId, 10, "테스트 대회", raceDate, settledAt,
			new BusinessDayCalendar()));
	}

	@Test
	@DisplayName("11/20에 지급 예정일이 된 정산 내역(10/17 대회분)이 지급 완료로 바뀌고 지급 시각이 남는다")
	void paysPreviousMonthOnPayDate() {
		Payout dueNov20 = savePayout(1, LocalDate.of(2026, 10, 17));

		useCase().run(PAY_DATE, NOW);

		Payout paid = payoutRepository.findById(dueNov20.getId()).orElseThrow();
		assertThat(paid.getStatus()).isEqualTo(PayoutStatus.지급_완료);
		assertThat(paid.getPaidAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("지급 예정일이 안 된 정산 내역(11/17 대회분, 12/21 지급)은 11/20에 지급하지 않는다")
	void doesNotPayCurrentMonth() {
		Payout dueDec21 = savePayout(1, LocalDate.of(2026, 11, 17));

		useCase().run(PAY_DATE, NOW);

		assertThat(statusOf(dueDec21)).isEqualTo(PayoutStatus.지급_예정);
	}

	@Test
	@DisplayName("지급 예정일 전날(11/19)에는 10/17 대회분도 지급하지 않는다")
	void doesNotPayBeforePayDate() {
		Payout dueNov20 = savePayout(1, LocalDate.of(2026, 10, 17));

		useCase().run(PAY_DATE.minusDays(1), NOW.minusDays(1));

		assertThat(statusOf(dueNov20)).isEqualTo(PayoutStatus.지급_예정);
	}

	@Test
	@DisplayName("지급보류인 정산 내역은 지급 예정일이 지나도 지급_완료로 바뀌지 않는다")
	void doesNotPayHeld() {
		Payout held = savePayout(1, LocalDate.of(2026, 10, 17));
		payoutHoldRepository.save(held.hold(PayoutHoldReason.정산_계좌_미등록, null, 99, NOW.minusDays(5)));
		entityManager.flush();
		entityManager.clear();

		useCase().run(PAY_DATE, NOW);

		assertThat(statusOf(held)).isEqualTo(PayoutStatus.지급보류);
	}

	@Test
	@DisplayName("같은 날 다시 돌려도 이미 지급한 정산 내역의 지급 시각은 바뀌지 않는다")
	void runTwiceKeepsFirstPaidAt() {
		Payout dueNov20 = savePayout(1, LocalDate.of(2026, 10, 17));

		useCase().run(PAY_DATE, NOW);
		useCase().run(PAY_DATE, NOW.plusHours(1));

		assertThat(payoutRepository.findById(dueNov20.getId()).orElseThrow().getPaidAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("11/20 실행을 놓쳐도 11/21 실행에서 10/17 대회분을 지급한다")
	void catchesUpMissedPayDate() {
		Payout dueNov20 = savePayout(1, LocalDate.of(2026, 10, 17));

		useCase().run(PAY_DATE.plusDays(1), NOW.plusDays(1));

		assertThat(statusOf(dueNov20)).isEqualTo(PayoutStatus.지급_완료);
	}

	private PayoutStatus statusOf(Payout payout) {
		return payoutRepository.findById(payout.getId()).orElseThrow().getStatus();
	}

	@Test
	@DisplayName("1차: 환불 마감 10/17 → 11/20에 40,000(환불 없음) · 20,000(R0)을 지급 완료한다")
	void paysFirstPayoutOnPayDate() {
		BusinessDayCalendar calendar = new BusinessDayCalendar();
		LocalDateTime settledAt = LocalDateTime.of(2026, 10, 18, 3, 0);
		Payout twoTickets = Payout.first(1, 10, "테스트 대회", LocalDate.of(2026, 10, 17), settledAt, calendar);
		twoTickets.recordAdvance(10, 100_000L, 2, LocalDate.of(2026, 10, 18));
		Payout oneTicket = Payout.first(2, 10, "테스트 대회", LocalDate.of(2026, 10, 17), settledAt, calendar);
		oneTicket.recordAdvance(10, 50_000L, 1, LocalDate.of(2026, 10, 18));
		payoutRepository.save(twoTickets);
		payoutRepository.save(oneTicket);

		assertThat(twoTickets.getScheduledPayDate()).isEqualTo(PAY_DATE);
		useCase().run(PAY_DATE, NOW);

		Payout paidTwo = payoutRepository.findById(twoTickets.getId()).orElseThrow();
		assertThat(paidTwo.getStatus()).isEqualTo(PayoutStatus.지급_완료);
		assertThat(paidTwo.getPayoutAmount()).isEqualTo(40_000L);
		Payout paidOne = payoutRepository.findById(oneTicket.getId()).orElseThrow();
		assertThat(paidOne.getStatus()).isEqualTo(PayoutStatus.지급_완료);
		assertThat(paidOne.getPayoutAmount()).isEqualTo(20_000L);
	}

	@Test
	@DisplayName("최종 정산은 지급액과 수수료를, 1차 정산은 수수료 0원으로 판매자 회원 ID와 함께 캐시 서비스에 보낸다")
	void sendsAmountsToCashService() {
		BusinessDayCalendar calendar = new BusinessDayCalendar();
		Payout finalPayout = savePayoutWithSale(7);
		Payout first = Payout.first(8, 10, "테스트 대회", LocalDate.of(2026, 10, 17),
			LocalDateTime.of(2026, 10, 18, 3, 0), calendar);
		first.recordAdvance(10, 100_000L, 2, LocalDate.of(2026, 10, 18));
		payoutRepository.save(first);

		useCase().run(PAY_DATE, NOW);

		assertThat(transfers).containsExactlyInAnyOrder(
			new PayoutTransfer(finalPayout.getId(), 7, 47_250L, 2_750L, PayoutStage.최종_정산),
			new PayoutTransfer(first.getId(), 8, 40_000L, 0L, PayoutStage.일차_정산));
	}

	@Test
	@DisplayName("대금이 상계로 0원이어도 수수료가 남으면 캐시 서비스에 보내 수수료를 옮기고 지급 완료한다")
	void sendsFeeOnlyPayout() {
		// 판매액 50,000 − 수수료 2,750 − 1차 정산금 차감 47,250 = 지급액 0원
		Payout feeOnly = Payout.finalStage(1, 10, "테스트 대회", LocalDate.of(2026, 10, 17),
			LocalDateTime.of(2026, 10, 18, 3, 0), new BusinessDayCalendar());
		feeOnly.deductAdvance(47_250L);
		feeOnly.addItem(100, 50_000L, new BigDecimal("0.0550"), LocalDate.of(2026, 10, 1));
		payoutRepository.save(feeOnly);

		useCase().run(PAY_DATE, NOW);

		assertThat(transfers).containsExactly(
			new PayoutTransfer(feeOnly.getId(), 1, 0L, 2_750L, PayoutStage.최종_정산));
		assertThat(statusOf(feeOnly)).isEqualTo(PayoutStatus.지급_완료);
	}

	@Test
	@DisplayName("대금 · 수수료가 둘 다 0원이면 캐시 서비스를 부르지 않고 0원으로 지급 완료한다")
	void paysZeroAmountPayoutWithoutTransfer() {
		Payout zero = savePayout(1, LocalDate.of(2026, 10, 17));
		assertThat(zero.getPayoutAmount()).isZero();
		assertThat(zero.getFeeAmount()).isZero();

		PayoutPayResultDto result = useCase().run(PAY_DATE, NOW);

		assertThat(transfers).isEmpty();
		Payout paid = payoutRepository.findById(zero.getId()).orElseThrow();
		assertThat(paid.getStatus()).isEqualTo(PayoutStatus.지급_완료);
		assertThat(paid.getPaidAt()).isEqualTo(NOW);
		assertThat(result.paid()).isEqualTo(1);
	}

	@Test
	@DisplayName("캐시 서비스가 거절하면(4xx) 정산 예정으로 되돌리고, 다음 실행에서 다시 지급한다")
	void rejectedStaysCalculatedAndRetries() {
		Payout payout = savePayoutWithSale(1);
		cashService = transfer -> {
			throw new PayoutTransferRejectedException("지갑 없음", null);
		};

		PayoutPayResultDto rejected = useCase().run(PAY_DATE, NOW);

		assertThat(statusOf(payout)).isEqualTo(PayoutStatus.지급_예정);
		assertThat(rejected.paid()).isZero();
		assertThat(rejected.failed()).isEqualTo(1);

		cashService = transfer -> {
		};
		useCase().run(PAY_DATE.plusDays(1), NOW.plusDays(1));

		assertThat(statusOf(payout)).isEqualTo(PayoutStatus.지급_완료);
		assertThat(transfers).hasSize(2);
	}

	@Test
	@DisplayName("BUK-62: 캐시 서비스가 이미 다른 금액으로 지급했다고 답하면(409) 정산 예정으로 되돌리지 않고 지급 불일치로 둔다")
	void mismatchIsNotRevertedToCalculated() {
		Payout payout = savePayoutWithSale(1);
		cashService = transfer -> {
			throw new PayoutTransferMismatchException("저장된 금액 40000", null);
		};

		PayoutPayResultDto result = useCase().run(PAY_DATE, NOW);

		assertThat(statusOf(payout)).isEqualTo(PayoutStatus.지급_불일치);
		assertThat(result).isEqualTo(new PayoutPayResultDto(PAY_DATE, 0, 0, 0, 1));
	}

	@Test
	@DisplayName("BUK-62: 지급 불일치가 된 정산 내역은 다음 지급 배치가 다시 요청하지 않는다")
	void mismatchIsNotRequestedAgain() {
		Payout payout = savePayoutWithSale(1);
		cashService = transfer -> {
			throw new PayoutTransferMismatchException("저장된 금액 40000", null);
		};
		useCase().run(PAY_DATE, NOW);

		useCase().run(PAY_DATE.plusDays(1), NOW.plusDays(1));

		assertThat(transfers).hasSize(1);
		assertThat(statusOf(payout)).isEqualTo(PayoutStatus.지급_불일치);
	}

	@Test
	@DisplayName("결과를 모르면(타임아웃 · 5xx) 지급 중으로 두고, 다음 실행에서 같은 금액으로 다시 요청해 지급 완료한다")
	void unknownStaysPayingAndRetriesWithSameAmount() {
		Payout payout = savePayoutWithSale(1);
		cashService = transfer -> {
			throw new PayoutTransferUnknownException("read timed out", null);
		};

		PayoutPayResultDto unknown = useCase().run(PAY_DATE, NOW);

		assertThat(statusOf(payout)).isEqualTo(PayoutStatus.지급_중);
		assertThat(unknown.unknown()).isEqualTo(1);

		cashService = transfer -> {
		};
		useCase().run(PAY_DATE.plusDays(1), NOW.plusDays(1));

		assertThat(statusOf(payout)).isEqualTo(PayoutStatus.지급_완료);
		assertThat(transfers).hasSize(2);
		assertThat(transfers.get(1)).isEqualTo(transfers.get(0));
	}

	@Test
	@DisplayName("지급 중으로 남은 정산 내역에는 보류를 걸 수 없다")
	void cannotHoldWhileUnknown() {
		Payout payout = savePayoutWithSale(1);
		cashService = transfer -> {
			throw new PayoutTransferUnknownException("read timed out", null);
		};
		useCase().run(PAY_DATE, NOW);

		Payout paying = payoutRepository.findById(payout.getId()).orElseThrow();
		assertThatThrownBy(() -> paying.hold(PayoutHoldReason.정산_계좌_미등록, null, 99, NOW))
			.isInstanceOf(PayoutHoldConflictException.class);
	}

	@Test
	@DisplayName("BUK-62: 지급 불일치 정산 내역에는 보류를 걸 수 없다. 풀면 정산 예정으로 돌아가 다시 요청하기 때문이다")
	void cannotHoldMismatch() {
		Payout payout = savePayoutWithSale(1);
		cashService = transfer -> {
			throw new PayoutTransferMismatchException("저장된 금액 40000", null);
		};
		useCase().run(PAY_DATE, NOW);

		Payout mismatch = payoutRepository.findById(payout.getId()).orElseThrow();
		assertThatThrownBy(() -> mismatch.hold(PayoutHoldReason.정산_계좌_미등록, null, 99, NOW))
			.isInstanceOf(PayoutHoldConflictException.class);
	}

	@Test
	@DisplayName("한 건이 실패해도 나머지 정산 내역은 지급한다")
	void otherPayoutsArePaidWhenOneFails() {
		Payout rejected = savePayoutWithSale(1);
		Payout unknown = savePayoutWithSale(2);
		Payout ok = savePayoutWithSale(3);
		cashService = transfer -> {
			if (transfer.payoutId() == rejected.getId()) {
				throw new PayoutTransferRejectedException("지갑 없음", null);
			}
			if (transfer.payoutId() == unknown.getId()) {
				throw new PayoutTransferUnknownException("read timed out", null);
			}
		};

		PayoutPayResultDto result = useCase().run(PAY_DATE, NOW);

		assertThat(statusOf(rejected)).isEqualTo(PayoutStatus.지급_예정);
		assertThat(statusOf(unknown)).isEqualTo(PayoutStatus.지급_중);
		assertThat(statusOf(ok)).isEqualTo(PayoutStatus.지급_완료);
		assertThat(result).isEqualTo(new PayoutPayResultDto(PAY_DATE, 1, 1, 1, 0));
	}

	@Test
	@DisplayName("어댑터 밖의 예상 못 한 오류도 돈이 나갔는지 모르는 것으로 보고 지급 중으로 둔다")
	void unexpectedErrorStaysPaying() {
		Payout payout = savePayoutWithSale(1);
		cashService = transfer -> {
			throw new IllegalStateException("예상 못 한 오류");
		};

		useCase().run(PAY_DATE, NOW);

		assertThat(statusOf(payout)).isEqualTo(PayoutStatus.지급_중);
	}

	@Test
	@DisplayName("다른 배치가 먼저 지급을 시작해 낙관적 락으로 부딪히면 그 건은 캐시 서비스를 부르지 않고 건너뛴다")
	void skipsWhenAnotherRunStartedFirst() {
		Payout payout = savePayoutWithSale(1);
		PayoutPayStepUseCase conflicting = new PayoutPayStepUseCase(payoutRepository, raceCancelUseCase()) {
			@Override
			public Start start(long payoutId, LocalDate today, LocalDateTime now) {
				throw new ObjectOptimisticLockingFailureException(Payout.class, payoutId);
			}
		};

		PayoutPayResultDto result = new PayoutPayUseCase(conflicting, transfers::add).run(PAY_DATE, NOW);

		assertThat(transfers).isEmpty();
		assertThat(statusOf(payout)).isEqualTo(PayoutStatus.지급_예정);
		assertThat(result).isEqualTo(new PayoutPayResultDto(PAY_DATE, 0, 0, 0, 0));
	}

	// 10/17 대회분(11/20 지급), 판매 50,000 → 지급액 47,250 · 수수료 2,750
	private Payout savePayoutWithSale(long sellerId) {
		Payout payout = savePayout(sellerId, LocalDate.of(2026, 10, 17));
		payout.addItem(100 + sellerId, 50_000L, new BigDecimal("0.0550"), LocalDate.of(2026, 9, 26));
		return payout;
	}

	// PRO-188: 최종은 같은 판매자 · 대회의 1차가 지급 완료된 뒤에만 지급한다
	// 마감일 = 대회일 = 10/17이면 1차 · 최종 모두 11/20 지급 예정이다 (같은 날 지급)
	private Payout saveFirstWithAdvance(long sellerId, long priceSum) {
		Payout first = Payout.first(sellerId, 10, "테스트 대회", LocalDate.of(2026, 10, 17),
			LocalDateTime.of(2026, 10, 18, 3, 0), new BusinessDayCalendar());
		if (priceSum > 0) {
			first.recordAdvance(10, priceSum, 1, LocalDate.of(2026, 10, 18));
		}
		return payoutRepository.save(first);
	}

	@Test
	@DisplayName("같은 날 1차 · 최종이 함께 대상이면 같은 실행에서 1차 → 최종 순서로 지급한다 (ID 순서로 처리하고 1차를 먼저 저장한다)")
	void sameDayPaysFirstThenFinalInOneRun() {
		Payout first = saveFirstWithAdvance(1, 50_000L);
		Payout last = savePayoutWithSale(1);

		useCase().run(PAY_DATE, NOW);

		assertThat(statusOf(first)).isEqualTo(PayoutStatus.지급_완료);
		assertThat(statusOf(last)).isEqualTo(PayoutStatus.지급_완료);
		assertThat(transfers).extracting(PayoutTransfer::payoutId).containsExactly(first.getId(), last.getId());
	}

	@Test
	@DisplayName("1차 이체 결과를 몰라 지급 중으로 남으면, 같은 대회의 최종은 이번 실행에서 건너뛰고 정산 예정으로 둔다")
	void finalWaitsWhileFirstIsPaying() {
		Payout first = saveFirstWithAdvance(1, 50_000L);
		Payout last = savePayoutWithSale(1);
		cashService = transfer -> {
			if (transfer.payoutId() == first.getId()) {
				throw new PayoutTransferUnknownException("read timed out", null);
			}
		};

		useCase().run(PAY_DATE, NOW);

		assertThat(statusOf(first)).isEqualTo(PayoutStatus.지급_중);
		assertThat(statusOf(last)).isEqualTo(PayoutStatus.지급_예정);
		assertThat(transfers).extracting(PayoutTransfer::payoutId).containsExactly(first.getId());
	}

	@Test
	@DisplayName("최종을 만든 뒤 1차에만 보류가 걸리면 최종은 기다리고, 1차를 풀어 지급한 뒤에 최종을 지급한다")
	void finalWaitsWhileFirstIsHeld() {
		Payout first = saveFirstWithAdvance(1, 50_000L);
		Payout last = savePayoutWithSale(1);
		var hold = payoutHoldRepository.save(
			first.hold(PayoutHoldReason.정산_계좌_미등록, null, 99, NOW.minusHours(1)));
		entityManager.flush();
		entityManager.clear();

		useCase().run(PAY_DATE, NOW);
		assertThat(statusOf(first)).isEqualTo(PayoutStatus.지급보류);
		assertThat(statusOf(last)).isEqualTo(PayoutStatus.지급_예정);

		// 11/20 해제 → 1차는 11/21 지급 예정
		Payout heldFirst = payoutRepository.findById(first.getId()).orElseThrow();
		heldFirst.release(payoutHoldRepository.findById(hold.getId()).orElseThrow(), 99, NOW.plusHours(5),
			new BusinessDayCalendar());
		entityManager.flush();
		entityManager.clear();

		useCase().run(PAY_DATE.plusDays(1), NOW.plusDays(1));
		useCase().run(PAY_DATE.plusDays(2), NOW.plusDays(2));

		assertThat(statusOf(first)).isEqualTo(PayoutStatus.지급_완료);
		assertThat(statusOf(last)).isEqualTo(PayoutStatus.지급_완료);
		assertThat(transfers).extracting(PayoutTransfer::payoutId).containsExactly(first.getId(), last.getId());
	}

	@Test
	@DisplayName("같은 대회의 1차가 없는 최종(1차를 만들지 못함, PRO-190)은 기다리지 않고 지급한다")
	void finalWithoutFirstIsPaid() {
		Payout last = savePayoutWithSale(1);

		useCase().run(PAY_DATE, NOW);

		assertThat(statusOf(last)).isEqualTo(PayoutStatus.지급_완료);
	}

	@Test
	@DisplayName("다른 판매자 · 다른 대회의 1차가 지급 전이어도 최종은 막히지 않는다")
	void otherFirstDoesNotBlockFinal() {
		Payout otherSellerFirst = saveFirstWithAdvance(2, 50_000L);
		payoutHoldRepository.save(
			otherSellerFirst.hold(PayoutHoldReason.정산_계좌_미등록, null, 99, NOW.minusHours(1)));
		Payout last = savePayoutWithSale(1);

		useCase().run(PAY_DATE, NOW);

		assertThat(statusOf(last)).isEqualTo(PayoutStatus.지급_완료);
	}
}
