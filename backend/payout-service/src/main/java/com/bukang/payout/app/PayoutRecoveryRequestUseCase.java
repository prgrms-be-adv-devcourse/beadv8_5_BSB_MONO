package com.bukang.payout.app;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutItem;
import com.bukang.payout.domain.PayoutRecordType;
import com.bukang.payout.domain.PayoutRecovery;
import com.bukang.payout.domain.PayoutStage;
import com.bukang.payout.domain.PayoutStatus;
import com.bukang.payout.domain.TwoStagePayoutCalculator;
import com.bukang.payout.domain.exception.PayoutRecoveryConflictException;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRecoveryRepository;
import com.bukang.payout.out.PayoutRepository;

import lombok.RequiredArgsConstructor;

/**
 * 일정 변경 환불의 회수 요청 (payout.md 4-2, PRO-33 · PRO-214)
 * 1차 정산을 만든 뒤의 환불은 1차 지급 전 · 뒤를 나누지 않고 항상 회수 요청으로 기록한다
 * 회수액은 그 참가권의 1차 몫(가격 × 40%, 원 미만 버림)이다. 가격은 주문 쪽 값을 받는다
 * 요청하면 같은 판매자 × 대회의 지급 전 정산 내역에서 바로 상계하고, 다 갚으면 회수가 바로 끝난다(구매자 환불로 이어짐)
 *
 * 지금은 정산 쪽 진입점만 만든다. 주문 서비스가 환불 신청 때 부르는 연결과 "회수 대기" 환불은 order 담당과 맞춘다
 */
@Service
@RequiredArgsConstructor
public class PayoutRecoveryRequestUseCase {
	private final PayoutRepository payoutRepository;
	private final PayoutItemRepository payoutItemRepository;
	private final PayoutRecoveryRepository payoutRecoveryRepository;
	private final PayoutLedgerUseCase payoutLedgerUseCase;

	/**
	 * @param raceCourseId 환불된 참가권의 코스 (원장 줄에 적어 코스별로 본다)
	 * @param price 환불된 참가권의 가격 (주문 상품 가격)
	 * @return 만든 회수. 1차 정산이 없으면(1차 정산금을 준 적이 없으면) 돌려받을 것이 없어 빈 값
	 */
	@Transactional
	public Optional<PayoutRecovery> requestScheduleChange(int sellerId, int raceId, int raceCourseId, int orderItemId,
		long price, LocalDateTime now) {
		if (payoutRecoveryRepository.existsByOrderItemId(orderItemId)) {
			throw new PayoutRecoveryConflictException("이미 회수를 요청한 참가권입니다.");
		}
		if (findLive(sellerId, raceId, PayoutStage.FINAL).isPresent()) {
			// 최종 정산을 만든 뒤의 환불은 번 돈(EARNING)까지 되돌려야 해서 아직 정하지 않았다 (PRO-214 미룸)
			throw new PayoutRecoveryConflictException("최종 정산을 만든 뒤의 환불은 아직 회수할 수 없습니다.");
		}
		if (findLive(sellerId, raceId, PayoutStage.FIRST).isEmpty()) {
			return Optional.empty();
		}

		long amount = TwoStagePayoutCalculator.firstShare(price);
		PayoutRecovery recovery = payoutRecoveryRepository.save(
			PayoutRecovery.scheduleChanged(sellerId, raceId, orderItemId, amount, now));
		// 1차 정산금에서 빼고, 같은 금액을 갚을 돈으로 옮긴다
		payoutItemRepository.save(
			PayoutItem.ofRecovery(recovery, PayoutRecordType.RECOVERY_REQUESTED, orderItemId, raceCourseId, -amount));
		payoutItemRepository.save(
			PayoutItem.ofRecovery(recovery, PayoutRecordType.RECEIVABLE_ADDED, orderItemId, raceCourseId, amount));

		payoutLedgerUseCase.settle(sellerId, raceId, now);
		return Optional.of(recovery);
	}

	// 대회 취소로 닫히지 않은 그 단계의 정산 내역
	private Optional<Payout> findLive(int sellerId, int raceId, PayoutStage stage) {
		return payoutRepository.findFirstBySellerIdAndRaceIdAndPayoutStageOrderByIdAsc(sellerId, raceId, stage)
			.filter(payout -> payout.getStatus() != PayoutStatus.CANCELED);
	}
}
