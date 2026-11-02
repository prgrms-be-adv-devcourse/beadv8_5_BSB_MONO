package com.bukang.payout.out;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.payout.domain.PayoutRecovery;
import com.bukang.payout.domain.PayoutRecoveryReason;
import com.bukang.payout.domain.PayoutRecoveryStatus;

public interface PayoutRecoveryRepository extends JpaRepository<PayoutRecovery, Long> {
	// 대회 취소 처리: 이 대회에 이 사유로 만든 회수가 있는지. 같은 이벤트가 다시 와도 회수를 두 번 만들지 않는다
	boolean existsByRaceIdAndReasonType(long raceId, PayoutRecoveryReason reasonType);

	// 일정 변경 회수: 이 참가권으로 이미 회수를 요청했는지 (참가권마다 1건)
	Optional<PayoutRecovery> findByOrderItemId(long orderItemId);

	// 갚을 돈이 0이 되면 같은 판매자 × 대회의 요청 중인 회수를 함께 완료한다 (PRO-214)
	List<PayoutRecovery> findBySellerIdAndRaceIdAndStatus(long sellerId, long raceId, PayoutRecoveryStatus status);

	// 청구 배치: 요청 중인 회수가 있는 판매자 × 대회를 찾는다. 갚을 돈이 남아 있으면 요청 중인 회수가 있다
	List<PayoutRecovery> findByStatus(PayoutRecoveryStatus status);

	// 청구 배치: 대회 취소 회수의 요청일(= 대회 취소일)로 청구 날짜를 정한다
	Optional<PayoutRecovery> findFirstBySellerIdAndRaceIdAndReasonType(long sellerId, long raceId,
		PayoutRecoveryReason reasonType);
}
