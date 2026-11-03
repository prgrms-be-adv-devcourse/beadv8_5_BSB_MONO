package com.bukang.order.app;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.order.domain.Order;
import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.out.OrderRepository;
import com.bukang.order.out.ShipmentRepository;
import com.bukang.order.out.ShipmentTrackingTarget;
import com.bukang.order.out.delivery.DeliveryTrackingPort;
import com.bukang.order.out.delivery.DeliveryTrackingResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 배송 중인 키트의 배송 상태를 Delivery Tracker에서 받아 온다 (BUK-55, KitTrackingScheduler가 30분마다 부름)
 * 오래 조회하지 않은 배송부터 한 번에 batch-size건, 요청 사이를 request-interval만큼 띄운다 (Delivery Tracker는 초당 10번까지)
 * 상대를 부르는 동안에는 주문을 잠그지 않는다. 결과를 남길 때 주문을 잠근다 (배송 상태를 바꾸는 곳은 모두 주문을 먼저 잠근다)
 * 택배사가 배송 완료라고 하면 키트를 배송 완료로 바꾸므로 다음 조회부터 빠진다 (정책 5장 3, 회의 확인 전 기본값)
 * 한 배송의 조회가 실패해도 조회 시각만 바꾸고 다음 배송으로 넘어간다. 다음 차례에 다시 조회한다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KitTrackingUseCase {
	private final ShipmentRepository shipmentRepository;
	private final OrderRepository orderRepository;
	private final DeliveryTrackingPort deliveryTrackingPort;
	private final TransactionTemplate transactionTemplate;

	@Value("${order.kit-tracking.batch-size:100}")
	private int batchSize;

	@Value("${order.kit-tracking.request-interval:200ms}")
	private Duration requestInterval;

	public record TrackResult(int tracked, int delivered, int failed) {
	}

	public TrackResult track(LocalDateTime now) {
		List<ShipmentTrackingTarget> targets = shipmentRepository.findTrackingTargets(PageRequest.of(0, batchSize));
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
			} catch (ExternalServiceUnavailableException exception) {
				failed++;
				markTracked(target.shipmentId(), now);
				continue;
			}
			boolean completed;
			try {
				completed = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
					Order order = orderRepository.findByIdForUpdate(target.orderId()).orElseThrow();
					return order.applyKitTracking(target.shipmentId(), result, now);
				}));
			} catch (RuntimeException exception) {
				// 한 배송을 남기지 못해도 나머지는 계속한다. 조회 시각을 바꿔 계속 실패하는 배송이 앞줄을 막지 않게 한다
				log.error("키트 배송 조회 결과를 남기지 못했습니다. shipmentId={}", target.shipmentId(), exception);
				failed++;
				markTracked(target.shipmentId(), now);
				continue;
			}
			tracked++;
			if (completed) {
				delivered++;
			}
		}
		return new TrackResult(tracked, delivered, failed);
	}

	// 조회 시각만 바꾼다. 이것마저 실패해도 나머지 배송은 계속 조회한다
	private void markTracked(long shipmentId, LocalDateTime now) {
		try {
			transactionTemplate.executeWithoutResult(status -> shipmentRepository.markTracked(shipmentId, now));
		} catch (RuntimeException exception) {
			log.error("키트 배송 조회 시각을 남기지 못했습니다. shipmentId={}", shipmentId, exception);
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
