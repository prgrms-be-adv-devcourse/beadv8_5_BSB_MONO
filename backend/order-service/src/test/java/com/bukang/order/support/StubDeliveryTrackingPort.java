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
	private final Map<String, RuntimeException> failures = new ConcurrentHashMap<>();
	private final List<String> calls = new ArrayList<>();
	// 조회 직전에 부른다. 두 쓰레드가 대상을 다 읽은 뒤에 조회하게 맞출 때 쓴다 (BUK-234). 잠금 밖에서 불러 서로 막지 않는다
	private volatile Runnable beforeTrack = () -> {
	};

	@Override
	public DeliveryTrackingResult track(String carrierId, String trackingNumber) {
		beforeTrack.run();
		synchronized (this) {
			calls.add(trackingNumber);
			if (failures.containsKey(trackingNumber)) {
				throw failures.get(trackingNumber);
			}
			return results.getOrDefault(trackingNumber, DeliveryTrackingResult.notTracked());
		}
	}

	public void beforeTrack(Runnable hook) {
		this.beforeTrack = hook;
	}

	public void put(String trackingNumber, DeliveryTrackingResult result) {
		results.put(trackingNumber, result);
	}

	public void fail(String trackingNumber) {
		failWith(trackingNumber, new ExternalServiceUnavailableException("배송 조회 서비스에 연결하지 못했습니다."));
	}

	// 상대를 부르지 못함이 아닌 예외 (어댑터 버그 등)
	public void failWith(String trackingNumber, RuntimeException exception) {
		failures.put(trackingNumber, exception);
	}

	// 부른 운송장 번호 (부른 순서)
	public synchronized List<String> calls() {
		return List.copyOf(calls);
	}

	public synchronized void clear() {
		beforeTrack = () -> {
		};
		results.clear();
		failures.clear();
		calls.clear();
	}
}
