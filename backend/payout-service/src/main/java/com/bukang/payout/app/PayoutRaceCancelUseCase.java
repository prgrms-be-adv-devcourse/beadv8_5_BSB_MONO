package com.bukang.payout.app;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutHold;
import com.bukang.payout.domain.PayoutStage;
import com.bukang.payout.out.PayoutRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 대회 취소 처리: 그 대회의 정산 내역을 지급 전후로 나눠 처리한다 (payout.md 4-2 · 4-3, PRO-34 · PRO-214)
 * - 지급 전(정산 예정 · 지급보류): 취소(CANCELED)로 닫고 원장 줄을 반대 부호로 되돌린다. 보류로 막아 두면
 *   일찍 풀었을 때 돈이 나가므로 닫는다
 * - 지급 완료: 판매자가 손에 든 돈만큼 회수를 대회당 1건 만들도록 원장(PayoutLedgerUseCase)에 부탁한다
 * 정책 원문대로 구매자 환불은 회수가 끝난 뒤에 나간다. 주문 서비스 연결은 order 담당과 맞춘다
 * 세미는 정산 대상일(대회 다음 날) 전에 대회가 취소되므로 처리할 정산 내역이 없는 게 보통이다 (payout.md 2-5)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutRaceCancelUseCase {
	private static final String CANCEL_REASON = "대회 취소";

	private final PayoutRepository payoutRepository;
	private final PayoutLedgerUseCase payoutLedgerUseCase;

	// 한 트랜잭션으로 묶는다. 지급 배치와 부딪혀 낙관적 락으로 실패하면 모두 되돌리고, 메시지를 다시 처리한다
	// 다시 처리해도 이미 닫은 정산 내역은 CANCELED라 건너뛰고, 회수는 이미 있으면 만들지 않는다
	@Transactional
	public CancelResult cancelRace(long raceId, LocalDateTime now) {
		List<Payout> payouts = payoutRepository.findByRaceId(raceId);
		int canceled = 0;
		boolean firstCanceled = false;
		for (Payout payout : payouts) {
			if (payout.getStatus().isBeforePayment()) {
				payout.cancel(PayoutHold.SYSTEM_MEMBER_ID, now, CANCEL_REASON);
				canceled++;
				firstCanceled |= payout.getPayoutStage() == PayoutStage.FIRST;
			}
		}
		boolean hasFinal = payouts.stream().anyMatch(payout -> payout.getPayoutStage() == PayoutStage.FINAL);
		// 1차 지급 전 대회 취소: 그 1차 정산 내역에서 상계로 끝났던 회수도 되돌린다 (R9). 회수를 만들기 전에 한다
		if (firstCanceled && !hasFinal) {
			payoutLedgerUseCase.revertOffsetRecoveries(payouts.getFirst().getSellerId(), raceId);
		}

		boolean recoveryCreated = !payouts.isEmpty()
			&& payoutLedgerUseCase.recoverRaceCancel(payouts.getFirst().getSellerId(), raceId, now).isPresent();
		log.info("대회 취소 처리: raceId={}, 정산 내역 {}건 중 {}건 취소, 회수 생성 {}",
			raceId, payouts.size(), canceled, recoveryCreated);
		return new CancelResult(payouts.size(), canceled, recoveryCreated);
	}

	/**
	 * @param found 그 대회의 정산 내역 수
	 * @param canceled 이번에 취소로 닫은 정산 내역 수
	 * @param recoveryCreated 이번에 회수를 만들었는지
	 */
	public record CancelResult(int found, int canceled, boolean recoveryCreated) {
	}
}
