package com.bukang.payout.app;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.common.shared.order.event.OrderItemRefundedEvent;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutRecovery;
import com.bukang.payout.domain.PayoutStage;
import com.bukang.payout.domain.PayoutStatus;
import com.bukang.payout.out.PayoutRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 참가권 환불 알림을 받아 판매자에게서 돌려받을 회수를 남긴다 (선 환불 후 회수 R1)
 * 구매자 환불은 주문 · 캐시가 먼저 끝냈고, 정산은 장부에 남기기만 한다
 * 판매자는 알림에 없으므로 그 대회의 1차 정산 내역에서 찾는다. 1차 정산이 없으면 준 돈이 없어 남길 것도 없다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutRefundUseCase {
	private final PayoutRepository payoutRepository;
	private final PayoutLedgerUseCase payoutLedgerUseCase;

	@Transactional
	public Optional<PayoutRecovery> recordRefund(OrderItemRefundedEvent event, LocalDateTime now) {
		Optional<Payout> first = payoutRepository.findByRaceId(event.raceId()).stream()
			.filter(payout -> payout.getPayoutStage() == PayoutStage.FIRST)
			.filter(payout -> payout.getStatus() != PayoutStatus.CANCELED)
			.findFirst();
		if (first.isEmpty()) {
			log.info("1차 정산이 없는 대회의 환불이라 회수를 남기지 않습니다. raceId={}, orderItemId={}",
				event.raceId(), event.orderItemId());
			return Optional.empty();
		}
		return payoutLedgerUseCase.recoverScheduleChange(first.get().getSellerId(), event.raceId(),
			event.raceCourseId(), event.orderItemId(), event.price(), now);
	}
}
