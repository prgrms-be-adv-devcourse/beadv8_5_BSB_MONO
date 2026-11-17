package com.bukang.payout.app;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.payout.domain.PayoutStatus;
import com.bukang.payout.dto.SellerUnpaidDto;
import com.bukang.payout.out.PayoutRepository;

import lombok.RequiredArgsConstructor;

/**
 * 판매자가 아직 매듭짓지 않은 정산금이 남았는지 본다 (회원 탈퇴 제한). receivable(갚을 돈)과 방향이 반대다
 * 미수령 = 지급_완료 · 취소를 뺀 모든 상태다. 지급_완료는 받은 것, 취소는 대회가 취소돼 지급하지 않음이라 뺀다
 * 지급_중 · 지급_확인_필요 · 지급_불일치는 돈이 나갔는지 확정되지 않았거나 사람이 확인 중이라, 탈퇴를 막아 미해결 정산을 남기지 않는다
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PayoutUnpaidQueryUseCase {
	private static final Set<PayoutStatus> UNPAID =
		EnumSet.complementOf(EnumSet.of(PayoutStatus.지급_완료, PayoutStatus.취소));

	private final PayoutRepository payoutRepository;

	public SellerUnpaidDto findUnpaid(long sellerId) {
		return SellerUnpaidDto.of(sellerId, payoutRepository.existsBySellerIdAndStatusIn(sellerId, UNPAID));
	}
}
