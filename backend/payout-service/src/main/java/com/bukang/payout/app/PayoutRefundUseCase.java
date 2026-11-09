package com.bukang.payout.app;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.common.shared.order.event.OrderItemRefundReason;
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
		// 사유가 없으면 대회 취소 환불인지 알 수 없다. 회수를 두 번 남길 수 있어 처리하지 않는다
		// IllegalArgumentException은 소비자가 재시도하지 않고 바로 DLT로 보낸다 (PayoutKafkaConsumer)
		if (event.reason() == null) {
			throw new IllegalArgumentException("환불 사유가 없는 알림입니다. orderItemId=%d, raceId=%d"
				.formatted(event.orderItemId(), event.raceId()));
		}
		// 대회 취소 일괄 환불은 대회 취소 알림(order.race.cancel)으로 대회당 한 번 회수한다. 여기서도 남기면 두 번 청구된다
		if (event.reason() == OrderItemRefundReason.RACE_CANCELED) {
			log.info("대회 취소 환불이라 회수를 남기지 않습니다. raceId={}, orderItemId={}", event.raceId(),
				event.orderItemId());
			return Optional.empty();
		}
		Optional<Payout> first = payoutRepository.findByRaceId(event.raceId()).stream()
			.filter(payout -> payout.getPayoutStage() == PayoutStage.일차_정산)
			.filter(payout -> payout.getStatus() != PayoutStatus.취소)
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
