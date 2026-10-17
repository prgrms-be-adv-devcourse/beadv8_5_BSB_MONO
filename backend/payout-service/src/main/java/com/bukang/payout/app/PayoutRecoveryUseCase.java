package com.bukang.payout.app;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.payout.domain.PayoutAccount;
import com.bukang.payout.domain.PayoutItem;
import com.bukang.payout.domain.PayoutRecordType;
import com.bukang.payout.domain.PayoutRecovery;
import com.bukang.payout.domain.PayoutRecoveryStatus;
import com.bukang.payout.domain.exception.PayoutRecoveryConflictException;
import com.bukang.payout.domain.exception.PayoutRecoveryNotFoundException;
import com.bukang.payout.dto.PayoutRecoveryDto;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRecoveryRepository;

import lombok.RequiredArgsConstructor;

/**
 * 관리자의 회수 완료: 판매자의 입금을 확인하고 회수를 끝낸다 (payout.md 4-2, PRO-33)
 * 판매자 입금 요청 · 시스템 입금 확인 이슈가 생기기 전까지 쓰는 임시 경로다 (PRO-214)
 *
 * 입금액은 이 회수의 금액과 그 대회의 남은 갚을 돈 중 작은 쪽이다 (상계로 일부를 이미 갚았을 수 있다)
 * 입금으로 그 대회의 갚을 돈이 0이 되면, 남은 요청 중인 회수는 앞서 상계로 갚은 몫이므로 상계로 함께 완료한다
 * (상계로 못 다 갚은 회수들을 묶어 두었다가 다 갚으면 한 번에 끝내는 규칙. 여러 회수를 한 번에 입금받는 요청은 별도 이슈에서 만든다)
 */
@Service
@RequiredArgsConstructor
public class PayoutRecoveryUseCase {
	private final PayoutRecoveryRepository payoutRecoveryRepository;
	private final PayoutItemRepository payoutItemRepository;
	private final PayoutLedgerUseCase payoutLedgerUseCase;

	@Transactional
	public PayoutRecoveryDto complete(int recoveryId, int completedBy, LocalDateTime now) {
		PayoutRecovery recovery = payoutRecoveryRepository.findByIdForUpdate(recoveryId)
			.orElseThrow(PayoutRecoveryNotFoundException::new);
		if (recovery.getStatus() == PayoutRecoveryStatus.COMPLETED) {
			throw new PayoutRecoveryConflictException("이미 완료된 회수입니다.");
		}
		long receivable = payoutLedgerUseCase.balance(recovery.getSellerId(), recovery.getRaceId(),
			PayoutAccount.RECEIVABLE);
		long deposit = Math.min(recovery.getAmount(), receivable);
		if (deposit > 0) {
			payoutItemRepository.save(
				PayoutItem.ofRecovery(recovery, PayoutRecordType.RECOVERY_RECEIVED, null, null, -deposit));
		}
		recovery.complete(completedBy, now);
		if (receivable - deposit == 0) {
			payoutRecoveryRepository.findBySellerIdAndRaceIdAndStatus(recovery.getSellerId(), recovery.getRaceId(),
					PayoutRecoveryStatus.REQUESTED)
				.forEach(requested -> requested.completeByOffset(now));
		}
		return PayoutRecoveryDto.from(recovery);
	}
}
