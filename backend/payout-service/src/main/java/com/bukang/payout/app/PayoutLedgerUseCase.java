package com.bukang.payout.app;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutAccount;
import com.bukang.payout.domain.PayoutRecovery;
import com.bukang.payout.domain.PayoutRecoveryStatus;
import com.bukang.payout.domain.PayoutStatus;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRecoveryRepository;
import com.bukang.payout.out.PayoutRepository;

import lombok.RequiredArgsConstructor;

/**
 * 원장 잔고와 회수 상계 (PRO-214)
 * 잔고는 판매자 × 대회 × 계정의 원장 줄을 더해 계산한다. 따로 저장하지 않는다
 * 상계는 갚을 돈(RECEIVABLE)을 같은 판매자 × 대회의 송금 전 정산 묶음에서 바로 뺀다. 묶음 금액까지만 뺀다
 * 회수 요청 · 최종 생성 · 대회 취소가 같은 규칙을 쓰도록 한곳에 둔다
 */
@Service
@RequiredArgsConstructor
public class PayoutLedgerUseCase {
	// 아직 송금하지 않은 정산 묶음. 보류(HELD)도 송금 전이라 상계 대상이다
	private static final List<PayoutStatus> UNSENT = List.of(PayoutStatus.CALCULATED, PayoutStatus.HELD);

	private final PayoutRepository payoutRepository;
	private final PayoutItemRepository payoutItemRepository;
	private final PayoutRecoveryRepository payoutRecoveryRepository;

	// JPQL 합계 쿼리 전에 Hibernate가 쌓인 원장 줄을 먼저 내보내므로(자동 flush) 같은 트랜잭션에서 쓴 줄도 더해진다
	public long balance(int sellerId, int raceId, PayoutAccount account) {
		return payoutItemRepository.sumBalance(sellerId, raceId, account);
	}

	/**
	 * 갚을 돈을 송금 전 묶음에서 빼고, 다 갚았으면 그 대회의 요청 중인 회수를 상계로 완료한다
	 * 송금 전 묶음을 잠가서 읽으므로 지급 배치 · 다른 회수 요청과 묶음 금액을 동시에 바꾸지 않는다
	 * 다시 불러도 잔고로 다시 계산하므로 이미 뺀 금액을 또 빼지 않는다
	 *
	 * @return 다 갚아서 이번에 완료한 회수 (못 다 갚았으면 빈 목록. 남은 갚을 돈은 입금으로 받는다)
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public List<PayoutRecovery> settle(int sellerId, int raceId, LocalDateTime now) {
		long receivable = balance(sellerId, raceId, PayoutAccount.RECEIVABLE);
		if (receivable <= 0) {
			return List.of();
		}
		for (Payout payout : payoutRepository.findForUpdate(sellerId, raceId, UNSENT)) {
			receivable -= payout.offsetReceivable(receivable);
			if (receivable == 0) {
				break;
			}
		}
		if (receivable > 0) {
			return List.of();
		}
		List<PayoutRecovery> requested = payoutRecoveryRepository.findBySellerIdAndRaceIdAndStatus(
			sellerId, raceId, PayoutRecoveryStatus.REQUESTED);
		requested.forEach(recovery -> recovery.completeByOffset(now));
		return requested;
	}
}
