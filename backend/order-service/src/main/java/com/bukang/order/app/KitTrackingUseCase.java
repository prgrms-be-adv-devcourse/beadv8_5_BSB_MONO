package com.bukang.order.app;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.common.global.eventpublisher.EventPublisher;
import com.bukang.common.shared.notification.NotificationKind;
import com.bukang.common.shared.notification.event.NotificationRequestedEvent;
import com.bukang.order.domain.Order;
import com.bukang.order.domain.Shipment;
import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.out.OrderRepository;
import com.bukang.order.out.ShipmentRepository;
import com.bukang.order.out.ShipmentTrackingTarget;
import com.bukang.order.out.delivery.DeliveryTrackingPort;
import com.bukang.order.out.delivery.DeliveryTrackingResult;
import com.bukang.order.out.seller.SellerLookupPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 배송 중인 키트의 배송 상태를 Delivery Tracker에서 받아 온다 (BUK-55, KitTrackingScheduler가 30분마다 부름)
 * 다음 조회 시각이 된 배송만 조회한다. 다음 조회 시각은 받은 상태로 정한다: 배달 출발·배달 실패·수령 대기는 30분, 그 밖은 3시간 (BUK-90)
 * 발송 7일 뒤에도 아직 조회되지 않으면 조회를 멈추고 판매자에게 송장 번호를 확인해 달라고 한 번 알린다 (BUK-90)
 * 다음 조회 시각이 오래된 배송부터 한 번에 batch-size건, 요청 사이를 request-interval만큼 띄운다 (Delivery Tracker는 초당 10번까지)
 * 상대를 부르는 동안에는 주문을 잠그지 않는다. 결과를 남길 때 주문을 잠근다 (배송 상태를 바꾸는 곳은 모두 주문을 먼저 잠근다)
 * 택배사가 배송 완료라고 하면 키트를 배송 완료로 바꾸므로 다음 조회부터 빠진다 (정책 5장 3, 회의 확인 전 기본값)
 * 한 배송의 조회가 실패해도(어댑터가 연결 오류는 3번 다시 부른 뒤) 조회 시각만 바꾸고 다음 배송으로 넘어간다.
 * 다음 조회 시각은 지난 상태로 정한다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KitTrackingUseCase {
	private final ShipmentRepository shipmentRepository;
	private final OrderRepository orderRepository;
	private final DeliveryTrackingPort deliveryTrackingPort;
	private final TransactionTemplate transactionTemplate;
	private final SellerLookupPort sellerLookupPort;
	private final EventPublisher eventPublisher;

	@Value("${order.kit-tracking.batch-size:100}")
	private int batchSize;

	@Value("${order.kit-tracking.request-interval:200ms}")
	private Duration requestInterval;

	public record TrackResult(int tracked, int delivered, int failed) {
	}

	public TrackResult track(LocalDateTime now) {
		List<ShipmentTrackingTarget> targets = shipmentRepository.findTrackingTargets(now,
			PageRequest.of(0, batchSize));
		int tracked = 0;
		int delivered = 0;
		int failed = 0;
		for (int i = 0; i < targets.size(); i++) {
			if (i > 0 && !pause()) {
				break;
			}
			ShipmentTrackingTarget target = targets.get(i);
			DeliveryTrackingResult result;
			try {
				result = deliveryTrackingPort.track(target.carrierId(), target.trackingNumber());
			} catch (RuntimeException exception) {
				// 상대를 부르지 못함은 어댑터가 이미 남겼다. 그 밖의 예외(어댑터 버그 등)도 이 배송만 미루고 다음 배송을 조회한다
				if (!(exception instanceof ExternalServiceUnavailableException)) {
					log.error("키트 배송을 조회하다 예상하지 못한 오류가 났습니다. shipmentId={}", target.shipmentId(), exception);
				}
				failed++;
				markTracked(target, now);
				continue;
			}
			Applied applied;
			try {
				applied = transactionTemplate.execute(status -> {
					Order order = orderRepository.findByIdForUpdate(target.orderId()).orElseThrow();
					Shipment.TrackingOutcome outcome = order.applyKitTracking(target.shipmentId(), target.carrierId(),
						target.trackingNumber(), result, now);
					return new Applied(outcome, order.kitSellerId(target.shipmentId()));
				});
			} catch (RuntimeException exception) {
				// 한 배송을 남기지 못해도 나머지는 계속한다. 조회 시각을 바꿔 계속 실패하는 배송이 앞줄을 막지 않게 한다
				log.error("키트 배송 조회 결과를 남기지 못했습니다. shipmentId={}", target.shipmentId(), exception);
				failed++;
				markTracked(target, now);
				continue;
			}
			tracked++;
			if (applied.outcome() == Shipment.TrackingOutcome.DELIVERED) {
				delivered++;
			}
			if (applied.outcome() == Shipment.TrackingOutcome.STOPPED) {
				notifyStopped(target.shipmentId(), applied.sellerId());
			}
		}
		return new TrackResult(tracked, delivered, failed);
	}

	private record Applied(Shipment.TrackingOutcome outcome, long sellerId) {
	}

	// 조회 시각과 다음 조회 시각(지난 상태로 정함)만 바꾼다. 이것마저 실패해도 나머지 배송은 계속 조회한다
	private void markTracked(ShipmentTrackingTarget target, LocalDateTime now) {
		LocalDateTime nextTrackingAt = now.plus(target.trackingStatus().trackingInterval());
		try {
			transactionTemplate.executeWithoutResult(status -> shipmentRepository.markTracked(target.shipmentId(),
				target.carrierId(), target.trackingNumber(), now, nextTrackingAt));
		} catch (RuntimeException exception) {
			log.error("키트 배송 조회 시각을 남기지 못했습니다. shipmentId={}", target.shipmentId(), exception);
		}
	}

	// 조회를 멈춘 커밋 뒤에 판매자에게 알린다. 멈춤은 한 번만 일어나므로(다음 조회 시각을 비움) 알림도 한 번이다
	// 알림은 잃어도 되는 메시지라(11/04 결정) 판매자를 찾지 못해도 조회는 멈춘 채로 두고 로그만 남긴다
	private void notifyStopped(long shipmentId, long sellerId) {
		try {
			Long memberId = sellerLookupPort.findSellerMemberId(sellerId).orElse(null);
			if (memberId == null) {
				log.warn("조회를 멈춘 키트의 판매자를 찾지 못해 알리지 못했습니다. shipmentId={}, sellerId={}", shipmentId, sellerId);
				return;
			}
			eventPublisher.publish(NotificationRequestedEvent.of(memberId, NotificationKind.KIT_TRACKING_STOPPED,
				"송장 번호를 확인해 주세요",
				"키트를 보낸 지 %d일이 지나도 택배사에서 조회되지 않아 배송 조회를 멈췄어요.\n송장 번호를 고치면 다시 조회해요."
					.formatted(Shipment.NOT_TRACKED_LIMIT.toDays()),
				"/seller/kits"));
		} catch (RuntimeException exception) {
			log.error("조회를 멈춘 키트를 판매자에게 알리지 못했습니다. shipmentId={}", shipmentId, exception);
		}
	}

	// 다음 요청까지 쉰다. 앱이 꺼지며 끊기면 남은 배송은 다음 차례로 넘긴다
	private boolean pause() {
		try {
			Thread.sleep(requestInterval);
			return true;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return false;
		}
	}
}
