package com.bukang.payout.app;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import com.bukang.payout.domain.PayoutCreateDate;
import com.bukang.payout.domain.PayoutCreateFailure;
import com.bukang.payout.domain.PayoutStage;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.out.RacePort;
import com.bukang.payout.out.RaceSchedule;
import com.bukang.payout.out.SoldEntry;
import com.bukang.payout.out.SoldEntryPort;
import com.bukang.payout.out.SoldSummary;
import com.bukang.payout.out.SoldSummaryPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 정산 대사 (BUK-67): 정산 생성 배치 뒤에 주문 판매와 정산 내역을 비교해, 코드가 알아채지 못한 빠짐 · 어긋남을 찾는다
 * BUK-60 실패 기록은 코드가 알아챈 실패만 남긴다. 대사는 원인을 몰라도 숫자가 안 맞으면 실패 기록표에 '대사_불일치'로 남긴다
 *
 * 대상 대회 = ① 그날 새로 대상이 된 대회(1차는 환불 마감일이 어제, 최종은 대회일이 어제)
 *          + ② 이번 실행에서 정산 내역이 만들어진 대회(되돌아보기로 늦게 만들어진 정산, 정산 시각 >= 실행 시작 시각)
 * 대사는 정산을 만든 그 실행에서만 비교하므로 대사 시점의 정산은 늘 지급 전이다 (지급은 다음 달 20일)
 *
 * 이 클래스에는 트랜잭션을 걸지 않는다. 대회 하나의 대사가 실패해도 로그만 남기고 다음 대회를 본다 (AC-67-10)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutReconcileUseCase {
	private final PayoutRepository payoutRepository;
	private final RacePort racePort;
	private final SoldSummaryPort soldSummaryPort;
	private final SoldEntryPort soldEntryPort;
	private final PayoutReconcileStepUseCase payoutReconcileStepUseCase;
	private final PayoutCreateFailureUseCase payoutCreateFailureUseCase;

	public void run(LocalDate today, LocalDateTime now) {
		for (RaceSchedule race : targetRaces(PayoutStage.FIRST, today, now)) {
			List<SoldSummary> summaries;
			try {
				summaries = soldSummaryPort.summarizeSold(race.raceId());
			} catch (RuntimeException exception) {
				failOrderLookup(PayoutStage.FIRST, race.raceId(), race.refundDeadline(), today, exception);
				continue;
			}
			recordMismatches(PayoutStage.FIRST, race,
				() -> payoutReconcileStepUseCase.reconcileFirst(race, summaries, today, now));
		}
		for (RaceSchedule race : targetRaces(PayoutStage.FINAL, today, now)) {
			List<SoldEntry> entries;
			try {
				entries = soldEntryPort.findSoldEntries(race.raceId());
			} catch (RuntimeException exception) {
				failOrderLookup(PayoutStage.FINAL, race.raceId(), race.raceDate(), today, exception);
				continue;
			}
			recordMismatches(PayoutStage.FINAL, race,
				() -> payoutReconcileStepUseCase.reconcileFinal(race, entries, today, now));
		}
	}

	// ②의 대회는 생성 배치의 되돌아보기 기간 안에서만 만들어지므로, 그 기간의 대회 목록에서 ① · ②를 함께 고른다
	private List<RaceSchedule> targetRaces(PayoutStage stage, LocalDate today, LocalDateTime now) {
		LocalDate newTargetDate = stage == PayoutStage.FIRST
			? PayoutCreateDate.refundDeadlineFor(today) : PayoutCreateDate.raceDateFor(today);
		LocalDate from = newTargetDate.minusDays(PayoutCreateUseCase.LOOKBACK_DAYS - 1);
		List<RaceSchedule> races;
		try {
			races = stage == PayoutStage.FIRST
				? racePort.findRacesByRefundDeadline(from, newTargetDate)
				: racePort.findRacesByRaceDate(from, newTargetDate);
		} catch (RuntimeException exception) {
			log.error("대사 대상 대회를 상품 서비스에서 받지 못했습니다. stage={}", stage, exception);
			payoutCreateFailureUseCase.record(PayoutCreateFailure.raceLookup(stage, from, newTargetDate, today));
			return List.of();
		}
		Set<Long> settledNow = Set.copyOf(payoutRepository.findRaceIdsSettledSince(stage, now));
		return races.stream()
			.filter(race -> targetDateOf(stage, race).equals(newTargetDate) || settledNow.contains(race.raceId()))
			.toList();
	}

	private static LocalDate targetDateOf(PayoutStage stage, RaceSchedule race) {
		return stage == PayoutStage.FIRST ? race.refundDeadline() : race.raceDate();
	}

	// 대사 중 주문 조회만 실패한 대회는 다시 비교하지 않는다 (11/5 결정, AC-67-11)
	private void failOrderLookup(PayoutStage stage, long raceId, LocalDate payoutTargetDate, LocalDate today,
		RuntimeException exception) {
		log.error("대사용 판매 값을 주문 서비스에서 받지 못했습니다. stage={}, raceId={}", stage, raceId, exception);
		payoutCreateFailureUseCase.record(PayoutCreateFailure.orderLookup(stage, raceId, payoutTargetDate, today));
	}

	// 기록 저장이 실패해도 PayoutCreateFailureUseCase가 ERROR 로그만 남기고 넘어간다 (TC-67-10-1)
	private void recordMismatches(PayoutStage stage, RaceSchedule race, Supplier<List<PayoutCreateFailure>> step) {
		try {
			step.get().forEach(payoutCreateFailureUseCase::record);
		} catch (RuntimeException exception) {
			log.error("정산 대사를 하지 못했습니다. stage={}, raceId={}", stage, race.raceId(), exception);
		}
	}
}
