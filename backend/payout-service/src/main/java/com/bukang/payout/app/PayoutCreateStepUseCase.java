package com.bukang.payout.app;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutStage;
import com.bukang.payout.domain.PayoutTargetDate;
import com.bukang.payout.domain.TwoStagePayoutCalculator;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.out.SoldEntry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 정산 생성 배치의 정산 내역 하나(판매자 × 대회 × 단계)을 트랜잭션 하나로 저장한다 (PRO-60)
 * 정산 내역 · 정산 항목 · 자동 보류는 함께 저장되거나 함께 롤백된다. 보류만 빠지면 계좌 없는 판매자에게 지급될 수 있다
 * PayoutCreateUseCase 안의 메서드로 두면 같은 객체 안의 호출이라 트랜잭션 프록시를 거치지 않으므로 별도 빈으로 둔다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutCreateStepUseCase {
	private final PayoutRepository payoutRepository;
	private final BusinessDayCalendar businessDayCalendar;
	private final PayoutLedgerUseCase payoutLedgerUseCase;
	private final PayoutHoldUseCase payoutHoldUseCase;

	/**
	 * 1차 정산 내역을 만든다. 정산 월은 환불 기한의 달이고, 1차 정산금 줄은 판매 집계(가격 합 · 장수)로 계산한다
	 * 1차 정산금 = DOWN(1장 가격 × 40%) × 장수. 참가권 목록은 받지 않는다 (PRO-214)
	 * 다른 서비스에 묻는 값(대회 이름, 계좌 등록 여부)은 부르는 쪽이 미리 구해 넘긴다
	 * (다른 서비스 응답을 기다리는 동안 DB 트랜잭션을 열어 두지 않기 위해서다)
	 */
	@Transactional
	public void createFirst(FirstPayoutSource source, LocalDate refundDeadline, LocalDateTime now) {
		LocalDate targetDate = PayoutTargetDate.firstOf(refundDeadline);
		Payout payout = Payout.first(source.sellerId(), source.raceId(), source.raceName(),
			refundDeadline, now, businessDayCalendar);
		source.courses().forEach(course ->
			payout.recordAdvance(course.raceCourseId(), course.priceSum(), course.ticketCount(), targetDate));

		save(payout);
		payoutHoldUseCase.holdNewFirst(payout, source.hasPayoutAccount(), now);
	}

	/**
	 * 최종 정산 내역을 만든다. 정산 월은 대회일의 달이고, 같은 판매자 × 대회의 남은 1차 정산금(ADVANCE 잔고)을 1차 정산금 차감으로 뺀다
	 * 만든 뒤 아직 못 받은 갚을 돈(RECEIVABLE)이 있으면 이 정산 내역에서 바로 상계한다 (PRO-214)
	 * 자동 보류(1차가 보류 중이면 같은 사유로, 아니면 계좌 미등록일 때)는 PayoutHoldUseCase가 정한다
	 */
	@Transactional
	public void createFinal(FinalPayoutSource source, LocalDate raceDate, LocalDateTime now) {
		LocalDate targetDate = PayoutTargetDate.of(raceDate);
		Optional<Payout> first = payoutRepository.findFirstBySellerIdAndRaceIdAndPayoutStageOrderByIdAsc(
			source.sellerId(), source.raceId(), PayoutStage.FIRST);
		if (first.isEmpty()) {
			// 1차 없이 최종 대상이 된 경우의 처리는 PRO-190에서 정한다. 그때까지는 1차 정산금 차감 없이 만들고 드러낸다
			log.warn("1차 정산 내역 없이 최종 정산을 만듭니다. 선지급 차감은 0입니다. sellerId={}, raceId={}",
				source.sellerId(), source.raceId());
		}

		Payout payout = Payout.finalStage(source.sellerId(), source.raceId(), source.raceName(),
			raceDate, now, businessDayCalendar);

		source.entries().forEach(entry ->
			payout.addItem(entry.orderItemId(), entry.raceCourseId(), entry.price(), TwoStagePayoutCalculator.FEE_RATE,
				targetDate));

		save(payout);
		payoutLedgerUseCase.deductAdvanceAndSettle(payout, now);
		payoutHoldUseCase.holdNewFinal(payout, first, source.hasPayoutAccount(), now);
	}

	// 정산 ID의 끝 4자리는 PK에서 만들므로 저장해서 ID를 받은 뒤에 붙인다
	private void save(Payout payout) {
		payoutRepository.save(payout);
		payout.assignPayoutCode();
	}

	/**
	 * 1차 정산 내역 하나(판매자 × 대회)를 만들 재료: 코스별 판매 집계와, 저장에 필요한 다른 서비스의 값
	 * 정산 내역은 대회 단위이고, 1차 정산금 줄은 코스마다 하나씩 쓴다
	 */
	public record FirstPayoutSource(long sellerId, long raceId, String raceName, boolean hasPayoutAccount,
		List<CourseSale> courses) {
	}

	// 코스 하나의 판매 집계 (가격 합 · 장수)
	public record CourseSale(long raceCourseId, long priceSum, int ticketCount) {
	}

	/**
	 * 최종 정산 내역 하나(판매자 × 대회)를 만들 재료: 참가권과, 저장에 필요한 다른 서비스의 값
	 */
	public record FinalPayoutSource(long sellerId, long raceId, String raceName, boolean hasPayoutAccount,
		List<SoldEntry> entries) {
	}
}
