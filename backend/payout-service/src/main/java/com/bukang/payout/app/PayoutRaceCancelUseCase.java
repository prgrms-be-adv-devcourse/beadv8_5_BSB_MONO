package com.bukang.payout.app;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutAccount;
import com.bukang.payout.domain.PayoutHold;
import com.bukang.payout.domain.PayoutItem;
import com.bukang.payout.domain.PayoutRecordType;
import com.bukang.payout.domain.PayoutRecovery;
import com.bukang.payout.domain.PayoutRecoveryReason;
import com.bukang.payout.domain.PayoutStatus;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRecoveryRepository;
import com.bukang.payout.out.PayoutRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 대회 취소 처리: 그 대회의 정산 내역을 지급 전후로 나눠 처리한다 (payout.md 4-2 · 4-3, PRO-34 · PRO-214)
 * - 지급 전(정산 예정 · 지급보류): 취소(CANCELED)로 닫고 원장 줄을 반대 부호로 되돌린다. 보류로 막아 두면
 *   일찍 풀었을 때 돈이 나가므로 닫는다
 * - 지급 완료: 판매자가 손에 든 돈만큼 회수를 대회당 1건 만든다 (1차 정산금에서 빼고 갚을 돈으로 옮기는 두 줄)
 * 정책 원문대로 구매자 환불은 회수가 끝난 뒤에 나간다. 주문 서비스 연결은 order 담당과 맞춘다
 * 세미는 정산 대상일(대회 다음 날) 전에 대회가 취소되므로 처리할 정산 내역이 없는 게 보통이다 (payout.md 2-5)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutRaceCancelUseCase {
	private static final String CANCEL_REASON = "대회 취소";

	private final PayoutRepository payoutRepository;
	private final PayoutItemRepository payoutItemRepository;
	private final PayoutRecoveryRepository payoutRecoveryRepository;
	private final PayoutLedgerUseCase payoutLedgerUseCase;

	// 한 트랜잭션으로 묶는다. 지급 배치와 부딪혀 낙관적 락으로 실패하면 모두 되돌리고, 메시지를 다시 처리한다
	// 다시 처리해도 이미 닫은 정산 내역은 CANCELED라 건너뛰고, 회수는 이미 있으면 만들지 않는다
	@Transactional
	public CancelResult cancelRace(int raceId, LocalDateTime now) {
		List<Payout> payouts = payoutRepository.findByRaceId(raceId);
		int canceled = 0;
		for (Payout payout : payouts) {
			if (payout.getStatus() == PayoutStatus.CALCULATED || payout.getStatus() == PayoutStatus.HELD) {
				payout.cancel(PayoutHold.SYSTEM_MEMBER_ID, now, CANCEL_REASON);
				canceled++;
			}
		}

		boolean recoveryCreated = false;
		if (!payouts.isEmpty()
			&& !payoutRecoveryRepository.existsByRaceIdAndReasonType(raceId, PayoutRecoveryReason.RACE_CANCELED)) {
			recoveryCreated = requestRecovery(payouts.getFirst().getSellerId(), raceId, now);
		}
		log.info("대회 취소 처리: raceId={}, 정산 내역 {}건 중 {}건 취소, 회수 생성 {}",
			raceId, payouts.size(), canceled, recoveryCreated);
		return new CancelResult(payouts.size(), canceled, recoveryCreated);
	}

	// 판매자가 손에 든 돈 = 남은 1차 정산금 + 번 돈. 지급 전 정산 내역은 방금 되돌렸으므로 지급한 정산 내역의 몫만 남는다
	// 이미 요청 중인 갚을 돈(RECEIVABLE)은 따로 받으므로 더하지 않는다
	private boolean requestRecovery(int sellerId, int raceId, LocalDateTime now) {
		long advance = payoutLedgerUseCase.balance(sellerId, raceId, PayoutAccount.ADVANCE);
		long earning = payoutLedgerUseCase.balance(sellerId, raceId, PayoutAccount.EARNING);
		if (earning != 0) {
			// 최종 지급 뒤의 대회 취소는 번 돈까지 되돌려야 하는데 아직 정하지 않았다 (PRO-214 미룸). 금액만 맞춰 1차 정산금 줄로 남긴다
			log.warn("최종 지급 뒤 대회 취소: 번 돈까지 회수합니다. sellerId={}, raceId={}, earning={}",
				sellerId, raceId, earning);
		}
		long amount = advance + earning;
		if (amount < 0) {
			// 회수 요청액이 준 1차 정산금보다 많은 상태(지급 전 정산 내역에 상계했던 회수가 남음). 처리 규칙을 정하지 않았다
			log.warn("대회 취소 회수액이 음수라 만들지 않습니다. sellerId={}, raceId={}, amount={}", sellerId, raceId, amount);
		}
		// 지급액이 0원인 정산 내역(0원 지급 완료)만 있으면 돌려받을 돈이 없다
		if (amount <= 0) {
			return false;
		}
		PayoutRecovery recovery = payoutRecoveryRepository.save(
			new PayoutRecovery(sellerId, raceId, PayoutRecoveryReason.RACE_CANCELED, amount, now));
		payoutItemRepository.save(
			PayoutItem.ofRecovery(recovery, PayoutRecordType.RECOVERY_REQUESTED, null, null, -amount));
		payoutItemRepository.save(
			PayoutItem.ofRecovery(recovery, PayoutRecordType.RECEIVABLE_ADDED, null, null, amount));
		return true;
	}

	/**
	 * @param found 그 대회의 정산 내역 수
	 * @param canceled 이번에 취소로 닫은 정산 내역 수
	 * @param recoveryCreated 이번에 회수를 만들었는지
	 */
	public record CancelResult(int found, int canceled, boolean recoveryCreated) {
	}
}
