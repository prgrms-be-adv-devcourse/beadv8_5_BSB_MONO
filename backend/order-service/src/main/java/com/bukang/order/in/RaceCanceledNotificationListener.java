package com.bukang.order.in;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.bukang.common.global.eventpublisher.EventPublisher;
import com.bukang.common.shared.notification.NotificationKind;
import com.bukang.common.shared.notification.event.NotificationRequestedEvent;
import com.bukang.order.domain.event.OrderRaceCanceledEvent;

import lombok.RequiredArgsConstructor;

/**
 * 대회 취소로 환불한 구매자에게 알린다 (PRO-135, BUK-69)
 * 환불 트랜잭션 안에서 알림 요청을 발행하고, common의 NotificationKafkaSender가 커밋된 뒤에만 보낸다 (롤백된 환불은 알리지 않는다)
 * AFTER_COMMIT 리스너로 두면 그 안에서 발행한 알림이 이미 커밋 단계라 보내지지 않으므로 트랜잭션 안에서 받는다
 */
@Component
@RequiredArgsConstructor
public class RaceCanceledNotificationListener {
	private final EventPublisher eventPublisher;

	@EventListener
	public void notifyBuyer(OrderRaceCanceledEvent event) {
		eventPublisher.publish(NotificationRequestedEvent.of(event.buyerId(), NotificationKind.RACE_CANCELED,
			"대회가 취소됐어요",
			event.itemName() + " · 참가비 전액\n" + String.format("₩%,d", event.refundedAmount())
				+ "을 예치금으로 돌려드렸어요.",
			"/tickets"));
	}
}
