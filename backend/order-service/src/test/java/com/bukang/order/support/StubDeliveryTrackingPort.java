package com.bukang.order.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.out.delivery.DeliveryTrackingPort;
import com.bukang.order.out.delivery.DeliveryTrackingResult;

/**
 * 테스트용 배송 조회 (BUK-55). 운송장 번호마다 테스트가 정한 결과를 돌려준다. 정하지 않은 운송장은 아직 조회되지 않음이다
 * fail로 정한 운송장은 상대를 부르지 못한 것처럼 ExternalServiceUnavailableException을 던진다
 */
public class StubDeliveryTrackingPort implements DeliveryTrackingPort {
	private final Map<String, DeliveryTrackingResult> results = new ConcurrentHashMap<>();
	private final Map<String, Boolean> failures = new ConcurrentHashMap<>();
	private final List<String> calls = new ArrayList<>();

	@Override
	public synchronized DeliveryTrackingResult track(String carrierId, String trackingNumber) {
		calls.add(trackingNumber);
		if (failures.containsKey(trackingNumber)) {
			throw new ExternalServiceUnavailableException("배송 조회 서비스에 연결하지 못했습니다.");
		}
		return results.getOrDefault(trackingNumber, DeliveryTrackingResult.notTracked());
	}

	public void put(String trackingNumber, DeliveryTrackingResult result) {
		results.put(trackingNumber, result);
	}

	public void fail(String trackingNumber) {
		failures.put(trackingNumber, true);
	}

	// 부른 운송장 번호 (부른 순서)
	public synchronized List<String> calls() {
		return List.copyOf(calls);
	}

	public synchronized void clear() {
		results.clear();
		failures.clear();
		calls.clear();
	}
}
