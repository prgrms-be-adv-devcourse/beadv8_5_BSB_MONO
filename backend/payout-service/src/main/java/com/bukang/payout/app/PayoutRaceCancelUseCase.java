package com.bukang.payout.app;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutHold;
import com.bukang.payout.domain.PayoutStage;
import com.bukang.payout.domain.PayoutStatus;
import com.bukang.payout.out.PayoutRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 대회 취소 처리: 그 대회의 정산 내역을 지급 전후로 나눠 처리한다 (payout.md 4-2 · 4-3, PRO-34 · PRO-214)
 * - 지급 전(정산 예정 · 지급보류): 취소로 닫고 원장 줄을 반대 부호로 되돌린다. 보류로 막아 두면
 *   일찍 풀었을 때 돈이 나가므로 닫는다
 * - 지급 완료: 그대로 둔다. 회수는 주문이 환불한 참가권마다 보내는 환불 알림으로 만든다 (선 환불 후 회수, BUK-76)
 * - 지급 중: 돈이 나갔는지 몰라 닫지 않고 대회 취소를 표시한다. 거절되면 지급 단계가 닫고 회수를 되돌린다 (PRO-253)
 * - 지급 전 1차를 닫았으면 그 판매자 × 대회의 회수를 되돌린다. 판매자가 받은 돈이 없다 (R9)
 * 세미는 정산 생성일(대회 다음 날) 전에 대회가 취소되므로 처리할 정산 내역이 없는 게 보통이다 (payout.md 2-5)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutRaceCancelUseCase {
	private static final String CANCEL_REASON = "대회 취소";

	private final PayoutRepository payoutRepository;
	private final PayoutLedgerUseCase payoutLedgerUseCase;

	// 한 트랜잭션으로 묶는다. 지급 배치와 부딪혀 낙관적 락으로 실패하면 모두 되돌리고, 메시지를 다시 처리한다
	// 다시 처리해도 이미 닫은 정산 내역은 취소라 건너뛰고, 회수 되돌리기는 이미 되돌린 회수를 다시 고르지 않는다
	// 회수는 여기서 만들지 않는다. 주문이 환불한 참가권마다 보내는 환불 알림으로 만든다 (BUK-76, PayoutRefundUseCase)
	@Transactional
	public CancelResult cancelRace(long raceId, LocalDateTime now) {
		// 첫 문장에서 잠근다. 같은 대회의 환불 회수와 한 번에 하나만 돌아, 되돌리기가 막 만든 회수를 놓치지 않는다
		List<Payout> payouts = payoutRepository.findForUpdateByRaceId(raceId);
		int canceled = 0;
		boolean firstCanceled = false;
		int paying = 0;
		for (Payout payout : payouts) {
			if (payout.getStatus().isBeforePayment()) {
				payout.cancel(PayoutHold.SYSTEM_MEMBER_ID, now, CANCEL_REASON);
				canceled++;
				firstCanceled |= payout.getPayoutStage() == PayoutStage.일차_정산;
			} else if (payout.getStatus().isAwaitingPayResult()) {
				// 돈이 나갔는지 아직 모른다(지급 중 · 지급 확인 필요). 표시만 한다. 거절되면 지급 단계가 취소로 닫는다 (PRO-253)
				payout.markRaceCanceled(now);
				paying++;
			}
		}
		if (firstCanceled) {
			revertIfNoFinal(payouts, payouts.getFirst().getSellerId(), raceId);
		}
		log.info("대회 취소 처리: raceId={}, 정산 내역 {}건 중 {}건 취소, 지급 중 {}건", raceId, payouts.size(), canceled, paying);
		return new CancelResult(payouts.size(), canceled);
	}

	/**
	 * 대회 취소를 표시한 지급 중 정산 내역이 거절돼 취소로 닫힌 뒤 부른다 (PRO-253, PR #284 리뷰)
	 * 1차가 거절로 닫히면 돈이 나가지 않았으므로 지급 전 대회 취소와 같다. 그 판매자 × 대회의 회수를 되돌린다
	 * 지급 단계(PayoutPayStepUseCase)의 트랜잭션 안에서 돈다
	 */
	@Transactional
	public void revertAfterRejectedCancel(Payout canceled) {
		if (canceled.getPayoutStage() != PayoutStage.일차_정산) {
			return;
		}
		// 환불 회수와 겹치지 않게 같은 판매자 × 대회를 잠근다. 회수 읽기도 잠금 읽기라 막 커밋한 회수까지 본다
		payoutLedgerUseCase.lockSellerRace(canceled.getSellerId(), canceled.getRaceId());
		revertIfNoFinal(payoutRepository.findByRaceId(canceled.getRaceId()), canceled.getSellerId(),
			canceled.getRaceId());
	}

	// 1차를 돈이 나가기 전에 닫았을 때: 그 판매자 × 대회의 회수를 되돌린다 (R9, BUK-76)
	// 최종이 있으면 상계는 최종 정산 내역에서 일어난 것이라 되돌리지 않는다
	private void revertIfNoFinal(List<Payout> payouts, long sellerId, long raceId) {
		boolean hasFinal = payouts.stream().anyMatch(payout -> payout.getPayoutStage() == PayoutStage.최종_정산);
		if (!hasFinal) {
			payoutLedgerUseCase.revertRecoveries(sellerId, raceId);
		}
	}

	/**
	 * @param found 그 대회의 정산 내역 수
	 * @param canceled 이번에 취소로 닫은 정산 내역 수
	 */
	public record CancelResult(int found, int canceled) {
	}
}
