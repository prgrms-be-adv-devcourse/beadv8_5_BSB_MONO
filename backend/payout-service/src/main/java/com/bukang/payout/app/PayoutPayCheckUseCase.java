package com.bukang.payout.app;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutPayCheckResult;
import com.bukang.payout.domain.exception.PayoutNotFoundException;
import com.bukang.payout.dto.PayoutDto;
import com.bukang.payout.out.PayoutRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 지급 확인 필요 정산 내역을 관리자가 확인한 결과대로 닫는다 (BUK-174)
 * 지급 배치가 결과를 알았을 때와 같은 길로 닫는다 (PayoutPayStepUseCase의 complete · revert)
 * - 지급됨: 지급 완료. 대회 취소 표시가 있으면 이제 결과가 나왔으므로 회수를 만든다
 * - 지급 안 됨: 지급 예정으로 되돌려 다음 지급 배치가 다시 지급한다. 대회 취소 표시가 있으면 취소로 닫고 회수를 계산한다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutPayCheckUseCase {
	private final PayoutRepository payoutRepository;
	private final PayoutRaceCancelUseCase payoutRaceCancelUseCase;

	@Transactional
	public PayoutDto close(long payoutId, PayoutPayCheckResult result, long adminId, String memo,
		LocalDateTime now) {
		Payout payout = payoutRepository.findById(payoutId).orElseThrow(PayoutNotFoundException::new);
		if (result == PayoutPayCheckResult.지급됨) {
			payout.confirmPaid(adminId, now, memo);
			if (payout.isRaceCanceled()) {
				payoutRaceCancelUseCase.recoverAfterPayResult(payout.getSellerId(), payout.getRaceId(), now);
			}
		} else {
			payout.confirmNotPaid(adminId, now, memo);
			if (payout.isRaceCanceled()) {
				payoutRaceCancelUseCase.recoverAfterRejectedCancel(payout, now);
			}
		}
		log.info("지급 확인 필요 정산 내역을 닫았습니다. payoutId={}, result={}, status={}, adminId={}",
			payoutId, result, payout.getStatus(), adminId);
		return PayoutDto.from(payout);
	}
}
